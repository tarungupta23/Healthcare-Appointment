package com.clinic.appointment.exception;

/** Thrown whenever a requested slot is already held, booked, on a leave day,
 *  or outside the doctor's working hours. */
public class SlotUnavailableException extends RuntimeException {
    public SlotUnavailableException(String message) {
        super(message);
    }
}
