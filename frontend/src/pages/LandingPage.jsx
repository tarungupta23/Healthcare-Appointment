import { Link } from 'react-router-dom';
import PulseRule from '../components/PulseRule';

export default function LandingPage() {
  return (
    <div>
      <div className="container" style={{ paddingTop: 72, paddingBottom: 48 }}>
        <div style={{ maxWidth: 620 }}>
          <span className="badge badge-neutral" style={{ marginBottom: 16 }}>Healthcare appointments, done properly</span>
          <h1 style={{ fontSize: 44, lineHeight: 1.1 }}>Book faster. Arrive prepared. Never lose the paper trail.</h1>
          <p style={{ fontSize: 17, color: 'var(--color-text-muted)', marginTop: 16 }}>
            Meridian Clinic gives patients a live booking calendar with a pre-visit symptom intake,
            gives doctors an AI-drafted brief before every consultation, and keeps both sides synced
            over email and Google Calendar.
          </p>
          <div style={{ display: 'flex', gap: 12, marginTop: 28 }}>
            <Link to="/doctors" className="btn btn-primary">Find a doctor</Link>
            <Link to="/register" className="btn btn-secondary">Create patient account</Link>
          </div>
        </div>
      </div>

      <div style={{ background: 'var(--color-surface)', borderTop: '1px solid var(--color-border)', borderBottom: '1px solid var(--color-border)' }}>
        <div className="container" style={{ padding: '48px 24px', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 24 }}>
          {[
            { title: 'Live slot holds', body: 'Picking a time reserves it for 5 minutes while you fill in symptoms - no double-booked slots, ever.' },
            { title: 'AI pre-visit brief', body: 'Your doctor sees an urgency level, chief complaint, and suggested questions before you walk in.' },
            { title: 'Plain-language follow-up', body: 'After your visit, clinical notes become a friendly summary with your medication schedule.' },
          ].map((f) => (
            <div key={f.title}>
              <h3>{f.title}</h3>
              <p style={{ color: 'var(--color-text-muted)', fontSize: 14 }}>{f.body}</p>
            </div>
          ))}
        </div>
      </div>
      <PulseRule color="var(--color-border)" />
    </div>
  );
}
