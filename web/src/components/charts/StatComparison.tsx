'use client';

import { StatComparison as StatComparisonType } from '@/types/chart';

interface StatRowProps {
  stat: StatComparisonType;
  awayAbbrev: string;
  homeAbbrev: string;
}

function formatValue(value: number, label: string): string {
  // Format percentages
  if (label.toLowerCase().includes('%') || label.toLowerCase().includes('pct')) {
    return `${(value * 100).toFixed(1)}%`;
  }
  // Format ratings and per-game stats
  if (value % 1 !== 0) {
    return value.toFixed(1);
  }
  return value.toString();
}

function getRankBadgeClasses(rank: number): string {
  const base = 'inline-flex items-center justify-center w-7 h-4 rounded text-[10px] font-medium';
  if (rank <= 10) return `${base} bg-green-500/20 text-green-500`;
  if (rank >= 21) return `${base} bg-red-500/20 text-red-500`;
  return `${base} bg-[var(--muted)]/20 text-[var(--muted)]`;
}

function StatRow({ stat }: StatRowProps) {
  const awayValue = formatValue(stat.away.value, stat.label);
  const homeValue = formatValue(stat.home.value, stat.label);

  // Determine edge based on rank (lower rank = better)
  const awayHasEdge = stat.away.rank < stat.home.rank;
  const homeHasEdge = stat.home.rank < stat.away.rank;

  return (
    <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 py-1 border-b border-[var(--border)] last:border-b-0 text-sm items-center">
      {/* Away side - edge, value, rank (rank on inside) all aligned right */}
      <div className="flex items-center justify-end gap-1">
        <span className="w-3 text-right shrink-0">
          {awayHasEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
        </span>
        <span className="w-12 text-right font-mono shrink-0">{awayValue}</span>
        <span className="w-9 shrink-0 flex justify-end">
          <span className={getRankBadgeClasses(stat.away.rank)}>
            {stat.away.rank}
          </span>
        </span>
      </div>

      {/* Stat Name */}
      <div className="text-center text-xs text-[var(--muted)] truncate px-1">
        {stat.label}
      </div>

      {/* Home side - rank, value, edge (rank on inside) all aligned left */}
      <div className="flex items-center justify-start gap-1">
        <span className="w-9 shrink-0 flex justify-start">
          <span className={getRankBadgeClasses(stat.home.rank)}>
            {stat.home.rank}
          </span>
        </span>
        <span className="w-12 text-left font-mono shrink-0">{homeValue}</span>
        <span className="w-3 text-left shrink-0">
          {homeHasEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
        </span>
      </div>
    </div>
  );
}

interface StatComparisonProps {
  stats: Record<string, StatComparisonType>;
  awayAbbrev: string;
  homeAbbrev: string;
  title?: string;
}

export function StatComparisonTable({ stats, awayAbbrev, homeAbbrev, title }: StatComparisonProps) {
  const statEntries = Object.entries(stats);

  if (statEntries.length === 0) return null;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)]">
      {/* Header */}
      <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30 items-center">
        <div className="text-right text-xs font-bold">{awayAbbrev}</div>
        <div className="text-center text-xs font-bold">{title || 'Stats'}</div>
        <div className="text-left text-xs font-bold">{homeAbbrev}</div>
      </div>

      {/* Stats */}
      <div className="px-2">
        {statEntries.map(([key, stat]) => (
          <StatRow
            key={key}
            stat={stat}
            awayAbbrev={awayAbbrev}
            homeAbbrev={homeAbbrev}
          />
        ))}
      </div>
    </div>
  );
}

interface FullComparisonProps {
  comparisons: {
    sideBySide?: {
      offense?: Record<string, StatComparisonType>;
      defense?: Record<string, StatComparisonType>;
      misc?: Record<string, StatComparisonType>;
    };
  };
  awayAbbrev: string;
  homeAbbrev: string;
}

export function FullStatComparison({ comparisons, awayAbbrev, homeAbbrev }: FullComparisonProps) {
  const { sideBySide } = comparisons;

  if (!sideBySide) return null;

  return (
    <div className="space-y-2">
      {sideBySide.offense && (
        <StatComparisonTable
          stats={sideBySide.offense}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
          title="Offense"
        />
      )}
      {sideBySide.defense && (
        <StatComparisonTable
          stats={sideBySide.defense}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
          title="Defense"
        />
      )}
      {sideBySide.misc && (
        <StatComparisonTable
          stats={sideBySide.misc}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
          title="Other"
        />
      )}
    </div>
  );
}
