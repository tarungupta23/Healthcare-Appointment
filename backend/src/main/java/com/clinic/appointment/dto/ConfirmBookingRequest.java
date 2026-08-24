package com.clinic.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmBookingRequest {
    @NotNull
    private Long holdId;

    @NotBlank
    private String symptomsText;
}
