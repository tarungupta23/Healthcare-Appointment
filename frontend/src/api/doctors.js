import client from './client';

export const searchDoctors = (specialisation) =>
  client.get('/doctors/search', { params: specialisation ? { specialisation } : {} });

export const getDoctor = (doctorId) => client.get(`/doctors/${doctorId}`);

export const getAvailability = (doctorId, date) =>
  client.get(`/doctors/${doctorId}/availability`, { params: { date } });
