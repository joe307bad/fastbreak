'use client';

import { useState } from 'react';
import { NBAComparisons, OffDefComparison, OffDefStatEntry } from '@/types/chart';
import { FullStatComparison } from './StatComparison';

type ComparisonTab = 'team' | 'homeOffVsAwayDef' | 'awayOffVsHomeDef';

interface TabButtonProps {
  label: string;
  active: boolean;
  onClick: () => void;
}

function TabButton({ label, active, onClick }: TabButtonProps) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1 text-xs rounded-full border transition-colors ${
        active
          ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
          : 'bg-transparent text-[var(--muted)] border-[var(--border)] hover:border-[var(--foreground)] hover:text-[var(--foreground)]'
      }`}
    >
      {label}
    </button>
  );
}

function getRankBadgeClasses(rank: number): string {
  const base = 'inline-flex items-center justify-center w-7 h-4 rounded text-[10px] font-medium';
  if (rank <= 10) return `${base} bg-green-500/20 text-green-500`;
  if (rank >= 21) return `${base} bg-red-500/20 text-red-500`;
  return `${base} bg-[var(--muted)]/20 text-[var(--muted)]`;
}

function formatValue(value: number, label: string): string {
  if (label.toLowerCase().includes('%') || label.toLowerCase().includes('pct')) {
    return `${(value * 100).toFixed(1)}%`;
  }
  if (value % 1 !== 0) {
    return value.toFixed(1);
  }
  return value.toString();
}

function OffDefRow({ stat }: { stat: OffDefStatEntry }) {
  const offValue = formatValue(stat.offense.value, stat.offLabel);
  const defValue = formatValue(stat.defense.value, stat.defLabel);

  // Defense has edge when they allow LESS than offense typically scores
  // Offense has edge when defense allows MORE than offense typically scores
  const defenseHasEdge = stat.defense.value < stat.offense.value;
  const offenseHasEdge = stat.defense.value > stat.offense.value;

  return (
    <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 py-1 border-b border-[var(--border)] last:border-b-0 text-sm items-center">
      {/* Offense side - edge, value, rank (rank on inside) all aligned right */}
      <div className="flex items-center justify-end gap-1">
        <span className="w-3 text-right shrink-0">
          {offenseHasEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
        </span>
        <span className="w-12 text-right font-mono shrink-0">{offValue}</span>
        <span className="w-9 shrink-0 flex justify-end">
          <span className={getRankBadgeClasses(stat.offense.rank)}>
            {stat.offense.rank}
          </span>
        </span>
      </div>
      {/* Stat Label */}
      <div className="text-center text-xs text-[var(--muted)] truncate px-1">
        {stat.offLabel}
      </div>
      {/* Defense side - rank, value, edge (rank on inside) all aligned left */}
      <div className="flex items-center justify-start gap-1">
        <span className="w-9 shrink-0 flex justify-start">
          <span className={getRankBadgeClasses(stat.defense.rank)}>
            {stat.defense.rank}
          </span>
        </span>
        <span className="w-12 text-left font-mono shrink-0">{defValue}</span>
        <span className="w-3 text-left shrink-0">
          {defenseHasEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
        </span>
      </div>
    </div>
  );
}

interface OffDefTableProps {
  stats: OffDefComparison;
  offAbbrev: string;
  defAbbrev: string;
  title: string;
}

function OffDefComparisonTable({ stats, offAbbrev, defAbbrev, title }: OffDefTableProps) {
  const statEntries = Object.entries(stats).filter(([, v]) => v !== undefined) as [string, OffDefStatEntry][];

  if (statEntries.length === 0) return null;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)]">
      <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30 items-center">
        <div className="text-right text-xs font-bold">{offAbbrev}</div>
        <div className="text-center text-xs font-bold">{title}</div>
        <div className="text-left text-xs font-bold">{defAbbrev}</div>
      </div>
      <div className="px-2">
        {statEntries.map(([key, stat]) => (
          <OffDefRow key={key} stat={stat} />
        ))}
      </div>
    </div>
  );
}

interface MatchupComparisonsProps {
  comparisons: NBAComparisons;
  awayAbbrev: string;
  homeAbbrev: string;
}

export function MatchupComparisons({ comparisons, awayAbbrev, homeAbbrev }: MatchupComparisonsProps) {
  const [activeTab, setActiveTab] = useState<ComparisonTab>('team');

  const hasHomeOffVsAwayDef = comparisons.homeOffVsAwayDef &&
    Object.values(comparisons.homeOffVsAwayDef).some(v => v?.offense && v?.defense);
  const hasAwayOffVsHomeDef = comparisons.awayOffVsHomeDef &&
    Object.values(comparisons.awayOffVsHomeDef).some(v => v?.offense && v?.defense);

  return (
    <div>
      {/* Tab Navigation */}
      <div className="flex gap-2 mb-2">
        <TabButton
          label="Team"
          active={activeTab === 'team'}
          onClick={() => setActiveTab('team')}
        />
        {hasHomeOffVsAwayDef && (
          <TabButton
            label={`${homeAbbrev} Off vs ${awayAbbrev} Def`}
            active={activeTab === 'homeOffVsAwayDef'}
            onClick={() => setActiveTab('homeOffVsAwayDef')}
          />
        )}
        {hasAwayOffVsHomeDef && (
          <TabButton
            label={`${awayAbbrev} Off vs ${homeAbbrev} Def`}
            active={activeTab === 'awayOffVsHomeDef'}
            onClick={() => setActiveTab('awayOffVsHomeDef')}
          />
        )}
      </div>

      {/* Tab Content */}
      {activeTab === 'team' && comparisons.sideBySide && (
        <FullStatComparison
          comparisons={{ sideBySide: comparisons.sideBySide }}
          awayAbbrev={awayAbbrev}
          homeAbbrev={homeAbbrev}
        />
      )}

      {activeTab === 'homeOffVsAwayDef' && comparisons.homeOffVsAwayDef && (
        <OffDefComparisonTable
          stats={comparisons.homeOffVsAwayDef}
          offAbbrev={`${homeAbbrev} Off`}
          defAbbrev={`${awayAbbrev} Def`}
          title="Matchup"
        />
      )}

      {activeTab === 'awayOffVsHomeDef' && comparisons.awayOffVsHomeDef && (
        <OffDefComparisonTable
          stats={comparisons.awayOffVsHomeDef}
          offAbbrev={`${awayAbbrev} Off`}
          defAbbrev={`${homeAbbrev} Def`}
          title="Matchup"
        />
      )}
    </div>
  );
}
