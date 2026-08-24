import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getDoctorAppointments } from '../../api/doctorPortal';
import LoadingSpinner from '../../components/LoadingSpinner';
import StatusBadge from '../../components/StatusBadge';
import UrgencyBadge from '../../components/UrgencyBadge';
import ConnectCalendarButton from '../../components/ConnectCalendarButton';

export default function DoctorDashboard() {
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('UPCOMING');

  useEffect(() => {
    getDoctorAppointments().then(({ data }) => setAppointments(data)).finally(() => setLoading(false));
  }, []);

  const now = new Date();
  const filtered = appointments.filter((a) => {
    if (filter === 'UPCOMING') return ['CONFIRMED', 'PENDING_SYMPTOMS'].includes(a.status) && new Date(a.slotStart) >= now;
    if (filter === 'TODAY') {
      const d = new Date(a.slotStart);
      return d.toDateString() === now.toDateString() && ['CONFIRMED', 'PENDING_SYMPTOMS'].includes(a.status);
    }
    if (filter === 'COMPLETED') return a.status === 'COMPLETED';
    return true;
  });

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <h1 style={{ marginBottom: 0 }}>My schedule</h1>
        <ConnectCalendarButton />
      </div>

      <div style={{ display: 'flex', gap: 10, margin: '20px 0' }}>
        {['TODAY', 'UPCOMING', 'COMPLETED', 'ALL'].map((f) => (
          <button
            key={f}
            className="btn"
            onClick={() => setFilter(f)}
            style={{
              background: filter === f ? 'var(--color-primary)' : 'var(--color-surface)',
              color: filter === f ? 'white' : 'var(--color-navy)',
              border: '1px solid var(--color-border)'
            }}
          >
            {f.charAt(0) + f.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {loading ? <LoadingSpinner /> : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {filtered.length === 0 && <p style={{ color: 'var(--color-text-muted)' }}>No appointments in this view.</p>}
          {filtered.map((appt) => (
            <Link key={appt.appointmentId} to={`/doctor/appointments/${appt.appointmentId}`} className="card" style={{ textDecoration: 'none', color: 'inherit', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ marginBottom: 4 }}>{appt.patientName}</h3>
                <p style={{ margin: 0, color: 'var(--color-text-muted)', fontSize: 14 }}>
                  {new Date(appt.slotStart).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                </p>
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {appt.preVisitUrgency && <UrgencyBadge urgency={appt.preVisitUrgency} />}
                <StatusBadge status={appt.status} />
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
