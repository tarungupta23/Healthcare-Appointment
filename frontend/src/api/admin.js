import client from './client';

export const createDoctor = (data) => client.post('/admin/doctors', data);

export const listDoctors = () => client.get('/admin/doctors');

export const updateWorkingHours = (doctorId, hours) =>
  client.put(`/admin/doctors/${doctorId}/working-hours`, hours);

export const markDoctorLeave = (doctorId, data) =>
  client.post(`/admin/doctors/${doctorId}/leave`, data);
