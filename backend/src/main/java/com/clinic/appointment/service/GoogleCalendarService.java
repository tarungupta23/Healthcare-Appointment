package com.clinic.appointment.service;

import com.clinic.appointment.entity.Appointment;
import com.clinic.appointment.entity.GoogleCalendarToken;
import com.clinic.appointment.entity.User;
import com.clinic.appointment.repository.GoogleCalendarTokenRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Google Calendar sync is treated as strictly best-effort: every public
 * method here catches its own exceptions, logs them, and returns
 * Optional.empty()/false rather than throwing, so a missing/expired OAuth
 * grant or a transient Google API outage never blocks booking, rescheduling,
 * or cancellation. Appointment rows simply keep a null *_calendar_event_id
 * until the user (re)connects their calendar or the next sync succeeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final GoogleCalendarTokenRepository tokenRepository;

    @Value("${app.google.calendar.client-id}")
    private String clientId;

    @Value("${app.google.calendar.client-secret}")
    private String clientSecret;

    @Value("${app.google.calendar.redirect-uri}")
    private String redirectUri;

    @Value("${app.google.calendar.application-name}")
    private String applicationName;

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);
    private static final String ZONE = "Asia/Kolkata";

    private GoogleAuthorizationCodeFlow buildFlow() throws Exception {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(clientId)
                .setClientSecret(clientSecret);
        GoogleClientSecrets secrets = new GoogleClientSecrets().setWeb(details);

        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                secrets,
                SCOPES)
                .setAccessType("offline")
                .build();
    }

    public String getAuthorizationUrl(Long userId) {
        try {
            return buildFlow().newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .set("prompt", "consent")
                    .setState(String.valueOf(userId))
                    .build();
        } catch (Exception e) {
            log.error("Failed to build Google authorization URL: {}", e.getMessage());
            throw new IllegalStateException("Google Calendar is not configured on this server");
        }
    }

    @Transactional
    public void handleOAuthCallback(String code, Long userId, User user) {
        try {
            GoogleAuthorizationCodeFlow flow = buildFlow();
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUri)
                    .execute();

            GoogleCalendarToken token = tokenRepository.findByUserId(userId)
                    .orElse(GoogleCalendarToken.builder().user(user).build());

            token.setAccessToken(tokenResponse.getAccessToken());
            if (tokenResponse.getRefreshToken() != null) {
                token.setRefreshToken(tokenResponse.getRefreshToken());
            }
            if (tokenResponse.getExpiresInSeconds() != null) {
                token.setTokenExpiry(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            }
            tokenRepository.save(token);
        } catch (Exception e) {
            log.error("Google OAuth callback failed for user {}: {}", userId, e.getMessage());
            throw new IllegalStateException("Could not complete Google Calendar connection: " + e.getMessage());
        }
    }

    private Optional<Calendar> calendarClientFor(Long userId) {
        try {
            Optional<GoogleCalendarToken> tokenOpt = tokenRepository.findByUserId(userId);
            if (tokenOpt.isEmpty()) return Optional.empty();

            GoogleCalendarToken tokenEntity = tokenOpt.get();
            Credential credential = buildFlow().createAndStoreCredential(
                    new TokenResponse()
                            .setAccessToken(tokenEntity.getAccessToken())
                            .setRefreshToken(tokenEntity.getRefreshToken()),
                    String.valueOf(userId));

            Calendar calendar = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName(applicationName)
                    .build();
            return Optional.of(calendar);
        } catch (Exception e) {
            log.warn("Could not build Google Calendar client for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> createEvent(Long userId, String calendarId, String summary, String description,
                                         LocalDateTime start, LocalDateTime end) {
        Optional<Calendar> calendarOpt = calendarClientFor(userId);
        if (calendarOpt.isEmpty()) return Optional.empty();

        try {
            Event event = new Event().setSummary(summary).setDescription(description);
            event.setStart(toEventDateTime(start));
            event.setEnd(toEventDateTime(end));

            Event created = calendarOpt.get().events()
                    .insert(calendarId != null ? calendarId : "primary", event)
                    .execute();
            return Optional.ofNullable(created.getId());
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar event for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean updateEvent(Long userId, String calendarId, String eventId, LocalDateTime start, LocalDateTime end) {
        Optional<Calendar> calendarOpt = calendarClientFor(userId);
        if (calendarOpt.isEmpty() || eventId == null) return false;
        try {
            Event event = calendarOpt.get().events().get(calendarId != null ? calendarId : "primary", eventId).execute();
            event.setStart(toEventDateTime(start));
            event.setEnd(toEventDateTime(end));
            calendarOpt.get().events().update(calendarId != null ? calendarId : "primary", eventId, event).execute();
            return true;
        } catch (Exception e) {
            log.warn("Failed to update Google Calendar event {} for user {}: {}", eventId, userId, e.getMessage());
            return false;
        }
    }

    public boolean deleteEvent(Long userId, String calendarId, String eventId) {
        Optional<Calendar> calendarOpt = calendarClientFor(userId);
        if (calendarOpt.isEmpty() || eventId == null) return false;
        try {
            calendarOpt.get().events().delete(calendarId != null ? calendarId : "primary", eventId).execute();
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete Google Calendar event {} for user {}: {}", eventId, userId, e.getMessage());
            return false;
        }
    }

    /** Best-effort: create events on both the patient's and doctor's calendars. Never throws. */
    public void syncOnBooking(Appointment appt) {
        try {
            String summary = "Appointment: " + appt.getPatient().getUser().getFullName() +
                    " with Dr. " + appt.getDoctor().getUser().getFullName();
            String description = "Specialisation: " + appt.getDoctor().getSpecialisation();

            createEvent(appt.getPatient().getUser().getId(), "primary", summary, description,
                    appt.getSlotStart(), appt.getSlotEnd())
                    .ifPresent(appt::setPatientCalendarEventId);

            createEvent(appt.getDoctor().getUser().getId(), "primary", summary, description,
                    appt.getSlotStart(), appt.getSlotEnd())
                    .ifPresent(appt::setDoctorCalendarEventId);
        } catch (Exception e) {
            log.warn("Calendar sync on booking failed for appointment {}: {}", appt.getId(), e.getMessage());
        }
    }

    /** Best-effort: delete both calendar events on cancellation. Never throws. */
    public void syncOnCancellation(Appointment appt) {
        try {
            if (appt.getPatientCalendarEventId() != null) {
                deleteEvent(appt.getPatient().getUser().getId(), "primary", appt.getPatientCalendarEventId());
            }
            if (appt.getDoctorCalendarEventId() != null) {
                deleteEvent(appt.getDoctor().getUser().getId(), "primary", appt.getDoctorCalendarEventId());
            }
        } catch (Exception e) {
            log.warn("Calendar sync on cancellation failed for appointment {}: {}", appt.getId(), e.getMessage());
        }
    }

    private EventDateTime toEventDateTime(LocalDateTime dateTime) {
        ZonedDateTime zdt = dateTime.atZone(ZoneId.of(ZONE));
        return new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        zdt.toInstant().toEpochMilli(), zdt.getOffset().getTotalSeconds() / 60))
                .setTimeZone(ZONE);
    }
}
