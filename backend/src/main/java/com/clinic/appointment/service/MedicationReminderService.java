package com.clinic.appointment.service;

import com.clinic.appointment.dto.PrescriptionItemDto;
import com.clinic.appointment.entity.Appointment;
import com.clinic.appointment.entity.MedicationReminder;
import com.clinic.appointment.entity.ReminderStatus;
import com.clinic.appointment.repository.MedicationReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Expands a structured prescription (medication + frequency + duration) into
 * concrete reminder timestamps right after the post-visit summary is saved,
 * then a scheduled job "sends" (queues an email for) each reminder as it
 * comes due. Frequencies map to fixed daily times chosen to be reasonable
 * defaults for a general clinic (before/after typical meal times).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationReminderService {

    private final MedicationReminderRepository reminderRepository;
    private final EmailService emailService;

    @Transactional
    public void scheduleRemindersForPrescription(Appointment appt, List<PrescriptionItemDto> items) {
        if (items == null || items.isEmpty()) return;

        LocalDateTime visitDate = appt.getPostVisitCompletedAt() != null
                ? appt.getPostVisitCompletedAt()
                : LocalDateTime.now();

        for (PrescriptionItemDto item : items) {
            List<LocalDateTime> times = expandFrequency(item.getFrequency(), visitDate.toLocalDate(),
                    item.getDurationDays() != null ? item.getDurationDays() : 1);

            for (LocalDateTime t : times) {
                if (t.isBefore(LocalDateTime.now())) continue; // skip times already in the past for "today"
                reminderRepository.save(MedicationReminder.builder()
                        .appointment(appt)
                        .patient(appt.getPatient())
                        .medicationName(item.getMedicationName())
                        .dosage(item.getDosage())
                        .scheduledAt(t)
                        .status(ReminderStatus.PENDING)
                        .attempts(0)
                        .build());
            }
        }
    }

    private List<LocalDateTime> expandFrequency(String frequency, java.time.LocalDate startDate, int durationDays) {
        List<LocalDateTime> result = new java.util.ArrayList<>();
        List<java.time.LocalTime> dailyTimes = switch (frequency == null ? "ONCE_DAILY" : frequency.toUpperCase()) {
            case "TWICE_DAILY" -> List.of(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(21, 0));
            case "THRICE_DAILY" -> List.of(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(14, 0), java.time.LocalTime.of(20, 0));
            case "EVERY_8_HOURS" -> List.of(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(14, 0), java.time.LocalTime.of(22, 0));
            case "EVERY_6_HOURS" -> List.of(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(12, 0),
                    java.time.LocalTime.of(18, 0), java.time.LocalTime.of(0, 0));
            default -> List.of(java.time.LocalTime.of(9, 0)); // ONCE_DAILY / unrecognised -> safe default
        };

        for (int day = 0; day < durationDays; day++) {
            java.time.LocalDate date = startDate.plusDays(day);
            for (java.time.LocalTime time : dailyTimes) {
                result.add(LocalDateTime.of(date, time));
            }
        }
        return result;
    }

    @Scheduled(fixedDelayString = "60000")
    public void sendDueReminders() {
        List<MedicationReminder> due = reminderRepository.findDueReminders(LocalDateTime.now());
        for (MedicationReminder reminder : due) {
            try {
                emailService.queueMedicationReminderEmail(reminder);
                reminder.setStatus(ReminderStatus.SENT);
                reminder.setLastAttemptAt(LocalDateTime.now());
                reminder.setAttempts(reminder.getAttempts() + 1);
            } catch (Exception e) {
                log.error("Failed to queue medication reminder {}: {}", reminder.getId(), e.getMessage());
                reminder.setStatus(ReminderStatus.FAILED);
                reminder.setAttempts(reminder.getAttempts() + 1);
                reminder.setLastAttemptAt(LocalDateTime.now());
            }
            reminderRepository.save(reminder);
        }
    }
}
