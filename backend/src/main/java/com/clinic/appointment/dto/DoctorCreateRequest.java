package com.clinic.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DoctorCreateRequest {
    @Email @NotBlank
    private String email;

    @NotBlank
    private String temporaryPassword;

    @NotBlank
    private String fullName;

    private String phone;

    @NotBlank
    private String specialisation;

    private String qualification;
    private Integer yearsExperience;

    @NotNull
    private Integer slotDurationMinutes;

    private BigDecimal consultationFee;
    private String bio;

    private List<WorkingHoursDto> workingHours;
}
