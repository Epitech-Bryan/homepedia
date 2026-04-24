interface StatCardProps {
  label: string;
  value: string | number;
  unit?: string;
  className?: string;
}

export function StatCard({ label, value, unit, className = '' }: StatCardProps) {
  return (
    <div className={`rounded-xl bg-white p-6 shadow-sm border border-gray-100 ${className}`}>
      <p className="text-sm font-medium text-gray-500">{label}</p>
      <p className="mt-2 text-2xl font-bold text-gray-900">
        {typeof value === 'number' ? value.toLocaleString('fr-FR') : value}
        {unit && <span className="ml-1 text-sm font-normal text-gray-500">{unit}</span>}
      </p>
    </div>
  );
}
