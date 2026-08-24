-- =====================================================================
-- Healthcare Appointment & Follow-up Manager - Initial Schema
-- =====================================================================

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('ADMIN','DOCTOR','PATIENT') NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(20),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE patients (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    date_of_birth   DATE,
    gender          VARCHAR(20),
    address         VARCHAR(255),
    emergency_contact VARCHAR(20),
    CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE doctors (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    specialisation  VARCHAR(100) NOT NULL,
    qualification   VARCHAR(150),
    years_experience INT DEFAULT 0,
    slot_duration_minutes INT NOT NULL DEFAULT 20,
    consultation_fee DECIMAL(10,2) DEFAULT 0,
    bio             TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doctor_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_doctor_specialisation (specialisation)
) ENGINE=InnoDB;

-- Weekly recurring working hours per doctor, e.g. MONDAY 09:00-17:00
CREATE TABLE doctor_working_hours (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id       BIGINT NOT NULL,
    day_of_week     ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    CONSTRAINT fk_wh_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    UNIQUE KEY uq_doctor_day (doctor_id, day_of_week, start_time)
) ENGINE=InnoDB;

-- Doctor leave / unavailable dates
CREATE TABLE doctor_leaves (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id       BIGINT NOT NULL,
    leave_date      DATE NOT NULL,
    reason          VARCHAR(255),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_leave_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    UNIQUE KEY uq_doctor_leave_date (doctor_id, leave_date)
) ENGINE=InnoDB;

-- =====================================================================
-- SLOT HOLD: short-lived reservation created the instant a patient picks
-- a slot, before they finish the symptom form. Prevents two patients
-- from completing the symptom form for the same slot simultaneously.
-- A background job purges expired holds. A unique constraint on
-- (doctor_id, slot_start) guarantees only one *active* hold/booking can
-- exist for a given slot at the database level regardless of app-level
-- race conditions.
-- =====================================================================
CREATE TABLE slot_holds (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id       BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    slot_start      DATETIME NOT NULL,
    slot_end        DATETIME NOT NULL,
    expires_at      DATETIME NOT NULL,
    status          ENUM('ACTIVE','CONSUMED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hold_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    CONSTRAINT fk_hold_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    INDEX idx_hold_expiry (status, expires_at)
) ENGINE=InnoDB;

-- Partial-unique behaviour (only one ACTIVE hold per doctor/slot) is
-- enforced in application code via SELECT ... FOR UPDATE plus this
-- supporting index, since MySQL unique indexes cannot be conditional.
CREATE INDEX idx_hold_doctor_slot ON slot_holds (doctor_id, slot_start, status);

-- =====================================================================
-- APPOINTMENTS
-- The UNIQUE KEY on (doctor_id, slot_start) is the hard database-level
-- guarantee against double booking: even if two requests race past the
-- application-level lock, the second INSERT will fail with a
-- DataIntegrityViolationException which the service layer converts into
-- a friendly "slot no longer available" response.
-- =====================================================================
CREATE TABLE appointments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id           BIGINT NOT NULL,
    patient_id          BIGINT NOT NULL,
    slot_start          DATETIME NOT NULL,
    slot_end            DATETIME NOT NULL,
    status              ENUM('PENDING_SYMPTOMS','CONFIRMED','CANCELLED','CANCELLED_BY_LEAVE','COMPLETED','NO_SHOW') NOT NULL DEFAULT 'CONFIRMED',
    booked_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at        DATETIME NULL,
    cancellation_reason VARCHAR(255),

    -- Pre-visit symptom capture
    symptoms_text       TEXT,
    symptom_submitted_at DATETIME NULL,

    -- LLM-generated pre-visit summary
    pre_visit_summary_json TEXT,
    pre_visit_urgency   ENUM('LOW','MEDIUM','HIGH') NULL,
    pre_visit_generated_at DATETIME NULL,
    pre_visit_llm_status ENUM('PENDING','SUCCESS','FAILED','SKIPPED') NOT NULL DEFAULT 'PENDING',

    -- Doctor's post visit notes
    doctor_notes        TEXT,
    prescription_json   TEXT,
    post_visit_completed_at DATETIME NULL,

    -- LLM-generated patient-friendly post-visit summary
    post_visit_summary_text TEXT,
    post_visit_generated_at DATETIME NULL,
    post_visit_llm_status ENUM('PENDING','SUCCESS','FAILED','SKIPPED') NOT NULL DEFAULT 'PENDING',

    -- Google Calendar sync
    patient_calendar_event_id VARCHAR(255),
    doctor_calendar_event_id  VARCHAR(255),

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_appt_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    UNIQUE KEY uq_doctor_slot (doctor_id, slot_start),
    INDEX idx_appt_patient (patient_id),
    INDEX idx_appt_doctor_date (doctor_id, slot_start),
    INDEX idx_appt_status (status)
) ENGINE=InnoDB;

-- =====================================================================
-- MEDICATION REMINDERS: expanded from the prescription (frequency ->
-- concrete reminder timestamps) by a background job right after the
-- post-visit summary is generated.
-- =====================================================================
CREATE TABLE medication_reminders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id  BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    medication_name VARCHAR(150) NOT NULL,
    dosage          VARCHAR(100),
    scheduled_at    DATETIME NOT NULL,
    status          ENUM('PENDING','SENT','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_attempt_at DATETIME NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reminder_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    INDEX idx_reminder_due (status, scheduled_at)
) ENGINE=InnoDB;

-- =====================================================================
-- EMAIL OUTBOX: every notification is written here first (transactional
-- outbox pattern) so a background retry worker can guarantee eventual
-- delivery even if SMTP is briefly unavailable, without ever blocking
-- the booking/cancellation request itself.
-- =====================================================================
CREATE TABLE email_outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_email VARCHAR(255) NOT NULL,
    recipient_role  ENUM('PATIENT','DOCTOR','ADMIN') NOT NULL,
    template        ENUM('BOOKING_CONFIRMATION','REMINDER','CANCELLATION','LEAVE_CANCELLATION','MEDICATION_REMINDER','POST_VISIT_SUMMARY') NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    body            TEXT NOT NULL,
    related_appointment_id BIGINT NULL,
    status          ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 5,
    last_error      VARCHAR(500),
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         DATETIME NULL,
    INDEX idx_outbox_due (status, next_attempt_at)
) ENGINE=InnoDB;

-- =====================================================================
-- GOOGLE CALENDAR OAUTH TOKENS per user (patient or doctor), so each
-- user's own calendar is used, not a shared clinic calendar.
-- =====================================================================
CREATE TABLE google_calendar_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    access_token    TEXT NOT NULL,
    refresh_token   TEXT,
    token_expiry    DATETIME,
    calendar_id     VARCHAR(255) DEFAULT 'primary',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gtoken_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Audit trail for admin actions on doctor profiles / leave, useful for evaluation & debugging
CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id   BIGINT,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    details         TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
