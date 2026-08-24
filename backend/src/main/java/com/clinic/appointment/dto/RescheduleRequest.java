package com.clinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RescheduleRequest {
    @NotNull
    private LocalDateTime newSlotStart;
}
