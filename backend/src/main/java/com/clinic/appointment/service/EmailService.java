package com.clinic.appointment.service;

import com.clinic.appointment.entity.*;
import com.clinic.appointment.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * All outbound email goes through a transactional outbox table
 * (email_outbox) instead of being sent inline during the request:
 *   1. queue*() methods INSERT a row and return immediately - a booking or
 *      cancellation therefore always succeeds even if the mail server is
 *      completely down.
 *   2. processOutbox() runs on a schedule, sends whatever is due, and on
 *      failure reschedules with exponential backoff (1m, 2m, 4m, 8m, 16m)
 *      up to max_attempts, after which the row is marked FAILED and left
 *      for an admin/ops dashboard query rather than retried forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailOutboxRepository emailOutboxRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy 'at' hh:mm a");

    // ---------------- Queuing (called from other services, inside their own transaction) ----------------

    @Transactional
    public void queueBookingConfirmationEmails(Appointment appt) {
        String when = appt.getSlotStart().format(FMT);

        queue(appt.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.BOOKING_CONFIRMATION,
                "Appointment confirmed for " + when,
                "Hi " + appt.getPatient().getUser().getFullName() + ",\n\n" +
                "Your appointment with Dr. " + appt.getDoctor().getUser().getFullName() +
                " (" + appt.getDoctor().getSpecialisation() + ") is confirmed for " + when + ".\n\n" +
                "Please arrive 10 minutes early. You can view or manage this booking in the patient portal.\n\n" +
                "- Clinic Team", appt.getId());

        queue(appt.getDoctor().getUser().getEmail(), RecipientRole.DOCTOR, EmailTemplateType.BOOKING_CONFIRMATION,
                "New appointment booked for " + when,
                "Hi Dr. " + appt.getDoctor().getUser().getFullName() + ",\n\n" +
                "A new appointment has been booked by " + appt.getPatient().getUser().getFullName() +
                " for " + when + ". The patient's pre-visit symptom summary will appear in your dashboard " +
                "shortly before the visit.\n\n" +
                "- Clinic Team", appt.getId());
    }

    @Transactional
    public void queueCancellationEmails(Appointment appt) {
        String when = appt.getSlotStart().format(FMT);
        String reason = appt.getCancellationReason() != null ? appt.getCancellationReason() : "No reason provided";

        queue(appt.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.CANCELLATION,
                "Appointment cancelled - " + when,
                "Hi " + appt.getPatient().getUser().getFullName() + ",\n\n" +
                "Your appointment with Dr. " + appt.getDoctor().getUser().getFullName() +
                " scheduled for " + when + " has been cancelled.\nReason: " + reason + "\n\n" +
                "Please book a new slot at your convenience.\n\n- Clinic Team", appt.getId());

        queue(appt.getDoctor().getUser().getEmail(), RecipientRole.DOCTOR, EmailTemplateType.CANCELLATION,
                "Appointment cancelled - " + when,
                "Hi Dr. " + appt.getDoctor().getUser().getFullName() + ",\n\n" +
                "The appointment with " + appt.getPatient().getUser().getFullName() +
                " scheduled for " + when + " has been cancelled.\nReason: " + reason + "\n\n- Clinic Team",
                appt.getId());
    }

    /** Sent when an admin marks a doctor on leave and existing bookings must be cancelled. */
    @Transactional
    public void queueLeaveCancellationEmails(Appointment appt) {
        String when = appt.getSlotStart().format(FMT);

        queue(appt.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.LEAVE_CANCELLATION,
                "Your appointment on " + when + " has been cancelled",
                "Hi " + appt.getPatient().getUser().getFullName() + ",\n\n" +
                "We're sorry - Dr. " + appt.getDoctor().getUser().getFullName() +
                " is unexpectedly unavailable on this date, so your appointment scheduled for " + when +
                " has been cancelled.\n\n" +
                "Please log in to the patient portal to rebook at a convenient time. We apologise for the " +
                "inconvenience.\n\n- Clinic Team", appt.getId());

        queue(appt.getDoctor().getUser().getEmail(), RecipientRole.DOCTOR, EmailTemplateType.LEAVE_CANCELLATION,
                "Leave confirmed - appointment auto-cancelled for " + when,
                "Hi Dr. " + appt.getDoctor().getUser().getFullName() + ",\n\n" +
                "As part of marking " + appt.getSlotStart().toLocalDate() + " as leave, the appointment with " +
                appt.getPatient().getUser().getFullName() + " originally scheduled for " + when +
                " was automatically cancelled and the patient notified.\n\n- Clinic Team", appt.getId());
    }

    /** "Your appointment is coming up" reminder - distinct from medication reminders,
     *  queued by AppointmentReminderService roughly 24h before the slot. */
    @Transactional
    public void queueAppointmentReminderEmail(Appointment appt) {
        String when = appt.getSlotStart().format(FMT);

        queue(appt.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.REMINDER,
                "Reminder: appointment tomorrow at " + appt.getSlotStart().toLocalTime(),
                "Hi " + appt.getPatient().getUser().getFullName() + ",\n\n" +
                "This is a reminder that you have an appointment with Dr. " + appt.getDoctor().getUser().getFullName() +
                " (" + appt.getDoctor().getSpecialisation() + ") on " + when + ".\n\n" +
                "Please arrive 10 minutes early. If you need to reschedule or cancel, you can do so from the " +
                "patient portal.\n\n- Clinic Team", appt.getId());

        queue(appt.getDoctor().getUser().getEmail(), RecipientRole.DOCTOR, EmailTemplateType.REMINDER,
                "Reminder: appointment tomorrow with " + appt.getPatient().getUser().getFullName(),
                "Hi Dr. " + appt.getDoctor().getUser().getFullName() + ",\n\n" +
                "Reminder: you have an appointment with " + appt.getPatient().getUser().getFullName() +
                " on " + when + ".\n\n- Clinic Team", appt.getId());
    }

    /** Sent to both parties when an appointment's slot is moved rather than cancelled. */
    @Transactional
    public void queueRescheduleEmails(Appointment appt, java.time.LocalDateTime oldSlotStart) {
        String oldWhen = oldSlotStart.format(FMT);
        String newWhen = appt.getSlotStart().format(FMT);

        queue(appt.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.RESCHEDULED,
                "Appointment rescheduled to " + newWhen,
                "Hi " + appt.getPatient().getUser().getFullName() + ",\n\n" +
                "Your appointment with Dr. " + appt.getDoctor().getUser().getFullName() +
                " originally scheduled for " + oldWhen + " has been moved to " + newWhen + ".\n\n" +
                "Your calendar invite has been updated automatically if you've connected Google Calendar.\n\n" +
                "- Clinic Team", appt.getId());

        queue(appt.getDoctor().getUser().getEmail(), RecipientRole.DOCTOR, EmailTemplateType.RESCHEDULED,
                "Appointment rescheduled to " + newWhen,
                "Hi Dr. " + appt.getDoctor().getUser().getFullName() + ",\n\n" +
                "The appointment with " + appt.getPatient().getUser().getFullName() +
                " originally scheduled for " + oldWhen + " has been moved to " + newWhen + ".\n\n- Clinic Team",
                appt.getId());
    }

    @Transactional
    public void queueDoctorWelcomeEmail(User doctorUser, String temporaryPassword) {
        queue(doctorUser.getEmail(), RecipientRole.DOCTOR, EmailTemplateType.BOOKING_CONFIRMATION,
                "Welcome to the Clinic Appointment Platform",
                "Hi Dr. " + doctorUser.getFullName() + ",\n\n" +
                "An account has been created for you.\nLogin email: " + doctorUser.getEmail() +
                "\nTemporary password: " + temporaryPassword + "\n\n" +
                "Please log in and change your password as soon as possible.\n\n- Clinic Admin", null);
    }

    @Transactional
    public void queueMedicationReminderEmail(MedicationReminder reminder) {
        queue(reminder.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.MEDICATION_REMINDER,
                "Medication reminder: " + reminder.getMedicationName(),
                "Hi " + reminder.getPatient().getUser().getFullName() + ",\n\n" +
                "This is a reminder to take your medication: " + reminder.getMedicationName() +
                (reminder.getDosage() != null ? " (" + reminder.getDosage() + ")" : "") + ".\n\n" +
                "This reminder is based on the prescription from your recent visit.\n\n- Clinic Team",
                reminder.getAppointment().getId());
    }

    @Transactional
    public void queuePostVisitSummaryEmail(Appointment appt) {
        queue(appt.getPatient().getUser().getEmail(), RecipientRole.PATIENT, EmailTemplateType.POST_VISIT_SUMMARY,
                "Your visit summary from Dr. " + appt.getDoctor().getUser().getFullName(),
                "Hi " + appt.getPatient().getUser().getFullName() + ",\n\n" +
                (appt.getPostVisitSummaryText() != null
                        ? appt.getPostVisitSummaryText()
                        : "Your doctor's notes are available in the patient portal.") +
                "\n\n- Clinic Team", appt.getId());
    }

    private void queue(String recipientEmail, RecipientRole role, EmailTemplateType template,
                        String subject, String body, Long appointmentId) {
        EmailOutbox email = EmailOutbox.builder()
                .recipientEmail(recipientEmail)
                .recipientRole(role)
                .template(template)
                .subject(subject)
                .body(body)
                .relatedAppointmentId(appointmentId)
                .status(EmailStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        emailOutboxRepository.save(email);
    }

    // ---------------- Background delivery worker ----------------

    @Scheduled(fixedDelayString = "30000")
    public void processOutbox() {
        List<EmailOutbox> due = emailOutboxRepository.findDueForRetry(LocalDateTime.now());
        for (EmailOutbox email : due) {
            sendOne(email);
        }
    }

    @Transactional
    public void sendOne(EmailOutbox email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email.getRecipientEmail());
            message.setSubject(email.getSubject());
            message.setText(email.getBody());
            mailSender.send(message);

            email.setStatus(EmailStatus.SENT);
            email.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            int attempts = email.getAttempts() + 1;
            email.setAttempts(attempts);
            email.setLastError(truncate(ex.getMessage(), 500));

            if (attempts >= email.getMaxAttempts()) {
                email.setStatus(EmailStatus.FAILED);
                log.error("Email to {} permanently failed after {} attempts: {}",
                        email.getRecipientEmail(), attempts, ex.getMessage());
            } else {
                long backoffMinutes = (long) Math.pow(2, attempts - 1); // 1,2,4,8,16 minutes
                email.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                log.warn("Email to {} failed (attempt {}/{}), retrying in {} min: {}",
                        email.getRecipientEmail(), attempts, email.getMaxAttempts(), backoffMinutes, ex.getMessage());
            }
        }
        emailOutboxRepository.save(email);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
