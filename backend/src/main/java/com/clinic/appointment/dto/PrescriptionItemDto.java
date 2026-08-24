package com.clinic.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrescriptionItemDto {
    @NotBlank
    private String medicationName;

    private String dosage;

    @NotBlank
    private String frequency; // e.g. "TWICE_DAILY", "ONCE_DAILY", "THRICE_DAILY", "EVERY_8_HOURS"

    @NotNull
    private Integer durationDays;

    private String instructions; // e.g. "after food"
}
