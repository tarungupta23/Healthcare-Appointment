import client from './client';

export const getGoogleAuthorizeUrl = () => client.get('/auth/google/authorize');
