package com.clinic.appointment.controller;

import com.clinic.appointment.dto.AppointmentResponse;
import com.clinic.appointment.dto.CancelAppointmentRequest;
import com.clinic.appointment.dto.PostVisitRequest;
import com.clinic.appointment.dto.RescheduleRequest;
import com.clinic.appointment.security.AppUserDetails;
import com.clinic.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorPortalController {

    private final AppointmentService appointmentService;

    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(@AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(appointmentService.getDoctorAppointments(principal.getId()));
    }

    @GetMapping("/appointments/{appointmentId}")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(appointmentService.getAppointment(appointmentId));
    }

    /** Doctor submits notes + prescription after the visit; triggers LLM patient-friendly summary + reminders. */
    @PostMapping("/appointments/{appointmentId}/post-visit")
    public ResponseEntity<AppointmentResponse> submitPostVisit(@AuthenticationPrincipal AppUserDetails principal,
                                                                 @PathVariable Long appointmentId,
                                                                 @Valid @RequestBody PostVisitRequest request) {
        return ResponseEntity.ok(appointmentService.submitPostVisitNotes(principal.getId(), appointmentId, request));
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
