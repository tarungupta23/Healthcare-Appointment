package com.clinic.appointment.repository;

import com.clinic.appointment.entity.GoogleCalendarToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleCalendarTokenRepository extends JpaRepository<GoogleCalendarToken, Long> {
    Optional<GoogleCalendarToken> findByUserId(Long userId);
}
