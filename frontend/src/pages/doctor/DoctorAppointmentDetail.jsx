import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getAppointment, submitPostVisit, cancelAppointmentAsDoctor, rescheduleAppointmentAsDoctor } from '../../api/doctorPortal';
import LoadingSpinner from '../../components/LoadingSpinner';
import StatusBadge from '../../components/StatusBadge';
import UrgencyBadge from '../../components/UrgencyBadge';
import RescheduleModal from '../../components/RescheduleModal';

const FREQUENCIES = [
  { value: 'ONCE_DAILY', label: 'Once daily' },
  { value: 'TWICE_DAILY', label: 'Twice daily' },
  { value: 'THRICE_DAILY', label: 'Three times daily' },
  { value: 'EVERY_6_HOURS', label: 'Every 6 hours' },
  { value: 'EVERY_8_HOURS', label: 'Every 8 hours' },
];

export default function DoctorAppointmentDetail() {
  const { appointmentId } = useParams();
  const navigate = useNavigate();
  const [appt, setAppt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [rescheduling, setRescheduling] = useState(false);

  const [notes, setNotes] = useState('');
  const [followUp, setFollowUp] = useState('');
  const [items, setItems] = useState([{ medicationName: '', dosage: '', frequency: 'ONCE_DAILY', durationDays: 5, instructions: '' }]);

  const load = () => {
    setLoading(true);
    getAppointment(appointmentId).then(({ data }) => setAppt(data)).finally(() => setLoading(false));
  };

  useEffect(load, [appointmentId]);

  const preVisit = appt?.preVisitSummaryJson ? JSON.parse(appt.preVisitSummaryJson) : null;

  const updateItem = (idx, field, value) => {
    const next = [...items];
    next[idx] = { ...next[idx], [field]: value };
    setItems(next);
  };

  const addItem = () => setItems([...items, { medicationName: '', dosage: '', frequency: 'ONCE_DAILY', durationDays: 5, instructions: '' }]);
  const removeItem = (idx) => setItems(items.filter((_, i) => i !== idx));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const { data } = await submitPostVisit(appointmentId, {
        doctorNotes: notes,
        followUpInstructions: followUp,
        prescriptionItems: items.filter(i => i.medicationName.trim() !== ''),
      });
      setAppt(data);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not submit visit notes.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Cancel this appointment?')) return;
    await cancelAppointmentAsDoctor(appointmentId, 'Cancelled by doctor');
    navigate('/doctor');
  };

  const handleReschedule = async (newSlotStart) => {
    await rescheduleAppointmentAsDoctor(appointmentId, newSlotStart);
    setRescheduling(false);
    load();
  };

  if (loading || !appt) return <div className="container"><LoadingSpinner /></div>;

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 60, maxWidth: 760 }}>
      <button className="btn btn-secondary" onClick={() => navigate('/doctor')} style={{ marginBottom: 20 }}>&larr; Back to schedule</button>

      <div className="card" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h2 style={{ marginBottom: 4 }}>{appt.patientName}</h2>
            <p style={{ color: 'var(--color-text-muted)', margin: 0 }}>
              {new Date(appt.slotStart).toLocaleString(undefined, { dateStyle: 'full', timeStyle: 'short' })}
            </p>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexDirection: 'column' }}>
            <StatusBadge status={appt.status} />
            {appt.status !== 'COMPLETED' && (
              <button className="btn btn-secondary" onClick={() => setRescheduling(true)}>Reschedule</button>
            )}
          </div>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <h3>Pre-visit summary</h3>
        {appt.preVisitLlmStatus === 'PENDING' && <p style={{ color: 'var(--color-text-muted)' }}>Generating summary...</p>}
        {appt.preVisitLlmStatus === 'FAILED' && (
          <div className="alert alert-error">
            AI summary unavailable - showing raw patient notes below instead.
          </div>
        )}
        {preVisit ? (
          <>
            <UrgencyBadge urgency={preVisit.urgencyLevel} />
            <p style={{ marginTop: 10 }}><strong>Chief complaint:</strong> {preVisit.chiefComplaint}</p>
            <strong>Suggested questions:</strong>
            <ul>
              {preVisit.suggestedQuestions?.map((q, i) => <li key={i}>{q}</li>)}
            </ul>
          </>
        ) : (
          <p style={{ fontSize: 14 }}>{appt.symptomsText}</p>
        )}
        <details style={{ marginTop: 10, fontSize: 13, color: 'var(--color-text-muted)' }}>
          <summary style={{ cursor: 'pointer' }}>Raw patient-submitted symptoms</summary>
          <p>{appt.symptomsText}</p>
        </details>
      </div>

      {appt.status === 'COMPLETED' ? (
        <div className="card">
          <h3>Visit completed</h3>
          <p><strong>Doctor notes:</strong> {appt.doctorNotes}</p>
          <p style={{ whiteSpace: 'pre-wrap' }}><strong>Patient-facing summary:</strong> {appt.postVisitSummaryText}</p>
        </div>
      ) : (
        <div className="card">
          <h3>Complete this visit</h3>
          {error && <div className="alert alert-error">{error}</div>}
          <form onSubmit={handleSubmit}>
            <div className="field">
              <label>Clinical notes</label>
              <textarea rows={4} required value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Findings, diagnosis, observations..." />
            </div>

            <label>Prescription</label>
            {items.map((item, idx) => (
              <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr 1fr 0.7fr auto', gap: 8, marginBottom: 8, alignItems: 'end' }}>
                <input placeholder="Medication" value={item.medicationName} onChange={(e) => updateItem(idx, 'medicationName', e.target.value)} />
                <input placeholder="Dosage (e.g. 500mg)" value={item.dosage} onChange={(e) => updateItem(idx, 'dosage', e.target.value)} />
                <select value={item.frequency} onChange={(e) => updateItem(idx, 'frequency', e.target.value)}>
                  {FREQUENCIES.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
                </select>
                <input type="number" min={1} placeholder="Days" value={item.durationDays} onChange={(e) => updateItem(idx, 'durationDays', parseInt(e.target.value || '1', 10))} />
                <button type="button" className="btn btn-danger" onClick={() => removeItem(idx)}>&times;</button>
              </div>
            ))}
            <button type="button" className="btn btn-secondary" onClick={addItem} style={{ marginBottom: 16 }}>+ Add medication</button>

            <div className="field">
              <label>Follow-up instructions</label>
              <textarea rows={2} value={followUp} onChange={(e) => setFollowUp(e.target.value)} placeholder="e.g. Return in 2 weeks if symptoms persist" />
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button type="button" className="btn btn-danger" onClick={handleCancel}>Cancel appointment</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Saving...' : 'Complete visit & generate summary'}
              </button>
            </div>
          </form>
        </div>
      )}

      {rescheduling && (
        <RescheduleModal
          doctorId={appt.doctorId}
          currentSlotStart={appt.slotStart}
          onConfirm={handleReschedule}
          onClose={() => setRescheduling(false)}
        />
      )}
    </div>
  );
}
