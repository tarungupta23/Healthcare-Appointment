import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyAppointments, cancelAppointment, rescheduleAppointment } from '../../api/patient';
import LoadingSpinner from '../../components/LoadingSpinner';
import StatusBadge from '../../components/StatusBadge';
import UrgencyBadge from '../../components/UrgencyBadge';
import ConnectCalendarButton from '../../components/ConnectCalendarButton';
import RescheduleModal from '../../components/RescheduleModal';

export default function PatientDashboard() {
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedId, setExpandedId] = useState(null);
  const [reschedulingAppt, setReschedulingAppt] = useState(null);

  const load = () => {
    setLoading(true);
    getMyAppointments()
      .then(({ data }) => setAppointments(data))
      .catch(() => setError('Could not load your appointments.'))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleCancel = async (id) => {
    if (!window.confirm('Cancel this appointment?')) return;
    try {
      await cancelAppointment(id, 'Cancelled by patient');
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Could not cancel appointment.');
    }
  };

  const handleReschedule = async (newSlotStart) => {
    await rescheduleAppointment(reschedulingAppt.appointmentId, newSlotStart);
    setReschedulingAppt(null);
    load();
  };

  const upcoming = appointments.filter(a => ['CONFIRMED', 'PENDING_SYMPTOMS'].includes(a.status));
  const past = appointments.filter(a => !['CONFIRMED', 'PENDING_SYMPTOMS'].includes(a.status));

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 12 }}>
        <h1 style={{ marginBottom: 0 }}>My appointments</h1>
        <div style={{ display: 'flex', gap: 10 }}>
          <ConnectCalendarButton />
          <Link to="/doctors" className="btn btn-primary">Book a new appointment</Link>
        </div>
      </div>
      <p style={{ fontSize: 13, color: 'var(--color-text-muted)', marginBottom: 24 }}>
        Connecting Google Calendar adds a synced event for every booking automatically, updated if you reschedule.
      </p>

      {error && <div className="alert alert-error">{error}</div>}
      {loading ? <LoadingSpinner /> : (
        <>
          <h3>Upcoming</h3>
          {upcoming.length === 0 && <p style={{ color: 'var(--color-text-muted)' }}>No upcoming appointments.</p>}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 32 }}>
            {upcoming.map((appt) => (
              <div key={appt.appointmentId} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <h3 style={{ marginBottom: 4 }}>Dr. {appt.doctorName}</h3>
                    <span className="badge badge-neutral">{appt.specialisation}</span>
                    <p style={{ margin: '8px 0 0', color: 'var(--color-text-muted)' }}>
                      {new Date(appt.slotStart).toLocaleString(undefined, { dateStyle: 'full', timeStyle: 'short' })}
                    </p>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end' }}>
                    <StatusBadge status={appt.status} />
                    {appt.preVisitUrgency && <UrgencyBadge urgency={appt.preVisitUrgency} />}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 10, marginTop: 16, flexWrap: 'wrap' }}>
                  <button className="btn btn-secondary" onClick={() => setExpandedId(expandedId === appt.appointmentId ? null : appt.appointmentId)}>
                    {expandedId === appt.appointmentId ? 'Hide details' : 'View symptom notes'}
                  </button>
                  <button className="btn btn-secondary" onClick={() => setReschedulingAppt(appt)}>Reschedule</button>
                  <button className="btn btn-danger" onClick={() => handleCancel(appt.appointmentId)}>Cancel</button>
                </div>
                {expandedId === appt.appointmentId && (
                  <div style={{ marginTop: 12, padding: 12, background: 'var(--color-surface-alt)', borderRadius: 8, fontSize: 14 }}>
                    <strong>Your symptoms:</strong> {appt.symptomsText}
                  </div>
                )}
              </div>
            ))}
          </div>

          <h3>Past & cancelled</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {past.length === 0 && <p style={{ color: 'var(--color-text-muted)' }}>Nothing here yet.</p>}
            {past.map((appt) => (
              <div key={appt.appointmentId} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <h3 style={{ marginBottom: 4 }}>Dr. {appt.doctorName}</h3>
                    <p style={{ margin: '4px 0 0', color: 'var(--color-text-muted)', fontSize: 14 }}>
                      {new Date(appt.slotStart).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                    </p>
                  </div>
                  <StatusBadge status={appt.status} />
                </div>
                {appt.status === 'COMPLETED' && appt.postVisitSummaryText && (
                  <div style={{ marginTop: 12, padding: 12, background: 'var(--color-primary-light)', borderRadius: 8, fontSize: 14, whiteSpace: 'pre-wrap' }}>
                    <strong>Visit summary:</strong>
                    <div style={{ marginTop: 6 }}>{appt.postVisitSummaryText}</div>
                  </div>
                )}
                {appt.cancellationReason && (
                  <p style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 8 }}>
                    Reason: {appt.cancellationReason}
                  </p>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {reschedulingAppt && (
        <RescheduleModal
          doctorId={reschedulingAppt.doctorId}
          currentSlotStart={reschedulingAppt.slotStart}
          onConfirm={handleReschedule}
          onClose={() => setReschedulingAppt(null)}
        />
      )}
    </div>
  );
}
