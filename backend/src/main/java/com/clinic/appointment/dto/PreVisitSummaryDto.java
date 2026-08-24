package com.clinic.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Structured shape we ask the LLM to return (as JSON) for the pre-visit
 * summary, and that we parse the response into before persisting.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PreVisitSummaryDto {
    private String urgencyLevel;      // LOW / MEDIUM / HIGH
    private String chiefComplaint;
    private List<String> suggestedQuestions;
}
