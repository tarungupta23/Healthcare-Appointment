# Meridian Clinic — Healthcare Appointment & Follow-up Manager

Application Host URL : https://healthcare-appointment-production-324d.up.railway.app

A full-stack clinic platform with separate portals for **patients**, **doctors**, and an **admin**.
Patients book appointments and submit symptoms in advance; doctors get an AI-generated pre-visit
brief and produce a patient-friendly post-visit summary; both sides stay in sync via email and
Google Calendar.

**Stack:** Spring Boot 3 (Java 17) · MySQL 8 · React 18 (Vite) · Anthropic API (LLM) · Google
Calendar API (OAuth 2.0) · SMTP email

```
healthcare-appointment-manager/
├── backend/            Spring Boot REST API
├── frontend/           React (Vite) SPA
├── docker-compose.yml  Local MySQL for development
└── SYSTEM_DESIGN.md    Design write-up (double-booking, leave conflicts, slot holds, notifications)
```

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ (or use the included `mvnw` wrapper) |
| Node.js | 18+ |
| MySQL | 8.0 (or Docker) |
| An Anthropic **or** Google Gemini API key | for LLM summaries — Gemini is free indefinitely, Anthropic gives a one-time trial credit (see §6) |
| A Google Cloud OAuth 2.0 client | for Calendar sync (optional — app degrades gracefully without one) |
| An SMTP account | Gmail app password, SendGrid, Mailgun, etc. |

---

## 2. Quick start (local)

### 2.1 Database

```bash
docker compose up -d          # starts MySQL 8 on localhost:3306, db "appointment_manager"
```

Or point `DB_HOST`/`DB_PORT`/`DB_USERNAME`/`DB_PASSWORD` in `backend/.env` at any MySQL 8 instance
you already have. Tables are created automatically by **Flyway** migrations on first boot — no
manual SQL needed.

### 2.2 Backend

```bash
cd backend
cp .env.example .env          # fill in DB / SMTP / LLM / Google Calendar values
# export the vars in .env into your shell, or configure them in your IDE run config
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Health check: `GET /actuator/health`.

A default admin account is seeded by migration `V2__seed_admin.sql`:

```
email:    admin@clinic.com
password: Admin@123
```

**Change this password immediately in any real deployment.**

### 2.3 Frontend

```bash
cd frontend
cp .env.example .env          # VITE_API_BASE_URL defaults to http://localhost:8080/api
npm install
npm run dev
```

Opens on `http://localhost:5173`.

### 2.4 First-run walkthrough

1. Log in as `admin@clinic.com` → **Add doctor** → set specialisation, slot length, and weekly
   working hours.
2. Log out, register a new **patient** account.
3. **Find a doctor** → pick a date → pick a slot (this creates a 5-minute *hold*) → describe
   symptoms → **Confirm appointment**.
4. Log in as the doctor (email/temp password set by admin) → open the appointment → see the
   AI pre-visit brief → after the visit, fill in clinical notes + prescription → **Complete visit**.
5. Log back in as the patient → the completed appointment now shows the AI-generated,
   patient-friendly summary and medication schedule.
6. As admin, try **Manage leave** on a doctor with an existing booking → confirm the booking is
   auto-cancelled and both parties are emailed.

---

## 3. Environment variables (backend)

See `backend/.env.example` for the full annotated list. Key groups:

- **Database** — `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- **JWT** — `JWT_SECRET` (256-bit+ random string), `JWT_EXPIRATION_MS`
- **Email (SMTP)** — `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
- **LLM (Anthropic)** — `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `LLM_TIMEOUT_SECONDS`, `LLM_MAX_RETRIES`
- **Google Calendar** — `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`
- **Slot holds** — `SLOT_HOLD_MINUTES` (default 5)

Every value has a safe local default except secrets/credentials, which are left blank on purpose.

---

## 4. Database schema

Full DDL lives in `backend/src/main/resources/db/migration/V1__init_schema.sql` (Flyway-managed,
runs automatically). Summary of tables:

| Table | Purpose |
|---|---|
| `users` | Login identity + role (`ADMIN`/`DOCTOR`/`PATIENT`) |
| `patients`, `doctors` | Role-specific profile data, 1:1 with `users` |
| `doctor_working_hours` | Recurring weekly availability windows per doctor |
| `doctor_leaves` | Dates a doctor is unavailable |
| `slot_holds` | Short-lived (default 5 min) reservation created when a patient picks a slot, before symptoms are submitted |
| `appointments` | The booking itself — symptoms, AI pre-visit summary, doctor notes, prescription, AI post-visit summary, calendar event ids. **`UNIQUE (doctor_id, slot_start)`** is the hard guarantee against double-booking |
| `medication_reminders` | Expanded from the prescription (frequency → concrete timestamps) |
| `email_outbox` | Transactional outbox — every email is queued here first, a background worker delivers with retry/backoff |
| `google_calendar_tokens` | Per-user OAuth2 access/refresh tokens |
| `audit_log` | Reserved for admin action auditing |

See `SYSTEM_DESIGN.md` for how these tables work together to prevent double-booking, handle leave
conflicts, and guarantee notification delivery.

---

## 5. API reference

Base URL: `http://localhost:8080/api`. Authenticated endpoints expect
`Authorization: Bearer <jwt>` (obtained from `/auth/login` or `/auth/register/patient`).

