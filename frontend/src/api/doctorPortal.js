import client from './client';

export const getDoctorAppointments = () => client.get('/doctor/appointments');

export const getAppointment = (appointmentId) => client.get(`/doctor/appointments/${appointmentId}`);

export const submitPostVisit = (appointmentId, data) =>
  client.post(`/doctor/appointments/${appointmentId}/post-visit`, data);

export const cancelAppointmentAsDoctor = (appointmentId, reason) =>
  client.post(`/doctor/appointments/${appointmentId}/cancel`, { reason });

export const rescheduleAppointmentAsDoctor = (appointmentId, newSlotStart) =>
  client.post(`/doctor/appointments/${appointmentId}/reschedule`, { newSlotStart });
