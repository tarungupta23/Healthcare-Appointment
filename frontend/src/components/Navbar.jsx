import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import PulseRule from './PulseRule';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const homeLink = user
    ? user.role === 'ADMIN' ? '/admin' : user.role === 'DOCTOR' ? '/doctor' : '/patient'
    : '/';

  return (
    <header>
      <div className="container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 24px' }}>
        <Link to={homeLink} style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
          <span style={{
            width: 34, height: 34, borderRadius: 9, background: 'var(--color-primary)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontWeight: 800, fontFamily: 'var(--font-display)'
          }}>+</span>
          <span style={{ fontFamily: 'var(--font-display)', fontWeight: 800, color: 'var(--color-navy)', fontSize: 18 }}>
            Meridian Clinic
          </span>
        </Link>

        <nav style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
          {!user && (
            <>
              <Link to="/doctors" style={{ textDecoration: 'none', color: 'var(--color-text-muted)', fontWeight: 600, fontSize: 14 }}>Find a doctor</Link>
              <Link to="/login" className="btn btn-secondary">Log in</Link>
              <Link to="/register" className="btn btn-primary">Get started</Link>
            </>
          )}
          {user && (
            <>
              <span style={{ fontSize: 14, color: 'var(--color-text-muted)' }}>
                {user.fullName} <span className="badge badge-neutral" style={{ marginLeft: 6 }}>{user.role}</span>
              </span>
              <button className="btn btn-secondary" onClick={handleLogout}>Log out</button>
            </>
          )}
        </nav>
      </div>
      <PulseRule />
    </header>
  );
}
