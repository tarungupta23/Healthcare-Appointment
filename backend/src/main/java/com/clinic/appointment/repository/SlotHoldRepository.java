package com.clinic.appointment.repository;

import com.clinic.appointment.entity.HoldStatus;
import com.clinic.appointment.entity.SlotHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotHoldRepository extends JpaRepository<SlotHold, Long> {

    /**
     * Pessimistic write lock on any existing ACTIVE hold for this exact
     * doctor+slot. Combined with the transaction boundary in
     * SlotService.createHold(), this serialises concurrent hold attempts
     * for the same slot so only one caller proceeds at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SlotHold h where h.doctor.id = :doctorId and h.slotStart = :slotStart " +
           "and h.status = 'ACTIVE'")
    Optional<SlotHold> findActiveHoldForUpdate(@Param("doctorId") Long doctorId,
                                                @Param("slotStart") LocalDateTime slotStart);

    List<SlotHold> findByPatientIdAndStatus(Long patientId, HoldStatus status);

    @Query("select h from SlotHold h where h.status = 'ACTIVE' and h.expiresAt < :now")
    List<SlotHold> findExpiredActiveHolds(@Param("now") LocalDateTime now);

    @Query("select h from SlotHold h where h.doctor.id = :doctorId and h.status = 'ACTIVE' " +
           "and h.slotStart >= :dayStart and h.slotStart < :dayEnd and h.expiresAt >= :now")
    List<SlotHold> findActiveHoldsForDoctorOnDay(@Param("doctorId") Long doctorId,
                                                  @Param("dayStart") LocalDateTime dayStart,
                                                  @Param("dayEnd") LocalDateTime dayEnd,
                                                  @Param("now") LocalDateTime now);

    @Modifying
    @Query("update SlotHold h set h.status = 'EXPIRED' where h.id in :ids")
    void expireByIds(@Param("ids") List<Long> ids);
}
