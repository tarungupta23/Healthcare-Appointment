package com.clinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DoctorLeaveRequest {
    @NotNull
    private LocalDate leaveDate;
    private String reason;
}
