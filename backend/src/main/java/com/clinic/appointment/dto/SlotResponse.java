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
public class SlotResponse {
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private boolean available;
}
