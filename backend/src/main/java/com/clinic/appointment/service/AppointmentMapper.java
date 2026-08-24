package com.clinic.appointment.service;

import com.clinic.appointment.dto.AppointmentResponse;
import com.clinic.appointment.entity.Appointment;

public final class AppointmentMapper {

    private AppointmentMapper() {}

    public static AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .appointmentId(a.getId())
                .doctorId(a.getDoctor().getId())
                .doctorName(a.getDoctor().getUser().getFullName())
                .specialisation(a.getDoctor().getSpecialisation())
                .patientId(a.getPatient().getId())
                .patientName(a.getPatient().getUser().getFullName())
                .slotStart(a.getSlotStart())
                .slotEnd(a.getSlotEnd())
                .status(a.getStatus().name())
                .symptomsText(a.getSymptomsText())
                .preVisitSummaryJson(a.getPreVisitSummaryJson())
                .preVisitUrgency(a.getPreVisitUrgency() != null ? a.getPreVisitUrgency().name() : null)
                .preVisitLlmStatus(a.getPreVisitLlmStatus() != null ? a.getPreVisitLlmStatus().name() : null)
                .doctorNotes(a.getDoctorNotes())
                .prescriptionJson(a.getPrescriptionJson())
                .postVisitSummaryText(a.getPostVisitSummaryText())
                .postVisitLlmStatus(a.getPostVisitLlmStatus() != null ? a.getPostVisitLlmStatus().name() : null)
                .cancellationReason(a.getCancellationReason())
                .build();
    }
}
