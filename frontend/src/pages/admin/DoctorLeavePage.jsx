import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { markDoctorLeave } from '../../api/admin';

export default function DoctorLeavePage() {
  const { doctorId } = useParams();
  const navigate = useNavigate();
  const [leaveDate, setLeaveDate] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cancelled, setCancelled] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const { data } = await markDoctorLeave(doctorId, { leaveDate, reason });
      setCancelled(data);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not mark leave.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container" style={{ maxWidth: 560, paddingTop: 40, paddingBottom: 60 }}>
      <button className="btn btn-secondary" onClick={() => navigate('/admin')} style={{ marginBottom: 20 }}>&larr; Back</button>
      <div className="card">
        <h2>Mark doctor on leave</h2>
        <p style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
          Any confirmed appointments already booked on this date will be automatically cancelled,
          and both the patient and doctor will be notified by email.
        </p>
        {error && <div className="alert alert-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="field"><label>Leave date</label><input type="date" value={leaveDate} onChange={(e) => setLeaveDate(e.target.value)} required /></div>
          <div className="field"><label>Reason (optional)</label><input value={reason} onChange={(e) => setReason(e.target.value)} /></div>
          <button className="btn btn-danger btn-block" disabled={loading}>
            {loading ? 'Saving...' : 'Mark as leave'}
          </button>
        </form>

        {cancelled && (
          <div style={{ marginTop: 20 }}>
            <div className="alert alert-info">
              {cancelled.length} appointment(s) were cancelled and patients notified.
            </div>
            {cancelled.map((a) => (
              <div key={a.appointmentId} style={{ padding: 10, borderBottom: '1px solid var(--color-border)', fontSize: 14 }}>
                {a.patientName} - {new Date(a.slotStart).toLocaleString()}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
