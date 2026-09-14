'use client';

import { Fragment, useMemo, useState } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  flexRender,
  createColumnHelper,
  SortingState,
} from '@tanstack/react-table';
import type { SchedulerRun } from '@/types/diagnostics';

const columnHelper = createColumnHelper<SchedulerRun>();

function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds)) return '—';
  if (seconds < 60) return `${seconds.toFixed(1)}s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return `${minutes}m ${rest.toString().padStart(2, '0')}s`;
}

function formatTimestamp(iso: string): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toISOString().replace('T', ' ').replace(/\.\d+Z$/, 'Z');
}

const selectClass =
  'px-2 py-1.5 text-sm border border-[var(--border)] rounded bg-[var(--background)] text-[var(--foreground)]';

export function DiagnosticsTable({ runs }: { runs: SchedulerRun[] }) {
  const [sorting, setSorting] = useState<SortingState>([{ id: 'finishedAt', desc: true }]);
  const [globalFilter, setGlobalFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [scheduleFilter, setScheduleFilter] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);

  const schedules = useMemo(
    () => Array.from(new Set(runs.map((run) => run.schedule))).sort(),
    [runs]
  );

  const filteredRuns = useMemo(
    () =>
      runs.filter(
        (run) =>
          (!statusFilter || run.status === statusFilter) &&
          (!scheduleFilter || run.schedule === scheduleFilter)
      ),
    [runs, statusFilter, scheduleFilter]
  );

  const columns = useMemo(
    () => [
      columnHelper.accessor('status', {
        header: 'Status',
        cell: (info) => {
          const status = info.getValue();
          const ok = status === 'success';
          return (
            <span className="flex items-center gap-2">
              <span
                className={`w-2 h-2 rounded-full shrink-0 ${ok ? 'bg-emerald-500' : 'bg-red-500'}`}
              />
              <span className={ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}>
                {status}
              </span>
            </span>
          );
        },
      }),
      columnHelper.accessor('script', { header: 'Script' }),
      columnHelper.accessor('schedule', { header: 'Schedule' }),
      columnHelper.accessor('finishedAt', {
        header: 'Finished (UTC)',
        cell: (info) => formatTimestamp(info.getValue()),
      }),
      columnHelper.accessor('durationSeconds', {
        header: 'Duration',
        cell: (info) => formatDuration(Number(info.getValue())),
      }),
      columnHelper.accessor('exitCode', { header: 'Exit' }),
      columnHelper.accessor('error', {
        header: 'Error',
        enableSorting: false,
        cell: (info) => {
          const error = info.getValue();
          if (!error) return <span className="text-[var(--muted)]">—</span>;
          const key = info.row.original.file_key;
          const isOpen = expanded === key;
          // R always ends with "Execution halted", so prefer the last line that names the error
          const lines = error.split('\n').filter(Boolean);
          const preview = [...lines].reverse().find((line) => /error/i.test(line)) ?? lines.at(-1) ?? error;
          return (
            <button
              type="button"
              onClick={() => setExpanded(isOpen ? null : key)}
              className="text-left text-red-600 dark:text-red-400 hover:underline max-w-xs truncate block"
              title={isOpen ? 'Collapse' : 'Show full output'}
            >
              {preview}
            </button>
          );
        },
      }),
    ],
    [expanded]
  );

  const table = useReactTable({
    data: filteredRuns,
    columns,
    state: { sorting, globalFilter },
    onSortingChange: setSorting,
    onGlobalFilterChange: setGlobalFilter,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  });

  if (runs.length === 0) {
    return (
      <p className="text-sm text-[var(--muted)]">
        No script runs recorded yet. Rows appear here after the pipeline runs and the site is rebuilt.
      </p>
    );
  }

  return (
    <div className="flex flex-col">
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className={selectClass}>
          <option value="">All statuses</option>
          <option value="success">Success</option>
          <option value="failed">Failed</option>
        </select>
        <select value={scheduleFilter} onChange={(e) => setScheduleFilter(e.target.value)} className={selectClass}>
          <option value="">All schedules</option>
          {schedules.map((schedule) => (
            <option key={schedule} value={schedule}>
              {schedule}
            </option>
          ))}
        </select>
        <input
          type="text"
          value={globalFilter}
          onChange={(e) => setGlobalFilter(e.target.value)}
          placeholder="Filter scripts..."
          className="flex-1 min-w-32 md:w-48 px-3 py-1.5 text-sm border border-[var(--border)] rounded bg-[var(--background)] text-[var(--foreground)] placeholder:text-[var(--muted)]"
        />
        <span className="text-xs text-[var(--muted)] whitespace-nowrap">
          {table.getFilteredRowModel().rows.length} of {runs.length}
        </span>
      </div>

      <div className="overflow-x-auto border border-[var(--border)] rounded">
        <table className="w-full text-sm">
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id} className="border-b border-[var(--border)] bg-[var(--card)]">
                {headerGroup.headers.map((header) => (
                  <th
                    key={header.id}
                    onClick={header.column.getToggleSortingHandler()}
                    className={`px-3 py-2 text-left text-xs font-medium text-[var(--muted)] uppercase tracking-wider select-none whitespace-nowrap bg-[var(--card)] ${
                      header.column.getCanSort() ? 'cursor-pointer hover:text-[var(--foreground)] transition-colors' : ''
                    }`}
                  >
                    <span className="flex items-center gap-1">
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {{ asc: '↑', desc: '↓' }[header.column.getIsSorted() as string] ?? null}
                    </span>
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => {
              const run = row.original;
              const isOpen = expanded === run.file_key && !!run.error;
              return (
                <Fragment key={row.id}>
                  <tr
                    className="border-b border-[var(--border)] last:border-b-0 hover:bg-[var(--card)] transition-colors"
                  >
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="px-3 py-2 whitespace-nowrap align-top">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                  {isOpen && (
                    <tr className="border-b border-[var(--border)] bg-[var(--card)]">
                      <td colSpan={columns.length} className="px-3 py-2">
                        <pre className="text-xs whitespace-pre-wrap break-words max-h-80 overflow-auto text-red-600 dark:text-red-400">
                          {run.error}
                        </pre>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
