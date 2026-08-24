package com.clinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HoldSlotRequest {
    @NotNull
    private Long doctorId;

    @NotNull
    private LocalDateTime slotStart;
}
