import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createDoctor } from '../../api/admin';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export default function AddDoctorPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '', email: '', temporaryPassword: '', phone: '',
    specialisation: '', qualification: '', yearsExperience: 5,
    slotDurationMinutes: 20, consultationFee: 500, bio: '',
  });
  const [workingDays, setWorkingDays] = useState({
    MONDAY: { active: true, start: '09:00', end: '17:00' },
    TUESDAY: { active: true, start: '09:00', end: '17:00' },
    WEDNESDAY: { active: true, start: '09:00', end: '17:00' },
    THURSDAY: { active: true, start: '09:00', end: '17:00' },
    FRIDAY: { active: true, start: '09:00', end: '17:00' },
    SATURDAY: { active: false, start: '09:00', end: '13:00' },
    SUNDAY: { active: false, start: '09:00', end: '13:00' },
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const toggleDay = (day) => setWorkingDays({ ...workingDays, [day]: { ...workingDays[day], active: !workingDays[day].active } });
  const updateDayTime = (day, field, value) => setWorkingDays({ ...workingDays, [day]: { ...workingDays[day], [field]: value } });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const workingHours = Object.entries(workingDays)
        .filter(([, v]) => v.active)
        .map(([day, v]) => ({ dayOfWeek: day, startTime: v.start + ':00', endTime: v.end + ':00' }));

      await createDoctor({ ...form, workingHours });
      navigate('/admin');
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create doctor.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container" style={{ maxWidth: 640, paddingTop: 40, paddingBottom: 60 }}>
      <div className="card">
        <h2>Add a doctor</h2>
        {error && <div className="alert alert-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="field"><label>Full name</label><input value={form.fullName} onChange={update('fullName')} required /></div>
            <div className="field"><label>Email</label><input type="email" value={form.email} onChange={update('email')} required /></div>
            <div className="field"><label>Temporary password</label><input value={form.temporaryPassword} onChange={update('temporaryPassword')} required /></div>
            <div className="field"><label>Phone</label><input value={form.phone} onChange={update('phone')} /></div>
            <div className="field"><label>Specialisation</label><input value={form.specialisation} onChange={update('specialisation')} required /></div>
            <div className="field"><label>Qualification</label><input value={form.qualification} onChange={update('qualification')} /></div>
            <div className="field"><label>Years experience</label><input type="number" value={form.yearsExperience} onChange={update('yearsExperience')} /></div>
            <div className="field"><label>Slot duration (min)</label><input type="number" value={form.slotDurationMinutes} onChange={update('slotDurationMinutes')} required /></div>
            <div className="field"><label>Consultation fee</label><input type="number" value={form.consultationFee} onChange={update('consultationFee')} /></div>
          </div>
          <div className="field"><label>Bio</label><textarea rows={2} value={form.bio} onChange={update('bio')} /></div>

          <label>Working hours</label>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
            {DAYS.map((day) => (
              <div key={day} style={{ display: 'grid', gridTemplateColumns: '30px 100px 1fr 1fr', gap: 8, alignItems: 'center' }}>
                <input type="checkbox" checked={workingDays[day].active} onChange={() => toggleDay(day)} />
                <span style={{ fontSize: 13 }}>{day.slice(0, 3)}</span>
                <input type="time" value={workingDays[day].start} disabled={!workingDays[day].active} onChange={(e) => updateDayTime(day, 'start', e.target.value)} />
                <input type="time" value={workingDays[day].end} disabled={!workingDays[day].active} onChange={(e) => updateDayTime(day, 'end', e.target.value)} />
              </div>
            ))}
          </div>

          <button className="btn btn-primary btn-block" disabled={loading}>
            {loading ? 'Creating...' : 'Create doctor account'}
          </button>
        </form>
      </div>
    </div>
  );
}
