export default function UrgencyBadge({ urgency }) {
  if (!urgency) return <span className="badge badge-neutral">Pending</span>;
  const cls = urgency.toLowerCase() === 'high' ? 'badge-high' : urgency.toLowerCase() === 'medium' ? 'badge-medium' : 'badge-low';
  return <span className={`badge ${cls}`}>{urgency}</span>;
}
