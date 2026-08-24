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
public class HoldSlotResponse {
    private Long holdId;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private LocalDateTime expiresAt;
}
