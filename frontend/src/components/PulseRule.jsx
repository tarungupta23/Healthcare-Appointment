// Signature decorative element: a thin animated ECG "pulse" line used
// beneath headers across the app - ties every screen back to the clinical
// subject matter without resorting to a literal cross/stethoscope icon.
export default function PulseRule({ color = 'var(--color-primary)' }) {
  return (
    <div className="pulse-rule" aria-hidden="true">
      <svg width="100%" height="10" viewBox="0 0 400 10" preserveAspectRatio="none">
        <polyline
          points="0,5 140,5 152,1 160,9 168,1 176,9 184,5 400,5"
          fill="none"
          stroke={color}
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
}
