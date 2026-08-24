package com.clinic.appointment.service;

import com.clinic.appointment.entity.Appointment;
import com.clinic.appointment.entity.LlmStatus;
import com.clinic.appointment.entity.Urgency;
import com.clinic.appointment.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Separated into its own Spring bean (rather than a method on
 * AppointmentService) purely so that @Async is applied via Spring's proxy
 * mechanism correctly - self-invocation (a class calling its own @Async
 * method through `this`) silently runs synchronously, which would defeat
 * the purpose of keeping the LLM call off the booking request's critical
 * path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreVisitSummaryGenerator {

    private final AppointmentRepository appointmentRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("taskExecutor")
    @Transactional
    public void generateAsync(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId).orElse(null);
        if (appt == null) return;

        LlmService.LlmResult result = llmService.generatePreVisitSummary(appt.getSymptomsText());

        if (result.success) {
            try {
                JsonNode node = objectMapper.readTree(stripCodeFences(result.rawText));
                String urgencyRaw = node.path("urgencyLevel").asText("MEDIUM").toUpperCase();
                Urgency urgency = switch (urgencyRaw) {
                    case "LOW" -> Urgency.LOW;
                    case "HIGH" -> Urgency.HIGH;
                    default -> Urgency.MEDIUM;
                };
                appt.setPreVisitUrgency(urgency);
                appt.setPreVisitSummaryJson(node.toString());
                appt.setPreVisitLlmStatus(LlmStatus.SUCCESS);
            } catch (Exception parseEx) {
                log.error("Could not parse LLM pre-visit JSON for appointment {}: {}", appointmentId, parseEx.getMessage());
                appt.setPreVisitLlmStatus(LlmStatus.FAILED);
            }
        } else {
            appt.setPreVisitLlmStatus(LlmStatus.FAILED);
        }
        appt.setPreVisitGeneratedAt(LocalDateTime.now());
        appointmentRepository.save(appt);
    }

    private String stripCodeFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(json)?", "").trim();
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3).trim();
        }
        return t;
    }
}
