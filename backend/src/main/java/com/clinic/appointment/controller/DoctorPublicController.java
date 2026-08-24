package com.clinic.appointment.controller;

import com.clinic.appointment.dto.DoctorResponse;
import com.clinic.appointment.dto.SlotResponse;
import com.clinic.appointment.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Publicly browsable doctor directory & availability - no login required to search. */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorPublicController {

    private final DoctorService doctorService;

    @GetMapping("/search")
    public ResponseEntity<List<DoctorResponse>> search(@RequestParam(required = false) String specialisation) {
        return ResponseEntity.ok(doctorService.searchDoctors(specialisation));
    }

    @GetMapping("/{doctorId}")
    public ResponseEntity<DoctorResponse> getDoctor(@PathVariable Long doctorId) {
        return ResponseEntity.ok(doctorService.getDoctor(doctorId));
    }

    @GetMapping("/{doctorId}/availability")
    public ResponseEntity<List<SlotResponse>> getAvailability(
            @PathVariable Long doctorId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(doctorService.getAvailability(doctorId, date));
    }
}
