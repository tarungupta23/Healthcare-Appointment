package com.clinic.appointment.controller;

import com.clinic.appointment.dto.*;
import com.clinic.appointment.security.AppUserDetails;
import com.clinic.appointment.service.AppointmentService;
import com.clinic.appointment.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final SlotService slotService;
    private final AppointmentService appointmentService;

    /** Step 1 of booking: reserve the slot for a few minutes while the patient fills the symptom form. */
    @PostMapping("/slots/hold")
    public ResponseEntity<HoldSlotResponse> holdSlot(@AuthenticationPrincipal AppUserDetails principal,
                                                       @Valid @RequestBody HoldSlotRequest request) {
        return ResponseEntity.ok(slotService.createHold(principal.getId(), request.getDoctorId(), request.getSlotStart()));
    }

    /** Step 2 of booking: submit symptoms and confirm, converting the hold into a real appointment. */
    @PostMapping("/appointments/confirm")
    public ResponseEntity<AppointmentResponse> confirmBooking(@AuthenticationPrincipal AppUserDetails principal,
                                                                @Valid @RequestBody ConfirmBookingRequest request) {
        return ResponseEntity.ok(appointmentService.confirmBooking(principal.getId(), request));
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(@AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(appointmentService.getPatientAppointments(principal.getId()));
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(@AuthenticationPrincipal AppUserDetails principal,
                                                        @PathVariable Long appointmentId,
                                                        @RequestBody(required = false) CancelAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.cancelAppointment(
                principal.getId(), principal.getRole(), appointmentId, request));
    }

    /** Moves an existing appointment to a new slot in place of cancel+rebook. */
    @PostMapping("/appointments/{appointmentId}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(@AuthenticationPrincipal AppUserDetails principal,
                                                            @PathVariable Long appointmentId,
                                                            @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(
                principal.getId(), principal.getRole(), appointmentId, request));
    }

}
