import { useEffect, useState } from 'react';
import { getAvailability } from '../api/doctors';
import LoadingSpinner from './LoadingSpinner';

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Lightweight modal: pick a new date/slot for an existing appointment, then
 * call the provided onConfirm(newSlotStartIso). Reuses the same
 * /doctors/{id}/availability endpoint the original booking flow uses, so a
 * slot already taken (or now on a leave day) simply won't show as available.
 */
export default function RescheduleModal({ doctorId, currentSlotStart, onConfirm, onClose }) {
  const [date, setDate] = useState(currentSlotStart ? currentSlotStart.slice(0, 10) : todayIso());
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError('');
    getAvailability(doctorId, date)
      .then(({ data }) => setSlots(data))
      .catch(() => setSlots([]))
      .finally(() => setLoading(false));
  }, [doctorId, date]);

  const handleConfirm = async () => {
    if (!selected) return;
    setSubmitting(true);
    setError('');
    try {
      await onConfirm(selected.slotStart);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not reschedule. Please pick another slot.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(20, 30, 30, 0.45)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 20,
    }}>
      <div className="card" style={{ maxWidth: 480, width: '100%', maxHeight: '85vh', overflowY: 'auto' }}>
        <h3>Reschedule appointment</h3>
        <p style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
          Pick a new date and time. Your symptom notes and doctor stay the same.
        </p>
        {error && <div className="alert alert-error">{error}</div>}

        <input type="date" value={date} min={todayIso()} onChange={(e) => { setDate(e.target.value); setSelected(null); }} style={{ marginBottom: 16 }} />

        {loading ? <LoadingSpinner label="Loading slots..." /> : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(90px, 1fr))', gap: 8, marginBottom: 20 }}>
            {slots.length === 0 && <p style={{ color: 'var(--color-text-muted)', gridColumn: '1 / -1' }}>No slots available on this date.</p>}
            {slots.map((slot) => {
              const isSelected = selected?.slotStart === slot.slotStart;
              return (
                <button
                  key={slot.slotStart}
                  className="btn"
                  disabled={!slot.available}
                  onClick={() => setSelected(slot)}
                  style={{
                    background: isSelected ? 'var(--color-primary)' : slot.available ? 'var(--color-primary-light)' : 'var(--color-surface-alt)',
                    color: isSelected ? 'white' : slot.available ? 'var(--color-primary-dark)' : 'var(--color-text-muted)',
                    border: '1px solid ' + (slot.available ? '#C7E3E0' : 'var(--color-border)'),
                    cursor: slot.available ? 'pointer' : 'not-allowed',
                  }}
                >
                  {new Date(slot.slotStart).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}
                </button>
              );
            })}
          </div>
        )}

        <div style={{ display: 'flex', gap: 12 }}>
          <button className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={!selected || submitting} onClick={handleConfirm}>
            {submitting ? 'Rescheduling...' : 'Confirm new time'}
          </button>
        </div>
      </div>
    </div>
  );
}
