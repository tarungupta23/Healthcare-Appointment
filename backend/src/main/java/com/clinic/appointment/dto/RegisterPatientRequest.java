package com.clinic.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterPatientRequest {
    @Email @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String fullName;

    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContact;
}
