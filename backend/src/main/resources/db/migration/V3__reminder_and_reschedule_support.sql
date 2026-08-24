-- Adds support for:
--  1. Appointment reminder emails (distinct from medication reminders) -
--     reminder_sent_at tracks whether the "your appointment is tomorrow"
--     email has already gone out, so the reminder job never double-sends.
--  2. Reschedule notifications - a new RESCHEDULED template value.

ALTER TABLE appointments
    ADD COLUMN reminder_sent_at DATETIME NULL AFTER cancellation_reason;

ALTER TABLE email_outbox
    MODIFY COLUMN template ENUM(
        'BOOKING_CONFIRMATION','REMINDER','CANCELLATION','LEAVE_CANCELLATION',
        'MEDICATION_REMINDER','POST_VISIT_SUMMARY','RESCHEDULED'
    ) NOT NULL;

CREATE INDEX idx_appt_reminder_due ON appointments (status, slot_start, reminder_sent_at);
