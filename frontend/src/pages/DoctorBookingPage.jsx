import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getDoctor, getAvailability } from '../api/doctors';
import { holdSlot, confirmBooking } from '../api/patient';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export default function DoctorBookingPage() {
  const { doctorId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [doctor, setDoctor] = useState(null);
  const [date, setDate] = useState(todayIso());
  const [slots, setSlots] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [error, setError] = useState('');

  // Booking flow state
  const [hold, setHold] = useState(null); // { holdId, slotStart, slotEnd, expiresAt }
  const [symptoms, setSymptoms] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [confirmed, setConfirmed] = useState(null);
  const [countdown, setCountdown] = useState(null);

  useEffect(() => {
    getDoctor(doctorId).then(({ data }) => setDoctor(data));
  }, [doctorId]);

  useEffect(() => {
    if (!date) return;
    setLoadingSlots(true);
    setError('');
    getAvailability(doctorId, date)
      .then(({ data }) => setSlots(data))
      .catch(() => setSlots([]))
      .finally(() => setLoadingSlots(false));
  }, [doctorId, date]);

  useEffect(() => {
    if (!hold) return;
    const tick = () => {
      const secondsLeft = Math.max(0, Math.floor((new Date(hold.expiresAt) - new Date()) / 1000));
      setCountdown(secondsLeft);
      if (secondsLeft === 0) {
        setError('Your hold on this slot expired. Please select a slot again.');
        setHold(null);
      }
    };
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [hold]);

  const handlePickSlot = async (slot) => {
    if (!user) {
      navigate('/login');
      return;
    }
    if (user.role !== 'PATIENT') {
      setError('Only patient accounts can book appointments.');
      return;
    }
    setError('');
    try {
      const { data } = await holdSlot(doctorId, slot.slotStart);
      setHold(data);
    } catch (err) {
      setError(err.response?.data?.message || 'This slot is no longer available.');
      // refresh availability since the slot was likely just taken
      getAvailability(doctorId, date).then(({ data }) => setSlots(data));
    }
  };

  const handleConfirm = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const { data } = await confirmBooking(hold.holdId, symptoms);
      setConfirmed(data);
      setHold(null);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not confirm booking.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!doctor) return <div className="container"><LoadingSpinner /></div>;

  if (confirmed) {
    return (
      <div className="container" style={{ maxWidth: 560, paddingTop: 48 }}>
        <div className="card">
          <div className="alert alert-success">Appointment confirmed!</div>
          <h2>You're booked with Dr. {confirmed.doctorName}</h2>
          <p style={{ color: 'var(--color-text-muted)' }}>
            {new Date(confirmed.slotStart).toLocaleString(undefined, { dateStyle: 'full', timeStyle: 'short' })}
          </p>
          <p style={{ fontSize: 14 }}>
            A confirmation email and calendar invite are on their way. Your doctor will review your symptoms
            before the visit.
          </p>
          <button className="btn btn-primary" onClick={() => navigate('/patient')}>Go to my appointments</button>
        </div>
      </div>
    );
  }

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
      <div className="card" style={{ marginBottom: 24 }}>
        <h1 style={{ marginBottom: 4 }}>Dr. {doctor.fullName}</h1>
        <span className="badge badge-neutral">{doctor.specialisation}</span>
        <p style={{ color: 'var(--color-text-muted)', marginTop: 12 }}>{doctor.bio}</p>
        <p style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
          {doctor.qualification} · {doctor.slotDurationMinutes} min consultations
          {doctor.consultationFee != null ? ` · ₹${doctor.consultationFee}` : ''}
        </p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {!hold && (
        <div className="card">
          <h3>Select a date</h3>
          <input type="date" value={date} min={todayIso()} onChange={(e) => setDate(e.target.value)} style={{ maxWidth: 220, marginBottom: 20 }} />

          {loadingSlots ? <LoadingSpinner label="Loading slots..." /> : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(100px, 1fr))', gap: 10 }}>
              {slots.length === 0 && <p style={{ color: 'var(--color-text-muted)' }}>No slots available on this date.</p>}
              {slots.map((slot) => (
                <button
                  key={slot.slotStart}
                  className="btn"
                  disabled={!slot.available}
                  onClick={() => handlePickSlot(slot)}
                  style={{
                    background: slot.available ? 'var(--color-primary-light)' : 'var(--color-surface-alt)',
                    color: slot.available ? 'var(--color-primary-dark)' : 'var(--color-text-muted)',
                    border: '1px solid ' + (slot.available ? '#C7E3E0' : 'var(--color-border)'),
                    cursor: slot.available ? 'pointer' : 'not-allowed',
                  }}
                >
                  {new Date(slot.slotStart).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {hold && (
        <div className="card">
          <div className="alert alert-info">
            Slot held for {countdown != null ? `${Math.floor(countdown / 60)}:${String(countdown % 60).padStart(2, '0')}` : '...'} -
            complete the form below to confirm your booking.
          </div>
          <h3>Tell us your symptoms</h3>
          <p style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
            This helps your doctor prepare before the visit. An AI-generated summary with urgency level will be
            shared with your doctor - your raw notes stay in your medical record either way.
          </p>
          <form onSubmit={handleConfirm}>
            <div className="field">
              <textarea
                rows={5}
                required
                placeholder="e.g. Persistent headache for 3 days, mild fever, sensitivity to light..."
                value={symptoms}
                onChange={(e) => setSymptoms(e.target.value)}
              />
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <button type="button" className="btn btn-secondary" onClick={() => setHold(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Confirming...' : 'Confirm appointment'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
