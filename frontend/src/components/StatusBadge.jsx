const MAP = {
  CONFIRMED: 'badge-confirmed',
  PENDING_SYMPTOMS: 'badge-neutral',
  CANCELLED: 'badge-cancelled',
  CANCELLED_BY_LEAVE: 'badge-cancelled',
  COMPLETED: 'badge-completed',
  NO_SHOW: 'badge-neutral',
};

export default function StatusBadge({ status }) {
  const cls = MAP[status] || 'badge-neutral';
  return <span className={`badge ${cls}`}>{status?.replaceAll('_', ' ')}</span>;
}
