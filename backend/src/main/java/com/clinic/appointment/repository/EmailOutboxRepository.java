package com.clinic.appointment.repository;

import com.clinic.appointment.entity.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    @Query("select e from EmailOutbox e where e.status = 'PENDING' and e.nextAttemptAt <= :now " +
           "and e.attempts < e.maxAttempts order by e.nextAttemptAt asc")
    List<EmailOutbox> findDueForRetry(@Param("now") LocalDateTime now);
}
