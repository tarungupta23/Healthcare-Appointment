import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { searchDoctors } from '../api/doctors';
import LoadingSpinner from '../components/LoadingSpinner';

export default function DoctorSearchPage() {
  const [specialisation, setSpecialisation] = useState('');
  const [doctors, setDoctors] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = async (spec) => {
    setLoading(true);
    try {
      const { data } = await searchDoctors(spec);
      setDoctors(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(''); }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    load(specialisation);
  };

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
      <h1>Find a doctor</h1>
      <p style={{ color: 'var(--color-text-muted)', maxWidth: 560 }}>
        Search by specialisation and book directly into a confirmed slot. Every booking includes a
        pre-visit symptom form so your doctor is prepared before you walk in.
      </p>

      <form onSubmit={handleSearch} style={{ display: 'flex', gap: 12, margin: '24px 0', maxWidth: 480 }}>
        <input
          placeholder="e.g. Cardiology, Dermatology..."
          value={specialisation}
          onChange={(e) => setSpecialisation(e.target.value)}
        />
        <button className="btn btn-primary" type="submit">Search</button>
      </form>

      {loading ? <LoadingSpinner label="Finding doctors..." /> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
          {doctors.length === 0 && <p style={{ color: 'var(--color-text-muted)' }}>No doctors found.</p>}
          {doctors.map((doc) => (
            <Link to={`/doctors/${doc.doctorId}`} key={doc.doctorId} className="card" style={{ textDecoration: 'none', color: 'inherit' }}>
              <h3 style={{ marginBottom: 4 }}>Dr. {doc.fullName}</h3>
              <span className="badge badge-neutral">{doc.specialisation}</span>
              <p style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 10 }}>
                {doc.qualification} {doc.yearsExperience ? `· ${doc.yearsExperience} yrs experience` : ''}
              </p>
              {doc.consultationFee != null && (
                <p style={{ fontSize: 13, fontWeight: 700, color: 'var(--color-primary-dark)' }}>
                  ₹{doc.consultationFee} consultation
                </p>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
