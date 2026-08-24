export default function LoadingSpinner({ label = 'Loading...' }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'var(--color-text-muted)', padding: 24 }}>
      <div style={{
        width: 16, height: 16, borderRadius: '50%',
        border: '2px solid var(--color-border)', borderTopColor: 'var(--color-primary)',
        animation: 'spin 0.7s linear infinite'
      }} />
      <span>{label}</span>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
