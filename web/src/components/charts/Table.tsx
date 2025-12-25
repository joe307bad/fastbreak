'use client';

import { TableData } from '@/types/chart';

interface Props {
  data: TableData;
}

export function Table({ data }: Props) {
  if (!data.dataPoints || data.dataPoints.length === 0) {
    return <div>No data available</div>;
  }

  const columns = data.dataPoints[0].columns;

  return (
    <div className="w-full h-full overflow-auto">
      <table className="w-full text-xs">
        <thead className="sticky top-0 bg-[var(--background)]">
          <tr>
            <th className="px-3 py-2 text-left text-[var(--muted)] font-bold uppercase tracking-wider">
              Team
            </th>
            {columns.map((col, i) => (
              <th
                key={i}
                className="px-3 py-2 text-left text-[var(--muted)] font-bold uppercase tracking-wider"
              >
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.dataPoints.map((row, i) => (
            <tr key={i}>
              <td className="px-3 py-2 font-bold">{row.label}</td>
              {row.columns.map((col, j) => (
                <td key={j} className="px-3 py-2 text-[var(--foreground)]">
                  {col.value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