### Auth
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register/patient` | — | Register a new patient, returns JWT |
| POST | `/auth/login` | — | Log in (any role), returns JWT |
| GET | `/auth/google/authorize` | JWT | Get the Google OAuth consent URL for the logged-in user |
| GET | `/auth/google/callback` | — | OAuth2 redirect target (Google calls this) |

### Public doctor directory
| Method | Path | Description |
|---|---|---|
| GET | `/doctors/search?specialisation=` | List/search doctors |
| GET | `/doctors/{doctorId}` | Doctor profile + working hours |
| GET | `/doctors/{doctorId}/availability?date=YYYY-MM-DD` | Free/booked slots for a date |

### Patient (JWT, role `PATIENT`)
| Method | Path | Description |
|---|---|---|
| POST | `/patient/slots/hold` | `{doctorId, slotStart}` → reserve a slot for `SLOT_HOLD_MINUTES` |
| POST | `/patient/appointments/confirm` | `{holdId, symptomsText}` → confirms booking, triggers AI pre-visit summary, email, calendar sync |
| GET | `/patient/appointments` | List own appointments |
| POST | `/patient/appointments/{id}/cancel` | Cancel own appointment |
| POST | `/patient/appointments/{id}/reschedule` | `{newSlotStart}` → moves the appointment to a new slot, re-validates against double-booking/leave, updates the Google Calendar event in place |

### Doctor (JWT, role `DOCTOR`)
| Method | Path | Description |
|---|---|---|
| GET | `/doctor/appointments` | List own appointments |
| GET | `/doctor/appointments/{id}` | Appointment detail incl. pre-visit AI summary |
| POST | `/doctor/appointments/{id}/post-visit` | `{doctorNotes, prescriptionItems[], followUpInstructions}` → completes visit, generates AI patient summary, schedules medication reminders |
| POST | `/doctor/appointments/{id}/cancel` | Cancel an appointment |
| POST | `/doctor/appointments/{id}/reschedule` | `{newSlotStart}` → same reschedule flow as the patient endpoint |

### Admin (JWT, role `ADMIN`)
| Method | Path | Description |
|---|---|---|
| POST | `/admin/doctors` | Create a doctor account + working hours |
| GET | `/admin/doctors` | List all doctors |
| PUT | `/admin/doctors/{id}/working-hours` | Replace weekly working hours |
| POST | `/admin/doctors/{id}/leave` | Mark a date as leave — **auto-cancels existing bookings on that date and emails both sides** |

All error responses share a shape:
```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "message": "This slot was just booked by someone else...", "path": "/api/patient/appointments/confirm" }
```

---

## 6. LLM prompts used & choosing a provider

The app supports **two interchangeable LLM providers**, switched purely by config (`LLM_PROVIDER`
env var) — no code changes needed:

| Provider | `LLM_PROVIDER` | Cost | Get a key |
|---|---|---|---|
| **Google Gemini** (default) | `gemini` | **Permanently free** — no card, no expiry, ~1,500 req/day on `gemini-2.5-flash` | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) — sign in with any Google account, no card |
| **Anthropic Claude** | `anthropic` | One-time ~$5 trial credit on signup (phone verification, no card), then pay-as-you-go | [console.anthropic.com](https://console.anthropic.com/) |

Only the API key for whichever provider you choose needs to be filled in — see
`backend/.env.example`. Gemini is the default specifically so the project keeps working for free
indefinitely without needing to top up credits later.

Both prompts below are sent verbatim to whichever provider is active. See `LlmService.java` for
the exact code (`callAnthropic`/`callGemini`).

**Pre-visit summary** (`generatePreVisitSummary`):
> Analyse these symptoms and return ONLY a JSON object (no markdown fences, no prose) with exactly
> these fields: `"urgencyLevel"` (one of "Low", "Medium", "High"), `"chiefComplaint"` (a short
> one-sentence summary), `"suggestedQuestions"` (an array of exactly 3 short questions the doctor
> could ask the patient). Symptoms: `<symptoms>`

**Post-visit summary** (`generatePostVisitSummary`):
> Convert these clinical notes into a patient-friendly summary with a medication schedule and
> follow-up steps. Use simple, warm, non-alarming language a patient with no medical background can
> understand. Keep it under 250 words. Do not use markdown headers.
> Clinical notes: `<notes>` / Prescription: `<prescription>` / Additional follow-up instructions: `<followUp>`

**Failure handling:** every LLM call goes through `WebClient` with a configurable timeout
(`LLM_TIMEOUT_SECONDS`), retry with exponential backoff (`LLM_MAX_RETRIES`, skipped for
4xx/429 errors), and a hard try/catch. On failure the appointment is persisted with
`preVisitLlmStatus`/`postVisitLlmStatus = FAILED` instead of throwing — the pre-visit screen falls
back to showing the doctor the patient's raw symptom text, and the post-visit summary falls back to
a plain-text summary assembled directly from the doctor's structured notes/prescription (see
`AppointmentService.buildFallbackPostVisitSummary`). **The booking and visit-completion flows never
fail because of the LLM.**

---

## 7. Google Calendar setup

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project (or reuse one)
   and enable the **Google Calendar API**.
2. Under **APIs & Services → Credentials**, create an **OAuth 2.0 Client ID** of type
   *Web application*.
3. Add an authorized redirect URI matching `GOOGLE_REDIRECT_URI`
   (default `http://localhost:8080/api/auth/google/callback`; use your deployed backend URL in
   production).
