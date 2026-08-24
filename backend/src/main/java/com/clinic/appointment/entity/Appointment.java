package com.clinic.appointment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_doctor_slot", columnNames = {"doctor_id", "slot_start"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalDateTime slotEnd;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(name = "booked_at")
    private LocalDateTime bookedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    // Pre-visit symptoms
    @Column(name = "symptoms_text", columnDefinition = "TEXT")
    private String symptomsText;

    @Column(name = "symptom_submitted_at")
    private LocalDateTime symptomSubmittedAt;

    @Column(name = "pre_visit_summary_json", columnDefinition = "TEXT")
    private String preVisitSummaryJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_visit_urgency")
    private Urgency preVisitUrgency;

    @Column(name = "pre_visit_generated_at")
    private LocalDateTime preVisitGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_visit_llm_status")
    @Builder.Default
    private LlmStatus preVisitLlmStatus = LlmStatus.PENDING;

    // Post-visit
    @Column(name = "doctor_notes", columnDefinition = "TEXT")
    private String doctorNotes;

    @Column(name = "prescription_json", columnDefinition = "TEXT")
    private String prescriptionJson;

    @Column(name = "post_visit_completed_at")
    private LocalDateTime postVisitCompletedAt;

    @Column(name = "post_visit_summary_text", columnDefinition = "TEXT")
    private String postVisitSummaryText;

    @Column(name = "post_visit_generated_at")
    private LocalDateTime postVisitGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_visit_llm_status")
    @Builder.Default
    private LlmStatus postVisitLlmStatus = LlmStatus.PENDING;

    @Column(name = "patient_calendar_event_id")
    private String patientCalendarEventId;

    @Column(name = "doctor_calendar_event_id")
    private String doctorCalendarEventId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (bookedAt == null) bookedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
