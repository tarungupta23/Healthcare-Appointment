package com.clinic.appointment.controller;

import com.clinic.appointment.dto.*;
import com.clinic.appointment.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DoctorService doctorService;

    @PostMapping("/doctors")
    public ResponseEntity<DoctorResponse> createDoctor(@Valid @RequestBody DoctorCreateRequest request) {
        return ResponseEntity.ok(doctorService.createDoctor(request));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorResponse>> listDoctors() {
        return ResponseEntity.ok(doctorService.searchDoctors(null));
    }

    @PutMapping("/doctors/{doctorId}/working-hours")
    public ResponseEntity<DoctorResponse> updateWorkingHours(@PathVariable Long doctorId,
                                                               @RequestBody List<WorkingHoursDto> hours) {
        return ResponseEntity.ok(doctorService.updateWorkingHours(doctorId, hours));
    }

    /** Marks a doctor unavailable for a date; any existing bookings on that date are auto-cancelled and both
     *  patient and doctor are notified by email (see DoctorService.markDoctorOnLeave). */
    @PostMapping("/doctors/{doctorId}/leave")
    public ResponseEntity<List<AppointmentResponse>> markLeave(@PathVariable Long doctorId,
                                                                 @Valid @RequestBody DoctorLeaveRequest request) {
        return ResponseEntity.ok(doctorService.markDoctorOnLeave(doctorId, request));
    }
}