4. Copy the generated **Client ID** and **Client secret** into `GOOGLE_CLIENT_ID` /
   `GOOGLE_CLIENT_SECRET` in `backend/.env`.
5. In the app, a logged-in patient or doctor calls `GET /api/auth/google/authorize` to get a
   consent URL, completes Google's consent screen, and Google redirects back to
   `/api/auth/google/callback?code=...&state=<userId>`, which exchanges the code for tokens and
   stores them in `google_calendar_tokens`.
6. From then on, booking an appointment creates an event on **both** the patient's and doctor's
   own primary calendars (`GoogleCalendarService.syncOnBooking`); cancelling deletes both events
   (`syncOnCancellation`); **rescheduling updates both events in place** rather than deleting and
   recreating them (`syncOnBooking`/`updateEvent`, triggered from `AppointmentService.rescheduleAppointment`).
   All calendar calls are best-effort — a missing/expired grant, or Google being briefly
   unavailable, never blocks booking/rescheduling/cancellation (see `SYSTEM_DESIGN.md §4`).

In the app itself, a **"Connect Google Calendar"** button appears on both the patient and doctor
dashboards, which calls step 5 above for the logged-in user.

---

## 8. Email

The backend uses **Spring Mail** (`JavaMailSender`) — Java's SMTP client, functionally the same
role Nodemailer plays in a Node stack — so it works with any SMTP provider by changing config
alone, no code changes.

**Recommended: [Brevo](https://app.brevo.com)** (formerly Sendinblue) — a real transactional email
service in the same category as SendGrid/Mailgun, with a genuinely **permanent free tier** (300
emails/day, no card required). Sign up → **Settings → SMTP & API → SMTP** tab for your
host/port/login/key → verify a sender address in the dashboard (a click-to-verify step, not a real
mailbox you need to own) → plug the values into `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_USERNAME`,
`MAIL_PASSWORD` (the generated SMTP key), `MAIL_FROM`.

**Note on SendGrid/Mailgun**: both have removed their permanent free tiers as of 2025/2026
(SendGrid is now a 60-day trial only, Mailgun requires a card upfront) — Brevo is the closest
free-and-easy equivalent still available at time of writing.

**Alternative: Gmail SMTP** — simpler if you'd rather not create another account, using a
*throwaway* Gmail address (not your personal one): enable 2-Step Verification, generate an
[App Password](https://myaccount.google.com/apppasswords) (never your real account password), and
note Gmail requires `MAIL_FROM` to match `MAIL_USERNAME`.

Notifications sent: booking confirmation (patient + doctor), appointment reminder (~24h before the
slot, patient + doctor), reschedule notice (patient + doctor), cancellation (patient + doctor),
leave-triggered cancellation (patient + doctor), medication reminders, post-visit summary, and a
doctor welcome email with temporary credentials.

---

## 9. Deployment

Any platform that runs a Spring Boot jar + a MySQL instance + serves a static frontend build works
(Render, Railway, Fly.io, an EC2 box, etc.):

```bash
# Backend
cd backend
./mvnw clean package -DskipTests
java -jar target/appointment-manager-1.0.0.jar   # reads config from environment variables

# Frontend
cd frontend
npm run build       # outputs static files to dist/ — deploy to Vercel/Netlify/any static host,
                     # or serve from the same box behind nginx
```

Remember to set `CORS_ALLOWED_ORIGINS` on the backend to your deployed frontend's origin, and
`VITE_API_BASE_URL` on the frontend to your deployed backend's `/api` URL.

---

## 10. Testing the concurrency guarantees

To sanity-check double-booking prevention, fire two simultaneous `POST /patient/slots/hold`
requests for the same doctor+slot from two different patient accounts — the loser should get a
`409 Conflict` (`"This slot is currently being booked by another patient..."`). If both somehow
create holds (e.g. across app instances with no shared lock), the second `confirmBooking` call will
still fail atomically on the `uq_doctor_slot` unique constraint. See `SYSTEM_DESIGN.md` for the full
reasoning.
