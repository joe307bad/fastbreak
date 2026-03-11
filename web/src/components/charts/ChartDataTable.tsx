'use client';

import { useState, useMemo, useEffect } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  flexRender,
  createColumnHelper,
  SortingState,
} from '@tanstack/react-table';
import { ChartData, ScatterPlotData } from '@/types/chart';

type QuadrantKey = 'topRight' | 'topLeft' | 'bottomLeft' | 'bottomRight';

// Get quadrant for a scatter plot point
function getPointQuadrant(data: ScatterPlotData, label: string): QuadrantKey | null {
  const point = data.dataPoints.find(p => p.label === label);
  if (!point) return null;

  const xValues = data.dataPoints.map(p => p.x);
  const yValues = data.dataPoints.map(p => p.y);
  const xMean = xValues.reduce((sum, val) => sum + val, 0) / xValues.length;
  const yMean = yValues.reduce((sum, val) => sum + val, 0) / yValues.length;

  const isHighX = point.x >= xMean;
  const isHighY = data.invertYAxis ? point.y <= yMean : point.y >= yMean;

  if (isHighX && isHighY) return 'topRight';
  if (!isHighX && isHighY) return 'topLeft';
  if (!isHighX && !isHighY) return 'bottomLeft';
  return 'bottomRight';
}

