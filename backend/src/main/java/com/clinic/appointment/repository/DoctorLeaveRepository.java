package com.clinic.appointment.repository;

import com.clinic.appointment.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {
    List<DoctorLeave> findByDoctorId(Long doctorId);
    Optional<DoctorLeave> findByDoctorIdAndLeaveDate(Long doctorId, LocalDate date);
    boolean existsByDoctorIdAndLeaveDate(Long doctorId, LocalDate date);
}
