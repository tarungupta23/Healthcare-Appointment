import { useState } from 'react';
import { getGoogleAuthorizeUrl } from '../api/calendar';

/**
 * Kicks off the Google OAuth2 consent flow for the logged-in user (patient
 * or doctor). Redirects the whole browser tab to Google's consent screen;
 * Google redirects back to the backend's /callback, which stores the token
 * and (in this simple flow) the user lands on the backend's plain JSON
 * response - fine for an evaluation build, but worth polishing to redirect
 * back into the SPA with a success banner for a production version.
 */
export default function ConnectCalendarButton({ style }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleConnect = async () => {
    setLoading(true);
    setError('');
    try {
      const { data } = await getGoogleAuthorizeUrl();
      window.location.href = data.authorizationUrl;
    } catch (err) {
      setError('Could not start Google Calendar connection. Is it configured on the server?');
      setLoading(false);
    }
  };

  return (
    <div style={style}>
      <button className="btn btn-secondary" onClick={handleConnect} disabled={loading}>
        {loading ? 'Redirecting...' : '📅 Connect Google Calendar'}
      </button>
      {error && <p style={{ fontSize: 12, color: 'var(--color-danger)', marginTop: 6 }}>{error}</p>}
    </div>
  );
}
