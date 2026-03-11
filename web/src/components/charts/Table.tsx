'use client';

import { TableData, TableColumn } from '@/types/chart';

interface Props {
  data: TableData;
}

function formatOrdinal(n: number): string {
  const s = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
}

function getConferenceAbbrev(conference: string): string {
  const lower = conference.toLowerCase();
  if (lower.includes('east')) return 'East';
  if (lower.includes('west')) return 'West';
  if (lower.includes('afc')) return 'AFC';
  if (lower.includes('nfc')) return 'NFC';
  return conference;
}

function processColumns(columns: TableColumn[]): { displayColumns: TableColumn[]; skipIndices: Set<number> } {
  const skipIndices = new Set<number>();
  const displayColumns: TableColumn[] = [];

  const confRankIdx = columns.findIndex(c => c.label.toLowerCase().includes('conferencerank'));
  const confIdx = columns.findIndex(c => c.label.toLowerCase() === 'conference');

  for (let i = 0; i < columns.length; i++) {
    if (confRankIdx !== -1 && confIdx !== -1) {
      if (i === confRankIdx) {
        const rank = Number(columns[confRankIdx].value);
        const conf = String(columns[confIdx].value);
        displayColumns.push({
          label: 'Conf',
          value: `${formatOrdinal(rank)} / ${getConferenceAbbrev(conf)}`,
        });
        skipIndices.add(confIdx);
        continue;
      }
      if (i === confIdx) {
        continue;
      }
    }
    displayColumns.push(columns[i]);
  }

  return { displayColumns, skipIndices };
}

export function Table({ data }: Props) {
  if (!data.dataPoints || data.dataPoints.length === 0) {
    return <div>No data available</div>;
  }

  const firstRow = data.dataPoints[0].columns;
  const { displayColumns: headerColumns } = processColumns(firstRow);

  return (
    <div className="w-full h-full overflow-auto">
      <table className="w-full text-xs">
        <thead className="sticky top-0 bg-[var(--background)]">
          <tr>
            <th className="px-3 py-2 text-left text-[var(--muted)] font-bold uppercase tracking-wider">
              Team
            </th>
            {headerColumns.map((col, i) => (
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
          {data.dataPoints.map((row, i) => {
            const { displayColumns } = processColumns(row.columns);
            return (
              <tr key={i}>
                <td className="px-3 py-2 font-bold">{row.label}</td>
                {displayColumns.map((col, j) => (
                  <td key={j} className="px-3 py-2 text-[var(--foreground)]">
                    {col.value}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
