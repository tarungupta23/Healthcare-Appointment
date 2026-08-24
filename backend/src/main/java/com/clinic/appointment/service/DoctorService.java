package com.clinic.appointment.service;

import com.clinic.appointment.dto.*;
import com.clinic.appointment.entity.*;
import com.clinic.appointment.exception.BadRequestException;
import com.clinic.appointment.exception.ResourceNotFoundException;
import com.clinic.appointment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final DoctorWorkingHoursRepository workingHoursRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // ---------- Admin: create & manage doctor profiles ----------

    @Transactional
    public DoctorResponse createDoctor(DoctorCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getTemporaryPassword()))
                .role(Role.DOCTOR)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isActive(true)
                .build();
        user = userRepository.save(user);

        Doctor doctor = Doctor.builder()
                .user(user)
                .specialisation(request.getSpecialisation())
                .qualification(request.getQualification())
                .yearsExperience(request.getYearsExperience())
                .slotDurationMinutes(request.getSlotDurationMinutes())
                .consultationFee(request.getConsultationFee())
                .bio(request.getBio())
                .build();
        doctor = doctorRepository.save(doctor);

        if (request.getWorkingHours() != null) {
            for (WorkingHoursDto wh : request.getWorkingHours()) {
                validateWorkingHours(wh);
                workingHoursRepository.save(DoctorWorkingHours.builder()
                        .doctor(doctor)
                        .dayOfWeek(wh.getDayOfWeek())
                        .startTime(wh.getStartTime())
                        .endTime(wh.getEndTime())
                        .build());
            }
        }

        emailService.queueDoctorWelcomeEmail(user, request.getTemporaryPassword());

        return toDoctorResponse(doctor);
    }

    @Transactional
    public DoctorResponse updateWorkingHours(Long doctorId, List<WorkingHoursDto> hours) {
        Doctor doctor = getDoctorOrThrow(doctorId);
        workingHoursRepository.deleteAll(workingHoursRepository.findByDoctorId(doctorId));
        for (WorkingHoursDto wh : hours) {
            validateWorkingHours(wh);
            workingHoursRepository.save(DoctorWorkingHours.builder()
                    .doctor(doctor)
                    .dayOfWeek(wh.getDayOfWeek())
                    .startTime(wh.getStartTime())
                    .endTime(wh.getEndTime())
                    .build());
        }
        return toDoctorResponse(doctor);
    }

    private void validateWorkingHours(WorkingHoursDto wh) {
        if (wh.getStartTime() == null || wh.getEndTime() == null || !wh.getStartTime().isBefore(wh.getEndTime())) {
            throw new BadRequestException("Working hours start time must be before end time for " + wh.getDayOfWeek());
        }
    }

    // ---------- Doctor leave management with conflict handling ----------

    /**
     * Marks a doctor unavailable for a given date. If confirmed appointments
     * already exist on that date, they are cancelled and BOTH the patient
     * (cancellation notice) and doctor (confirmation of the leave-triggered
     * cancellations) are notified by email. This runs inside a single
     * transaction so leave creation and appointment cancellation are atomic;
     * emails themselves are queued to the outbox (see EmailService) so a
     * transient SMTP outage never rolls back the leave/cancellation itself.
     */
    @Transactional
    public List<AppointmentResponse> markDoctorOnLeave(Long doctorId, DoctorLeaveRequest request) {
        Doctor doctor = getDoctorOrThrow(doctorId);

        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, request.getLeaveDate())) {
            throw new BadRequestException("Doctor is already marked on leave for this date");
        }

        doctorLeaveRepository.save(DoctorLeave.builder()
                .doctor(doctor)
                .leaveDate(request.getLeaveDate())
                .reason(request.getReason())
                .build());

        LocalDateTime dayStart = request.getLeaveDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Appointment> affected = appointmentRepository.findActiveByDoctorAndDay(doctorId, dayStart, dayEnd);

        List<AppointmentResponse> cancelledResponses = new ArrayList<>();
        for (Appointment appt : affected) {
            appt.setStatus(AppointmentStatus.CANCELLED_BY_LEAVE);
            appt.setCancelledAt(LocalDateTime.now());
            appt.setCancellationReason("Doctor unavailable on this date"
                    + (request.getReason() != null ? ": " + request.getReason() : ""));
            appointmentRepository.save(appt);

            emailService.queueLeaveCancellationEmails(appt);
            cancelledResponses.add(AppointmentMapper.toResponse(appt));
        }

        return cancelledResponses;
    }

    // ---------- Search / discovery ----------

    public List<DoctorResponse> searchDoctors(String specialisation) {
        List<Doctor> doctors = (specialisation == null || specialisation.isBlank())
                ? doctorRepository.findAll()
                : doctorRepository.findBySpecialisationContainingIgnoreCase(specialisation);
        return doctors.stream().map(this::toDoctorResponse).collect(Collectors.toList());
    }

    public DoctorResponse getDoctor(Long doctorId) {
        return toDoctorResponse(getDoctorOrThrow(doctorId));
    }

    /**
     * Computes free slots for a doctor on a given date by:
     *  1. Reading the doctor's recurring working-hours window for that weekday
     *  2. Slicing it into fixed-size slots (doctor.slotDurationMinutes)
     *  3. Removing slots that fall on a leave day
     *  4. Removing slots already CONFIRMED/PENDING or currently ACTIVE-held
     */
    public List<SlotResponse> getAvailability(Long doctorId, LocalDate date) {
        Doctor doctor = getDoctorOrThrow(doctorId);

        boolean onLeave = doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, date);
        if (onLeave) {
            return List.of();
        }

        Optional<DoctorWorkingHours> whOpt = workingHoursRepository
                .findByDoctorIdAndDayOfWeek(doctorId, date.getDayOfWeek());
        if (whOpt.isEmpty()) {
            return List.of();
        }
        DoctorWorkingHours wh = whOpt.get();

        int durationMin = doctor.getSlotDurationMinutes();
        List<SlotResponse> slots = new ArrayList<>();

        LocalDateTime cursor = date.atTime(wh.getStartTime());
        LocalDateTime windowEnd = date.atTime(wh.getEndTime());

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> bookedToday = appointmentRepository.findActiveByDoctorAndDay(doctorId, dayStart, dayEnd);
        List<SlotHold> activeHoldsToday = slotHoldRepository.findActiveHoldsForDoctorOnDay(doctorId, dayStart, dayEnd, now);

        while (!cursor.plusMinutes(durationMin).isAfter(windowEnd)) {
            LocalDateTime slotEnd = cursor.plusMinutes(durationMin);
            final LocalDateTime slotStartFinal = cursor;

            boolean isBooked = bookedToday.stream().anyMatch(a -> a.getSlotStart().equals(slotStartFinal));
            boolean isHeld = activeHoldsToday.stream().anyMatch(h -> h.getSlotStart().equals(slotStartFinal));
            boolean isPast = cursor.isBefore(now);

            slots.add(SlotResponse.builder()
                    .slotStart(cursor)
                    .slotEnd(slotEnd)
                    .available(!isBooked && !isHeld && !isPast)
                    .build());

            cursor = slotEnd;
        }

        return slots;
    }

    /**
     * Server-side re-validation that a slot is actually bookable, independent
     * of whatever the frontend showed the user. Called by SlotService before
     * creating a hold, so a stale client-side slot list can never bypass the
     * doctor's real working hours or a leave day added in the meantime.
     */
    public Doctor validateSlotBookable(Long doctorId, LocalDateTime slotStart) {
        Doctor doctor = getDoctorOrThrow(doctorId);

        if (slotStart.isBefore(LocalDateTime.now())) {
            throw new com.clinic.appointment.exception.SlotUnavailableException("Cannot book a slot in the past");
        }

        LocalDate date = slotStart.toLocalDate();
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, date)) {
            throw new com.clinic.appointment.exception.SlotUnavailableException(
                    "Doctor is on leave on " + date);
        }

        DoctorWorkingHours wh = workingHoursRepository
                .findByDoctorIdAndDayOfWeek(doctorId, date.getDayOfWeek())
                .orElseThrow(() -> new com.clinic.appointment.exception.SlotUnavailableException(
                        "Doctor does not work on " + date.getDayOfWeek()));

        LocalTime time = slotStart.toLocalTime();
        LocalTime slotEndTime = time.plusMinutes(doctor.getSlotDurationMinutes());
        if (time.isBefore(wh.getStartTime()) || slotEndTime.isAfter(wh.getEndTime())) {
            throw new com.clinic.appointment.exception.SlotUnavailableException(
                    "Requested time is outside doctor's working hours");
        }

        return doctor;
    }

    // ---------- helpers ----------

    Doctor getDoctorOrThrow(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
    }

    private DoctorResponse toDoctorResponse(Doctor doctor) {
        List<WorkingHoursDto> hours = workingHoursRepository.findByDoctorId(doctor.getId()).stream()
                .map(wh -> new WorkingHoursDto(wh.getDayOfWeek(), wh.getStartTime(), wh.getEndTime()))
                .collect(Collectors.toList());

        return DoctorResponse.builder()
                .doctorId(doctor.getId())
                .fullName(doctor.getUser().getFullName())
                .specialisation(doctor.getSpecialisation())
                .qualification(doctor.getQualification())
                .yearsExperience(doctor.getYearsExperience())
                .slotDurationMinutes(doctor.getSlotDurationMinutes())
                .consultationFee(doctor.getConsultationFee())
                .bio(doctor.getBio())
                .workingHours(hours)
                .build();
    }
}
