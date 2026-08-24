package com.clinic.appointment.repository;

import com.clinic.appointment.entity.MedicationReminder;
import com.clinic.appointment.entity.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MedicationReminderRepository extends JpaRepository<MedicationReminder, Long> {
    @Query("select r from MedicationReminder r where r.status = 'PENDING' and r.scheduledAt <= :now")
    List<MedicationReminder> findDueReminders(@Param("now") LocalDateTime now);

    List<MedicationReminder> findByAppointmentId(Long appointmentId);

    List<MedicationReminder> findByPatientIdAndStatus(Long patientId, ReminderStatus status);
}
