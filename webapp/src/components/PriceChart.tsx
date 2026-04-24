import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface PriceChartProps {
  data: Array<{ label: string; value: number }>;
  title?: string;
  color?: string;
}

export function PriceChart({ data, title, color = '#6366f1' }: PriceChartProps) {
  if (!data.length) return <p className="text-gray-500 text-sm py-4">No data available</p>;

  return (
    <div className="rounded-xl bg-white p-6 shadow-sm border border-gray-100">
      {title && <h3 className="text-lg font-semibold text-gray-900 mb-4">{title}</h3>}
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} tickFormatter={(v: number) => `${(v / 1000).toFixed(0)}k`} />
          <Tooltip formatter={(v) => [`${Number(v).toLocaleString('fr-FR')} €`, 'Price']} />
          <Bar dataKey="value" fill={color} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