interface Row {
  label: string;
  [key: string]: string | number | undefined;
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

function formatConferenceRank(rank: number | undefined, conference: string | undefined): string {
  if (rank != null && conference) {
    return `${formatOrdinal(rank)} / ${getConferenceAbbrev(conference)}`;
  }
  if (conference) return getConferenceAbbrev(conference);
  return '';
}

// Parse "1st / East" back to { rank: 1, conf: "East" } for sorting
function parseConfValue(val: string | number | undefined): { conf: string; rank: number } {
  if (typeof val !== 'string') return { conf: '', rank: 999 };
  const match = val.match(/^(\d+)(?:st|nd|rd|th)\s*\/\s*(.+)$/);
  if (match) {
    return { conf: match[2], rank: parseInt(match[1], 10) };
  }
  // Just conference name, no rank
  return { conf: val, rank: 999 };
}

function extractFilterOptions(data: ChartData): { conferences: string[]; divisions: string[] } {
  const conferences = new Set<string>();
  const divisions = new Set<string>();

  if (data.visualizationType === 'SCATTER_PLOT') {
    data.dataPoints.forEach(d => {
      if (d.conference) conferences.add(getConferenceAbbrev(d.conference));
      if (d.division) divisions.add(d.division);
    });
  } else if (data.visualizationType === 'BAR_CHART' || data.visualizationType === 'BAR_GRAPH') {
    data.dataPoints.forEach(d => {
      if (d.conference) conferences.add(getConferenceAbbrev(d.conference));
      if (d.division) divisions.add(d.division);
    });
  } else if (data.visualizationType === 'LINE_CHART') {
    data.series.forEach(s => {
      if (s.conference) conferences.add(getConferenceAbbrev(s.conference));
      if (s.division) divisions.add(s.division);
    });
  }

  return {
    conferences: Array.from(conferences).sort(),
    divisions: Array.from(divisions).sort(),
  };
}

function buildTableData(data: ChartData): { rows: Row[]; columnKeys: string[] } {
  switch (data.visualizationType) {
    case 'SCATTER_PLOT': {
      const hasConfRank = data.dataPoints.some(d => (d as Record<string, unknown>).conferenceRank != null);
      const hasConf = data.dataPoints.some(d => d.conference);
      const columnKeys = [
        'label',
        data.xColumnLabel || data.xAxisLabel,
        data.yColumnLabel || data.yAxisLabel,
        ...(data.dataPoints.some(d => d.sum != null) ? ['sum'] : []),
        ...(data.dataPoints.some(d => d.teamCode) ? ['team'] : []),
        ...(data.dataPoints.some(d => d.division) ? ['division'] : []),
        ...(hasConf ? ['conf'] : []),
      ];
      const rows = data.dataPoints.map(d => {
        const dAny = d as Record<string, unknown>;
        const confRank = dAny.conferenceRank as number | undefined;
        return {
          label: d.label,
          [data.xColumnLabel || data.xAxisLabel]: d.x,
          [data.yColumnLabel || data.yAxisLabel]: d.y,
          ...(d.sum != null ? { sum: d.sum } : {}),
          ...(d.teamCode ? { team: d.teamCode } : {}),
          ...(d.division ? { division: d.division } : {}),
          ...(d.conference ? { conf: hasConfRank ? formatConferenceRank(confRank, d.conference) : getConferenceAbbrev(d.conference) } : {}),
        };
      });
      return { rows, columnKeys };
    }
    case 'LINE_CHART': {
      const xValues = [...new Set(data.series.flatMap(s => s.dataPoints.map(d => String(d.x))))];
      const columnKeys = ['label', ...xValues];
      const rows = data.series.map(s => {
        const row: Row = { label: s.label };
        s.dataPoints.forEach(d => {
          row[String(d.x)] = d.y;
        });
        return row;
      });
      return { rows, columnKeys };
    }
    case 'BAR_CHART':
    case 'BAR_GRAPH': {
      const hasConfRank = data.dataPoints.some(d => (d as Record<string, unknown>).conferenceRank != null);
      const hasConf = data.dataPoints.some(d => d.conference);
      const columnKeys = [
        'label',
        'value',
        ...(data.dataPoints.some(d => d.division) ? ['division'] : []),
        ...(hasConf ? ['conf'] : []),
      ];
      const rows = data.dataPoints.map(d => {
        const dAny = d as Record<string, unknown>;
        const confRank = dAny.conferenceRank as number | undefined;
        return {
          label: d.label,
          value: d.value,
          ...(d.division ? { division: d.division } : {}),
          ...(d.conference ? { conf: hasConfRank ? formatConferenceRank(confRank, d.conference) : getConferenceAbbrev(d.conference) } : {}),
        };
      });
      return { rows, columnKeys };
    }
    case 'TABLE': {
      if (data.dataPoints.length === 0) return { rows: [], columnKeys: [] };
      const colLabels = data.dataPoints[0].columns.map(c => c.label);
      const columnKeys = ['label', ...colLabels];
      const rows = data.dataPoints.map(d => {
        const row: Row = { label: d.label };
        d.columns.forEach(c => {
          row[c.label] = c.value;
        });
        return row;
      });
      return { rows, columnKeys };
    }
    default:
      return { rows: [], columnKeys: [] };
  }
}

interface ChartDataTableProps {
  data: ChartData;
  onHighlight?: (labels: string[]) => void;
  selectedLabel?: string | null;
  getItemColor?: (label: string) => string | null;
  onSelect?: (label: string | null) => void;
  quadrantFilter?: QuadrantKey | null;
}

export function ChartDataTable({ data, onHighlight, selectedLabel, getItemColor, onSelect, quadrantFilter }: ChartDataTableProps) {
  const { rows, columnKeys } = useMemo(() => buildTableData(data), [data]);
  const { conferences, divisions } = useMemo(() => extractFilterOptions(data), [data]);

  // Determine default sort column: prefer 'sum', then 'value'
  const defaultSortColumn = useMemo(() => {
    if (columnKeys.includes('sum')) return 'sum';
    if (columnKeys.includes('value')) return 'value';
    return null;
  }, [columnKeys]);

  const [sorting, setSorting] = useState<SortingState>(() => {
    if (defaultSortColumn) {
      return [{ id: defaultSortColumn, desc: true }];
    }
    return [];
  });
  const [globalFilter, setGlobalFilter] = useState('');
  const [conferenceFilter, setConferenceFilter] = useState<string>('');
  const [divisionFilter, setDivisionFilter] = useState<string>('');

  const columnHelper = createColumnHelper<Row>();

  const columns = useMemo(
    () =>
      columnKeys.map(key =>
        columnHelper.accessor(row => row[key], {
          id: key,
          header: key.charAt(0).toUpperCase() + key.slice(1),
          cell: info => {
            const val = info.getValue();
            return typeof val === 'number' ? val.toLocaleString(undefined, { maximumFractionDigits: 2 }) : val ?? '';
          },
          sortingFn: (rowA, rowB, columnId) => {
            const a = rowA.getValue(columnId);
            const b = rowB.getValue(columnId);
            // Special sorting for conf column: sort by conference first, then by rank
            if (columnId === 'conf') {
              const parsedA = parseConfValue(a as string | number | undefined);
              const parsedB = parseConfValue(b as string | number | undefined);
              const confCompare = parsedA.conf.localeCompare(parsedB.conf);
              if (confCompare !== 0) return confCompare;
              return parsedA.rank - parsedB.rank;
            }
            if (typeof a === 'number' && typeof b === 'number') return a - b;
            return String(a ?? '').localeCompare(String(b ?? ''));
          },
        })
      ),
    [columnKeys, columnHelper]
  );

  // Filter rows based on conference and division
  const filteredRows = useMemo(() => {
    return rows.filter(row => {
      if (conferenceFilter) {
        const confVal = row.conf as string | undefined;
        if (!confVal) return false;
        const parsed = parseConfValue(confVal);
        if (parsed.conf !== conferenceFilter && confVal !== conferenceFilter) return false;
      }
      if (divisionFilter) {
        if (row.division !== divisionFilter) return false;
      }
      // Filter by quadrant for scatter plots
      if (quadrantFilter && data.visualizationType === 'SCATTER_PLOT') {
        const pointQuadrant = getPointQuadrant(data, row.label);
        if (pointQuadrant !== quadrantFilter) return false;
      }
      return true;
    });
  }, [rows, conferenceFilter, divisionFilter, quadrantFilter, data]);

  const table = useReactTable({
    data: filteredRows,
    columns,
    state: { sorting, globalFilter },
    onSortingChange: setSorting,
    onGlobalFilterChange: setGlobalFilter,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  });

  // Notify parent of highlighted labels when filters change
  useEffect(() => {
    if (onHighlight) {
      const visibleLabels = table.getFilteredRowModel().rows.map(row => row.original.label);
      onHighlight(visibleLabels);
    }
  }, [conferenceFilter, divisionFilter, globalFilter, table, onHighlight]);

  if (rows.length === 0) return null;

  const hasFilters = conferences.length > 0 || divisions.length > 0;

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex flex-wrap items-center gap-2 mb-3 shrink-0">
        {conferences.length > 0 && (
          <select
            value={conferenceFilter}
            onChange={e => setConferenceFilter(e.target.value)}
            className="px-2 py-1.5 text-sm border border-[var(--border)] rounded bg-[var(--background)] text-[var(--foreground)]"
          >
            <option value="">All Conf</option>
            {conferences.map(conf => (
              <option key={conf} value={conf}>{conf}</option>
            ))}
          </select>
        )}
        {divisions.length > 0 && (
          <select
            value={divisionFilter}
            onChange={e => setDivisionFilter(e.target.value)}
            className="px-2 py-1.5 text-sm border border-[var(--border)] rounded bg-[var(--background)] text-[var(--foreground)]"
          >
            <option value="">All Div</option>
            {divisions.map(div => (
              <option key={div} value={div}>{div}</option>
            ))}
          </select>
        )}
        <input
          type="text"
          value={globalFilter}
          onChange={e => setGlobalFilter(e.target.value)}
          placeholder="Filter..."
          className="flex-1 min-w-32 md:w-48 px-3 py-1.5 text-sm border border-[var(--border)] rounded bg-[var(--background)] text-[var(--foreground)] placeholder:text-[var(--muted)]"
        />
        <span className="text-xs text-[var(--muted)] whitespace-nowrap">
          {table.getFilteredRowModel().rows.length} of {rows.length}
        </span>
      </div>
      <div className="overflow-auto min-h-0 flex-1 border border-[var(--border)] rounded">
        <table className="w-full text-sm">
          <thead className="sticky top-0 z-10">
            {table.getHeaderGroups().map(headerGroup => (
              <tr key={headerGroup.id} className="border-b border-[var(--border)] bg-[var(--card)]">
                {headerGroup.headers.map(header => (
                  <th
                    key={header.id}
                    onClick={header.column.getToggleSortingHandler()}
                    className="px-3 py-2 text-left text-xs font-medium text-[var(--muted)] uppercase tracking-wider cursor-pointer select-none hover:text-[var(--foreground)] transition-colors whitespace-nowrap bg-[var(--card)]"
                  >
                    <span className="flex items-center gap-1">
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {{ asc: ' \u2191', desc: ' \u2193' }[header.column.getIsSorted() as string] ?? ''}
                    </span>
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map(row => {
              const isSelected = selectedLabel === row.original.label;
              const rowColor = getItemColor?.(row.original.label) || null;
              // Convert color to rgba with 20% opacity for background
              let bgColor: string | undefined;
              if (isSelected && rowColor) {
                if (rowColor.startsWith('rgb(')) {
                  bgColor = rowColor.replace('rgb(', 'rgba(').replace(')', ', 0.2)');
                } else if (rowColor.startsWith('#')) {
                  bgColor = `${rowColor}33`; // 33 = 20% opacity in hex
                } else {
                  bgColor = rowColor;
                }
              }
              return (
                <tr
                  key={row.id}
                  className={`border-b border-[var(--border)] last:border-b-0 hover:bg-[var(--card)] transition-colors cursor-pointer`}
                  style={isSelected ? { backgroundColor: bgColor } : undefined}
                  onClick={() => onSelect?.(row.original.label)}
                >
                  {row.getVisibleCells().map((cell, cellIndex) => (
                    <td key={cell.id} className="px-3 py-2 whitespace-nowrap">
                      <span className="flex items-center gap-2">
                        {cellIndex === 0 && rowColor && (
                          <span
                            className="w-2 h-2 rounded-full shrink-0"
                            style={{ backgroundColor: rowColor }}
                          />
                        )}
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </span>
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
