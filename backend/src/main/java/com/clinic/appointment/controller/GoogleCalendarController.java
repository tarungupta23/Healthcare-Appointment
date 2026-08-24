package com.clinic.appointment.controller;

import com.clinic.appointment.entity.User;
import com.clinic.appointment.repository.UserRepository;
import com.clinic.appointment.security.AppUserDetails;
import com.clinic.appointment.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth2 connect flow for a user's personal Google Calendar. The frontend
 * redirects the browser to /api/auth/google/authorize, Google redirects back
 * to /api/auth/google/callback with a `code`, which we exchange for tokens.
 */
@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
public class GoogleCalendarController {

    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> authorize(@AuthenticationPrincipal AppUserDetails principal) {
        String url = googleCalendarService.getAuthorizationUrl(principal.getId());
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(@RequestParam String code, @RequestParam String state) {
        Long userId = Long.parseLong(state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.clinic.appointment.exception.ResourceNotFoundException("User not found"));
        googleCalendarService.handleOAuthCallback(code, userId, user);
        return ResponseEntity.ok(Map.of("status", "connected"));
    }
}
