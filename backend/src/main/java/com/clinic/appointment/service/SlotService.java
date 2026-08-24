package com.clinic.appointment.service;

import com.clinic.appointment.dto.HoldSlotResponse;
import com.clinic.appointment.entity.*;
import com.clinic.appointment.exception.ResourceNotFoundException;
import com.clinic.appointment.exception.SlotUnavailableException;
import com.clinic.appointment.exception.UnauthorizedActionException;
import com.clinic.appointment.repository.AppointmentRepository;
import com.clinic.appointment.repository.PatientRepository;
import com.clinic.appointment.repository.SlotHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns the "slot hold" concurrency mechanism described in the system design
 * write-up:
 *
 *  1. HOLD  - patient picks a slot -> a short-lived (default 5 min) hold row
 *             is created. A pessimistic SELECT ... FOR UPDATE is taken on any
 *             existing ACTIVE hold for the same doctor+slot first, so two
 *             requests that arrive close together for a slot that already
 *             has a hold are serialised and the second is rejected fast.
 *  2. CONFIRM - after the patient submits symptoms, the hold is consumed and
 *             converted into a real Appointment row. The `appointments` table
 *             has a UNIQUE (doctor_id, slot_start) constraint, which is the
 *             final, unconditional guarantee against double-booking: even if
 *             two app server instances raced past the in-memory/DB-row lock
 *             above (e.g. no lock existed yet because neither hold had been
 *             created), the second INSERT fails atomically and is translated
 *             into a friendly 409 response.
 *  3. EXPIRE - a scheduled job sweeps holds whose expiresAt has passed and
 *             marks them EXPIRED so the slot becomes bookable again.
 */
@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotHoldRepository slotHoldRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorService doctorService;

    @Value("${app.slot.hold-duration-minutes:5}")
    private int holdDurationMinutes;

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public HoldSlotResponse createHold(Long patientUserId, Long doctorId, LocalDateTime slotStart) {
        Doctor doctor = doctorService.validateSlotBookable(doctorId, slotStart);
        LocalDateTime slotEnd = slotStart.plusMinutes(doctor.getSlotDurationMinutes());

        Patient patient = patientRepository.findByUserId(patientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        // Defense-in-depth check #1: is there already a live hold on this exact slot?
        // The pessimistic lock means a second concurrent transaction hitting this
        // same query blocks until the first commits/rolls back, then re-reads
        // fresh data rather than a stale snapshot.
        slotHoldRepository.findActiveHoldForUpdate(doctorId, slotStart).ifPresent(existing -> {
            if (existing.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new SlotUnavailableException("This slot is currently being booked by another patient. Please try another slot.");
            }
        });

        // Defense-in-depth check #2: already a confirmed/pending appointment?
        appointmentRepository.findActiveForUpdate(doctorId, slotStart).ifPresent(existing -> {
            throw new SlotUnavailableException("This slot has already been booked.");
        });

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(holdDurationMinutes);

        SlotHold hold = SlotHold.builder()
                .doctor(doctor)
                .patient(patient)
                .slotStart(slotStart)
                .slotEnd(slotEnd)
                .expiresAt(expiresAt)
                .status(HoldStatus.ACTIVE)
                .build();

        try {
            hold = slotHoldRepository.save(hold);
        } catch (DataIntegrityViolationException ex) {
            // Ultimate safety net if two holds still raced past the checks above
            // (e.g. multi-instance deployment without a shared lock).
            throw new SlotUnavailableException("This slot was just taken by another patient. Please pick a different slot.");
        }

        return HoldSlotResponse.builder()
                .holdId(hold.getId())
                .slotStart(hold.getSlotStart())
                .slotEnd(hold.getSlotEnd())
                .expiresAt(hold.getExpiresAt())
                .build();
    }

    @Transactional
    public SlotHold consumeHoldForBooking(Long holdId, Long patientId) {
        SlotHold hold = slotHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot hold not found or already expired"));

        if (!hold.getPatient().getId().equals(patientId)) {
            throw new UnauthorizedActionException("This slot hold does not belong to you");
        }

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new SlotUnavailableException("This slot hold is no longer active. Please select a slot again.");
        }

        if (hold.getExpiresAt().isBefore(LocalDateTime.now())) {
            hold.setStatus(HoldStatus.EXPIRED);
            slotHoldRepository.save(hold);
            throw new SlotUnavailableException("Your slot hold expired. Please select the slot again to continue.");
        }

        hold.setStatus(HoldStatus.CONSUMED);
        return slotHoldRepository.save(hold);
    }

    /**
     * Runs every 60 seconds; purges stale ACTIVE holds so their slots become
     * visible as available again. Kept separate from the read path (which
     * also treats expired-but-still-ACTIVE holds as free) purely for data
     * hygiene and to keep the "active holds today" queries cheap over time.
     */
    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void expireStaleHolds() {
        List<SlotHold> expired = slotHoldRepository.findExpiredActiveHolds(LocalDateTime.now());
        if (!expired.isEmpty()) {
            List<Long> ids = expired.stream().map(SlotHold::getId).collect(Collectors.toList());
            slotHoldRepository.expireByIds(ids);
        }
    }
}
