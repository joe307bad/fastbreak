'use client';

import { StatComparison as StatComparisonType } from '@/types/chart';

/**
 * How a sport writes a percentage on the wire. Basketball, hockey and baseball
 * send a 0-1 fraction; football sends the percentage itself, so multiplying
 * would read 2780% where the team converted 27.8% of its third downs.
 */
export type PercentScale = 'fraction' | 'percent';

interface StatRowProps {
  stat: StatComparisonType;
  awayAbbrev: string;
  homeAbbrev: string;
  percentScale?: PercentScale;
}

/**
 * How many decimals a row should carry. A stat that lives entirely under one -
 * a batting average, an EPA per play - says nothing at a single decimal, where
 * -.122 and -.050 both round to -0.1. Decided from both sides of the row at
 * once so the two numbers are written the same way.
 */
export function matchupValueDecimals(...values: Array<number | null | undefined>): number {
  const numbers = values.filter((value): value is number => value != null);
  if (numbers.length === 0) return 1;
  return numbers.every(value => Math.abs(value) < 1) ? 3 : 1;
}

export function formatMatchupStatValue(
  value: number | null | undefined,
  label: string,
  percentScale: PercentScale = 'fraction',
  decimals = 1
): string {
  if (value == null) return '-';
  // Format percentages
  if (label.toLowerCase().includes('%') || label.toLowerCase().includes('pct')) {
    const pct = percentScale === 'fraction' ? value * 100 : value;
    return `${pct.toFixed(1)}%`;
  }
  // Format ratings and per-game stats
  if (value % 1 !== 0) {
    return value.toFixed(decimals);
  }
  return value.toString();
}

function getRankBadgeClasses(rank: number): string {
  const base = 'inline-flex items-center justify-center w-7 h-4 rounded text-[10px] font-medium';
  if (rank <= 10) return `${base} bg-green-500/20 text-green-500`;
  if (rank >= 21) return `${base} bg-red-500/20 text-red-500`;
  return `${base} bg-[var(--muted)]/20 text-[var(--muted)]`;
}

function StatRow({ stat, percentScale }: StatRowProps) {
  const decimals = matchupValueDecimals(stat.away.value, stat.home.value);
  const awayValue = formatMatchupStatValue(stat.away.value, stat.label, percentScale, decimals);
  const homeValue = formatMatchupStatValue(stat.home.value, stat.label, percentScale, decimals);

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
  percentScale?: PercentScale;
}

export function StatComparisonTable({ stats, awayAbbrev, homeAbbrev, title, percentScale }: StatComparisonProps) {
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
            percentScale={percentScale}
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
      overall?: Record<string, StatComparisonType>;
      misc?: Record<string, StatComparisonType>;
    };
  };
  awayAbbrev: string;
  homeAbbrev: string;
  percentScale?: PercentScale;
}

export function FullStatComparison({ comparisons, awayAbbrev, homeAbbrev, percentScale }: FullComparisonProps) {
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
          percentScale={percentScale}
        />
      )}
      {sideBySide.defense && (
        <StatComparisonTable
          stats={sideBySide.defense}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
          title="Defense"
          percentScale={percentScale}
        />
      )}
      {sideBySide.overall && (
        <StatComparisonTable
          stats={sideBySide.overall}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
          title="Overall"
          percentScale={percentScale}
        />
      )}
      {sideBySide.misc && (
        <StatComparisonTable
          stats={sideBySide.misc}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
          title="Other"
          percentScale={percentScale}
        />
      )}
    </div>
  );
}
