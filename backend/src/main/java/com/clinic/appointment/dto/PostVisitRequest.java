package com.clinic.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class PostVisitRequest {
    @NotBlank
    private String doctorNotes;

    @Valid
    private List<PrescriptionItemDto> prescriptionItems;

    private String followUpInstructions;
}
