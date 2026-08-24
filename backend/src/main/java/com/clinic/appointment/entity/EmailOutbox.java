package com.clinic.appointment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Transactional-outbox pattern: every notification is persisted here first.
 * A background retry worker guarantees eventual delivery with exponential
 * backoff, decoupling the request/response cycle from SMTP availability.
 */
@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false)
    private RecipientRole recipientRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailTemplateType template;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "related_appointment_id")
    private Long relatedAppointmentId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EmailStatus status = EmailStatus.PENDING;

    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (nextAttemptAt == null) nextAttemptAt = LocalDateTime.now();
    }
}
