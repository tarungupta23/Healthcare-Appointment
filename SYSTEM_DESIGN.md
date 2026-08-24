# System Design Write-up

*(Word count target: ≤800)*

## 1. Double-booking prevention

Booking is split into two phases, and correctness is enforced at three
independent layers so no single point of failure can let two patients land on
the same slot.

**Layer 1 — pessimistic read lock.** `SlotService.createHold()` opens a
transaction and runs `SELECT ... FOR UPDATE` against any existing *active*
hold for the exact `(doctor_id, slot_start)`. If a concurrent request is
mid-transaction on the same slot, this blocks until it commits or rolls back,
then re-reads fresh state rather than a stale snapshot — this catches the
common case of two clicks arriving milliseconds apart.

**Layer 2 — the database unique constraint.** The row lock above can only
protect a row that already exists; it cannot stop two *first* requests for a
never-before-held slot from both passing the check in the same instant
(especially with multiple backend instances behind a load balancer, where an
in-JVM lock would be useless anyway). The real, unconditional guarantee is
`UNIQUE (doctor_id, slot_start)` on both `slot_holds` and `appointments`. The
second `INSERT` always fails with `DataIntegrityViolationException`, which
`GlobalExceptionHandler` turns into a friendly `409 Conflict`. This is the
guarantee that actually matters; the lock is an optimisation to avoid a doomed
insert in the common case, not a substitute for it.

**Layer 3 — server-side slot validation.** Before any hold is created,
`DoctorService.validateSlotBookable()` re-checks the slot against the
doctor's real working hours and leave calendar, independent of whatever a
possibly-stale frontend showed the user — a slot list fetched a few minutes
earlier can never be used to sneak past a leave day added in between.
Rescheduling an existing appointment reuses this exact same three-layer
validation against the new slot, so a moved appointment can never land on
an occupied or invalid time either.

## 2. Slot hold mechanism

A raw "check-then-book" flow has a race window: the patient sees a free slot,
starts filling in symptoms, and someone else books it before they submit. To
close that window, picking a slot immediately creates a `slot_holds` row with
a 5-minute TTL (`SLOT_HOLD_MINUTES`) rather than booking outright. The
availability endpoint treats any slot with a live hold as unavailable, so
other patients never even see it as bookable. Submitting the symptom form
calls `confirmBooking`, which atomically flips the hold to `CONSUMED` and
inserts the real `Appointment` row (protected by the same unique constraint
above). If the countdown expires first, the frontend surfaces this and the
patient must reselect — a scheduled sweep (`SlotService.expireStaleHolds`,
every 60s) marks lapsed holds `EXPIRED` so the slot becomes visible again
without waiting on the next availability query's own leniency check.

## 3. Doctor leave conflict handling

`DoctorService.markDoctorOnLeave()` runs the leave insert and the cancellation
of every existing active appointment on that date **inside one transaction**:
either the leave is recorded and all conflicting bookings move to
`CANCELLED_BY_LEAVE`, or none of it happens. Each cancelled appointment
queues two emails (patient: apology + rebook prompt; doctor: confirmation of
what was auto-cancelled) via the outbox described below, and — outside the
transaction, best-effort — its Google Calendar events are deleted. Because
emailing is decoupled from the DB write (outbox pattern), a mail server
outage can never leave the leave-marking transaction half-done or block the
admin's request.

## 4. Notification & external-service failure handling

Two categories of external dependency exist — **email** and
**LLM/Calendar** — and each fails differently, so each gets a different
strategy.

**Email** uses a transactional outbox (`email_outbox`): queuing methods
(`EmailService.queue*`) just insert a `PENDING` row inside the caller's own
transaction, so a booking/cancellation/leave action always succeeds even if
SMTP is completely down. A scheduled worker (`processOutbox`, every 30s)
sends due rows and, on failure, reschedules with exponential backoff
(1, 2, 4, 8, 16 minutes) up to 5 attempts before marking the row `FAILED` for
later inspection — no infinite retry loop, no silent data loss, no request
ever blocked on SMTP latency.

**LLM calls** (`LlmService`) run through `WebClient` with a bounded timeout
and capped retries (skipping retries on 4xx, since a malformed prompt won't
fix itself), wrapped in a try/catch that returns a typed
`success/failure` result rather than throwing. Callers persist an explicit
`PENDING/SUCCESS/FAILED` status alongside the summary and always have a
fallback: the pre-visit screen shows the doctor raw symptom text if the AI
summary failed; the post-visit summary falls back to a summary assembled
directly from the doctor's structured notes and prescription — the patient
never receives nothing, just a slightly less polished version.

**Google Calendar sync** is treated as pure best-effort everywhere it's
called (`syncOnBooking`/`syncOnCancellation`): every method catches its own
exceptions, logs, and leaves the `*_calendar_event_id` columns null on
failure. A missing OAuth grant, an expired token, or a Google outage never
prevents booking, confirming, or cancelling an appointment — calendar state
simply catches up the next time sync succeeds.
