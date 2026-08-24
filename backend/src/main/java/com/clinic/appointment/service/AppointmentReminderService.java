package com.clinic.appointment.service;

import com.clinic.appointment.entity.Appointment;
import com.clinic.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fulfils the "reminder" leg of the required email set (booking confirmation,
 * reminder, cancellation) - separate from medication reminders, which are
 * about prescription adherence rather than the visit itself.
 *
 * Runs every 15 minutes and looks for CONFIRMED appointments whose slot
 * falls in the next 24h-24h15m window and that haven't been reminded yet
 * (reminder_sent_at is null). The 15-minute window matches the job's own
 * cadence so every appointment is caught exactly once as the 24h mark
 * passes, without needing a wider "already sent" scan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentReminderService {

    private final AppointmentRepository appointmentRepository;
    private final EmailService emailService;

    private static final long REMINDER_LEAD_HOURS = 24;

    @Scheduled(fixedDelayString = "900000") // 15 minutes
    @Transactional
    public void sendDueAppointmentReminders() {
        LocalDateTime windowStart = LocalDateTime.now().plusHours(REMINDER_LEAD_HOURS);
        LocalDateTime windowEnd = windowStart.plusMinutes(15);

        List<Appointment> due = appointmentRepository.findDueForReminder(windowStart, windowEnd);
        for (Appointment appt : due) {
            try {
                emailService.queueAppointmentReminderEmail(appt);
                appt.setReminderSentAt(LocalDateTime.now());
                appointmentRepository.save(appt);
            } catch (Exception e) {
                // Never let one bad appointment stop the rest of the batch;
                // it will simply be picked up again on the next run since
                // reminder_sent_at was not set.
                log.error("Failed to queue reminder for appointment {}: {}", appt.getId(), e.getMessage());
            }
        }
    }
}
