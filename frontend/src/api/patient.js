import client from './client';

export const holdSlot = (doctorId, slotStart) =>
  client.post('/patient/slots/hold', { doctorId, slotStart });

export const confirmBooking = (holdId, symptomsText) =>
  client.post('/patient/appointments/confirm', { holdId, symptomsText });

export const getMyAppointments = () => client.get('/patient/appointments');

export const cancelAppointment = (appointmentId, reason) =>
  client.post(`/patient/appointments/${appointmentId}/cancel`, { reason });

export const rescheduleAppointment = (appointmentId, newSlotStart) =>
  client.post(`/patient/appointments/${appointmentId}/reschedule`, { newSlotStart });
