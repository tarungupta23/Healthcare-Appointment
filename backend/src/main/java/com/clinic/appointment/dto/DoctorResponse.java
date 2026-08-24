package com.clinic.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DoctorResponse {
    private Long doctorId;
    private String fullName;
    private String specialisation;
    private String qualification;
    private Integer yearsExperience;
    private Integer slotDurationMinutes;
    private BigDecimal consultationFee;
    private String bio;
    private List<WorkingHoursDto> workingHours;
}
