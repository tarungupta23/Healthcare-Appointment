package com.clinic.appointment.service;

import com.clinic.appointment.dto.*;
import com.clinic.appointment.entity.*;
import com.clinic.appointment.exception.BadRequestException;
import com.clinic.appointment.exception.ResourceNotFoundException;
import com.clinic.appointment.exception.UnauthorizedActionException;
import com.clinic.appointment.repository.AppointmentRepository;
import com.clinic.appointment.repository.DoctorRepository;
import com.clinic.appointment.repository.PatientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorService doctorService;
    private final SlotService slotService;
    private final LlmService llmService;
    private final EmailService emailService;
    private final GoogleCalendarService googleCalendarService;
    private final MedicationReminderService medicationReminderService;
    private final PreVisitSummaryGenerator preVisitSummaryGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Confirms a booking from a previously created slot hold and symptom
     * text. Runs the DB write in its own transaction first (booking must
     * never fail because of the LLM or email/calendar side effects), then
     * performs the LLM call and notification/calendar sync as best-effort
     * follow-ups outside that transaction so a slow LLM call cannot hold a
     * database lock.
     */
    public AppointmentResponse confirmBooking(Long patientUserId, ConfirmBookingRequest request) {
        Patient patient = patientRepository.findByUserId(patientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Appointment appointment = createAppointmentFromHold(patient.getId(), request);

        // Best-effort side effects - failures here are logged but never
        // surface as a failed booking to the patient. Delegated to a
        // separate bean (not `this`) so Spring's @Async proxy actually
        // intercepts the call and runs it on a background thread.
        preVisitSummaryGenerator.generateAsync(appointment.getId());
        emailService.queueBookingConfirmationEmails(appointment);
        try {
            googleCalendarService.syncOnBooking(appointment);
            appointmentRepository.save(appointment);
        } catch (Exception e) {
            log.warn("Calendar sync failed for appointment {}: {}", appointment.getId(), e.getMessage());
        }

        return AppointmentMapper.toResponse(appointment);
    }

    // NOTE: intentionally NOT @Transactional here - this method is invoked via
    // `this.` (self-invocation) from confirmBooking(), which Spring's proxy-based
    // @Transactional cannot intercept. The two writes it performs already have
    // their own transaction boundaries: consumeHoldForBooking() runs in
    // SlotService's own transaction (a genuine external bean call), and
    // appointmentRepository.save() gets Spring Data's default per-call
    // transaction. Together they still give us: hold is atomically consumed,
    // then the appointment insert is atomically guarded by the DB unique
    // constraint - no correctness is lost by the absence of a wrapping
    // transaction here.
    private Appointment createAppointmentFromHold(Long patientId, ConfirmBookingRequest request) {
        SlotHold hold = slotService.consumeHoldForBooking(request.getHoldId(), patientId);

        Appointment appointment = Appointment.builder()
                .doctor(hold.getDoctor())
                .patient(hold.getPatient())
                .slotStart(hold.getSlotStart())
                .slotEnd(hold.getSlotEnd())
                .status(AppointmentStatus.CONFIRMED)
                .symptomsText(request.getSymptomsText())
                .symptomSubmittedAt(LocalDateTime.now())
                .preVisitLlmStatus(LlmStatus.PENDING)
                .postVisitLlmStatus(LlmStatus.PENDING)
                .build();

        try {
            return appointmentRepository.save(appointment);
        } catch (DataIntegrityViolationException ex) {
            // Final safety net: the unique (doctor_id, slot_start) constraint
            // caught a race that slipped past the hold mechanism entirely
            // (e.g. a hold was force-expired concurrently by the sweep job).
            throw new com.clinic.appointment.exception.SlotUnavailableException(
                    "This slot was just booked by someone else. Please choose another slot.");
        }
    }

    // ---------------- Cancellation ----------------

    @Transactional
    public AppointmentResponse cancelAppointment(Long userId, String role, Long appointmentId, CancelAppointmentRequest request) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        assertCanModify(userId, role, appt);

        if (appt.getStatus() == AppointmentStatus.CANCELLED || appt.getStatus() == AppointmentStatus.CANCELLED_BY_LEAVE) {
            throw new BadRequestException("Appointment is already cancelled");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed appointment");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setCancelledAt(LocalDateTime.now());
        appt.setCancellationReason(request != null ? request.getReason() : null);
        appointmentRepository.save(appt);

        emailService.queueCancellationEmails(appt);
        googleCalendarService.syncOnCancellation(appt);

        return AppointmentMapper.toResponse(appt);
    }

    private void assertCanModify(Long userId, String role, Appointment appt) {
        boolean isPatientOwner = "PATIENT".equals(role) && appt.getPatient().getUser().getId().equals(userId);
        boolean isDoctorOwner = "DOCTOR".equals(role) && appt.getDoctor().getUser().getId().equals(userId);
        boolean isAdmin = "ADMIN".equals(role);
        if (!isPatientOwner && !isDoctorOwner && !isAdmin) {
            throw new UnauthorizedActionException("You are not authorised to modify this appointment");
        }
    }

    // ---------------- Reschedule ----------------

    /**
     * Moves an existing appointment to a new slot rather than cancel+rebook.
     * Reuses the exact same validation as a fresh booking (working hours,
     * leave days, and the DB unique constraint as the final guard against a
     * concurrent booking landing on the same new slot), then updates - not
     * recreates - the Google Calendar events so attendees see a moved event
     * rather than a cancelled one plus a new invite.
     */
    @Transactional
    public AppointmentResponse rescheduleAppointment(Long userId, String role, Long appointmentId, RescheduleRequest request) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        assertCanModify(userId, role, appt);

        if (appt.getStatus() != AppointmentStatus.CONFIRMED && appt.getStatus() != AppointmentStatus.PENDING_SYMPTOMS) {
            throw new BadRequestException("Only confirmed appointments can be rescheduled");
        }

        Long doctorId = appt.getDoctor().getId();
        LocalDateTime newSlotStart = request.getNewSlotStart();

        // Same server-side re-validation used for a brand-new booking - working
        // hours, leave days, and "not in the past" - independent of the client.
        var doctor = doctorService.validateSlotBookable(doctorId, newSlotStart);
        LocalDateTime newSlotEnd = newSlotStart.plusMinutes(doctor.getSlotDurationMinutes());

        appointmentRepository.findActiveForUpdate(doctorId, newSlotStart).ifPresent(existing -> {
            if (!existing.getId().equals(appointmentId)) {
                throw new com.clinic.appointment.exception.SlotUnavailableException(
                        "That slot is already booked. Please choose another.");
            }
        });

        LocalDateTime oldSlotStart = appt.getSlotStart();

        appt.setSlotStart(newSlotStart);
        appt.setSlotEnd(newSlotEnd);
        appt.setReminderSentAt(null); // allow a fresh 24h-before reminder for the new time

        try {
            appt = appointmentRepository.save(appt);
        } catch (DataIntegrityViolationException ex) {
            // Final safety net: unique (doctor_id, slot_start) caught a race
            // that slipped past the check above.
            throw new com.clinic.appointment.exception.SlotUnavailableException(
                    "That slot was just booked by someone else. Please choose another.");
        }

        emailService.queueRescheduleEmails(appt, oldSlotStart);

        // Best-effort: move the existing calendar events rather than delete+recreate.
        boolean patientUpdated = appt.getPatientCalendarEventId() != null &&
                googleCalendarService.updateEvent(appt.getPatient().getUser().getId(), "primary",
                        appt.getPatientCalendarEventId(), newSlotStart, newSlotEnd);
        boolean doctorUpdated = appt.getDoctorCalendarEventId() != null &&
                googleCalendarService.updateEvent(appt.getDoctor().getUser().getId(), "primary",
                        appt.getDoctorCalendarEventId(), newSlotStart, newSlotEnd);
        if (!patientUpdated && appt.getPatientCalendarEventId() != null) {
            log.warn("Could not update patient's calendar event for rescheduled appointment {}", appointmentId);
        }
        if (!doctorUpdated && appt.getDoctorCalendarEventId() != null) {
            log.warn("Could not update doctor's calendar event for rescheduled appointment {}", appointmentId);
        }

        return AppointmentMapper.toResponse(appt);
    }

    // ---------------- Doctor: post-visit notes + LLM summary ----------------

    @Transactional
    public AppointmentResponse submitPostVisitNotes(Long doctorUserId, Long appointmentId, PostVisitRequest request) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (!appt.getDoctor().getUser().getId().equals(doctorUserId)) {
            throw new UnauthorizedActionException("You are not authorised to update this appointment");
        }

        appt.setDoctorNotes(request.getDoctorNotes());
        try {
            appt.setPrescriptionJson(objectMapper.writeValueAsString(request.getPrescriptionItems()));
        } catch (Exception e) {
            appt.setPrescriptionJson("[]");
        }
        appt.setStatus(AppointmentStatus.COMPLETED);
        appt.setPostVisitCompletedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // Generate the patient-friendly summary (best-effort; graceful fallback on failure)
        String prescriptionSummary = summarisePrescriptionForPrompt(request);
        LlmService.LlmResult result = llmService.generatePostVisitSummary(
                request.getDoctorNotes(), prescriptionSummary, request.getFollowUpInstructions());

        if (result.success) {
            appt.setPostVisitSummaryText(result.rawText);
            appt.setPostVisitLlmStatus(LlmStatus.SUCCESS);
        } else {
            // Graceful fallback: a plain, non-LLM summary built directly from
            // the doctor's structured input so the patient still gets useful
            // information even if the LLM is unavailable.
            appt.setPostVisitSummaryText(buildFallbackPostVisitSummary(request));
            appt.setPostVisitLlmStatus(LlmStatus.FAILED);
        }
        appt.setPostVisitGeneratedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        medicationReminderService.scheduleRemindersForPrescription(appt, request.getPrescriptionItems());
        emailService.queuePostVisitSummaryEmail(appt);

        return AppointmentMapper.toResponse(appt);
    }

    private String summarisePrescriptionForPrompt(PostVisitRequest request) {
        if (request.getPrescriptionItems() == null) return "None";
        return request.getPrescriptionItems().stream()
                .map(i -> i.getMedicationName() + " - " + i.getDosage() + ", " + i.getFrequency()
                        + " for " + i.getDurationDays() + " days"
                        + (i.getInstructions() != null ? " (" + i.getInstructions() + ")" : ""))
                .collect(Collectors.joining("; "));
    }

    private String buildFallbackPostVisitSummary(PostVisitRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summary of your visit:\n\n").append(request.getDoctorNotes()).append("\n\n");
        if (request.getPrescriptionItems() != null && !request.getPrescriptionItems().isEmpty()) {
            sb.append("Medication schedule:\n");
            request.getPrescriptionItems().forEach(i ->
                    sb.append("- ").append(i.getMedicationName())
                      .append(i.getDosage() != null ? " (" + i.getDosage() + ")" : "")
                      .append(": ").append(i.getFrequency())
                      .append(" for ").append(i.getDurationDays()).append(" days\n"));
        }
        if (request.getFollowUpInstructions() != null && !request.getFollowUpInstructions().isBlank()) {
            sb.append("\nFollow-up: ").append(request.getFollowUpInstructions());
        }
        return sb.toString();
    }

    // ---------------- Listing ----------------

    public List<AppointmentResponse> getPatientAppointments(Long patientUserId) {
        Patient patient = patientRepository.findByUserId(patientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        return appointmentRepository.findByPatientIdOrderBySlotStartDesc(patient.getId()).stream()
                .map(AppointmentMapper::toResponse).collect(Collectors.toList());
    }

    public List<AppointmentResponse> getDoctorAppointments(Long doctorUserId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));
        return appointmentRepository.findByDoctorIdOrderBySlotStartDesc(doctor.getId()).stream()
                .map(AppointmentMapper::toResponse).collect(Collectors.toList());
    }

    public AppointmentResponse getAppointment(Long appointmentId) {
        return AppointmentMapper.toResponse(appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found")));
    }
}
