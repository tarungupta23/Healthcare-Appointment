import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listDoctors } from '../../api/admin';
import LoadingSpinner from '../../components/LoadingSpinner';

export default function AdminDashboard() {
  const [doctors, setDoctors] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listDoctors().then(({ data }) => setDoctors(data)).finally(() => setLoading(false));
  }, []);

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1>Doctor management</h1>
        <Link to="/admin/doctors/new" className="btn btn-primary">+ Add doctor</Link>
      </div>

      {loading ? <LoadingSpinner /> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
          {doctors.map((doc) => (
            <div key={doc.doctorId} className="card">
              <h3 style={{ marginBottom: 4 }}>Dr. {doc.fullName}</h3>
              <span className="badge badge-neutral">{doc.specialisation}</span>
              <p style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 10 }}>
                Slot length: {doc.slotDurationMinutes} min
              </p>
              <p style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
                {doc.workingHours?.length || 0} working-hour block(s) configured
              </p>
              <Link to={`/admin/doctors/${doc.doctorId}/leave`} className="btn btn-secondary" style={{ marginTop: 8 }}>
                Manage leave
              </Link>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
