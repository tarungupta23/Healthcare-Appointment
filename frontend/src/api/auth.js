import client from './client';

export const registerPatient = (data) => client.post('/auth/register/patient', data);
export const login = (data) => client.post('/auth/login', data);
