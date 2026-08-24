package com.clinic.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentResponse {
    private Long appointmentId;
    private Long doctorId;
    private String doctorName;
    private String specialisation;
    private Long patientId;
    private String patientName;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private String status;

    private String symptomsText;
    private String preVisitSummaryJson;
    private String preVisitUrgency;
    private String preVisitLlmStatus;

    private String doctorNotes;
    private String prescriptionJson;
    private String postVisitSummaryText;
    private String postVisitLlmStatus;

    private String cancellationReason;
}
