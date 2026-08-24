package com.clinic.appointment.repository;

import com.clinic.appointment.entity.DoctorWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface DoctorWorkingHoursRepository extends JpaRepository<DoctorWorkingHours, Long> {
    List<DoctorWorkingHours> findByDoctorId(Long doctorId);
    Optional<DoctorWorkingHours> findByDoctorIdAndDayOfWeek(Long doctorId, DayOfWeek dayOfWeek);
}
