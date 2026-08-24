package com.clinic.appointment.repository;

import com.clinic.appointment.entity.Appointment;
import com.clinic.appointment.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByDoctorIdAndSlotStart(Long doctorId, LocalDateTime slotStart);

    /**
     * Row-level lock used inside the booking transaction as a second,
     * defense-in-depth check (the DB unique constraint is the ultimate
     * guarantee) before insert, avoiding an unnecessary failed-insert
     * round trip in the common case.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.doctor.id = :doctorId and a.slotStart = :slotStart " +
           "and a.status not in ('CANCELLED','CANCELLED_BY_LEAVE')")
    Optional<Appointment> findActiveForUpdate(@Param("doctorId") Long doctorId,
                                               @Param("slotStart") LocalDateTime slotStart);

    List<Appointment> findByPatientIdOrderBySlotStartDesc(Long patientId);

    List<Appointment> findByDoctorIdOrderBySlotStartDesc(Long doctorId);

    @Query("select a from Appointment a where a.doctor.id = :doctorId " +
           "and a.slotStart >= :dayStart and a.slotStart < :dayEnd " +
           "and a.status not in ('CANCELLED','CANCELLED_BY_LEAVE')")
    List<Appointment> findActiveByDoctorAndDay(@Param("doctorId") Long doctorId,
                                                @Param("dayStart") LocalDateTime dayStart,
                                                @Param("dayEnd") LocalDateTime dayEnd);

    @Query("select a from Appointment a where a.doctor.id = :doctorId and a.slotStart >= :from " +
           "and a.status not in ('CANCELLED','CANCELLED_BY_LEAVE')")
    List<Appointment> findUpcomingByDoctor(@Param("doctorId") Long doctorId, @Param("from") LocalDateTime from);

    @Query("select a from Appointment a where a.status = 'CONFIRMED' " +
           "and a.slotStart between :from and :to")
    List<Appointment> findConfirmedInWindow(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Appointments starting soon that haven't had a reminder email queued yet. */
    @Query("select a from Appointment a where a.status = 'CONFIRMED' " +
           "and a.reminderSentAt is null and a.slotStart between :from and :to")
    List<Appointment> findDueForReminder(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
