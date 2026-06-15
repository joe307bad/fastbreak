'use client';

import { useEffect, useMemo, useState } from 'react';
import { BottomSheet } from '@/components/ui/BottomSheet';
import { PlayoffChanceEntry, RankingEntry } from '@/types/chart';

function rankingTeamCode(entry: RankingEntry | PlayoffChanceEntry): string {
  return (entry.team ?? entry.teamCode ?? '').toUpperCase();
}

function teamRankColor(rank: number | null | undefined): string {
  if (rank == null) return 'bg-[var(--muted)]';
  if (rank <= 10) return 'bg-green-600';
  if (rank <= 15) return 'bg-lime-600';
  if (rank <= 20) return 'bg-orange-500';
  return 'bg-red-600';
}

function playoffProbColor(prob: number | null | undefined): string {
  if (prob == null) return 'bg-[var(--muted)]';
  const p = Math.max(0, Math.min(100, prob));
  if (p <= 5) {
    const t = p / 5;
    const r = Math.round(179 + 51 * t);
    const g = Math.round(26 + 102 * t);
    return `rgb(${r}, ${g}, 0)`;
  }
  return '#228B22';
}

function formatRankingValue(value: number, isPct: boolean): string {
  if (isPct) {
    const pctVal = value <= 1 ? value * 100 : value;
    return `${pctVal.toFixed(1)}%`;
  }
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatPlayoffProb(prob: number | null | undefined): string {
  if (prob == null) return '-';
  if (prob >= 99.5) return '>99%';
  if (prob > 0 && prob < 1) return '<1%';
  return `${Math.round(prob)}%`;
}

function formatWinPct(winPct: number | null | undefined): string {
  if (winPct == null) return '-';
  const scaled = winPct <= 1 ? winPct : winPct / 100;
  return `.${Math.round(scaled * 1000).toString().padStart(3, '0').slice(-3)}`;
}

function RankBadge({ rank, display }: { rank: number; display?: string | null }) {
  return (
    <span
      className={`inline-flex items-center justify-center min-w-8 h-5 px-1 rounded text-[10px] font-bold text-white ${teamRankColor(rank)}`}
    >
      {display ?? rank}
    </span>
  );
}

function ProbBadge({ prob }: { prob: number | null | undefined }) {
  return (
    <span
      className="inline-flex items-center justify-center min-w-[3.25rem] h-5 px-1 rounded text-[10px] font-bold text-white"
      style={{ backgroundColor: playoffProbColor(prob) }}
    >
      {formatPlayoffProb(prob)}
    </span>
  );
}

export function StatRankingsSheet({
  open,
  onClose,
  title,
  entries,
  highlightedTeam,
  isPct = false,
  subtitle = 'Season Rankings',
  source,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  entries: RankingEntry[];
  highlightedTeam: string;
  isPct?: boolean;
  subtitle?: string;
  source?: string;
}) {
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(() => new Set([highlightedTeam.toUpperCase()]));

  useEffect(() => {
    if (open) {
      setSelectedTeams(new Set([highlightedTeam.toUpperCase()]));
    }
  }, [open, highlightedTeam]);

  const toggleTeam = (team: string) => {
    const code = team.toUpperCase();
    setSelectedTeams(prev => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={title}
      subtitle={subtitle}
      source={source}
    >
      <div className="grid grid-cols-[2rem_2.5rem_1fr] gap-x-2 pl-3 pr-1 py-1 text-[10px] font-bold uppercase text-[var(--muted)] border-b border-[var(--border)]">
        <div className="text-center pl-1">RK</div>
        <div>Team</div>
        <div className="text-right">Value</div>
      </div>

      {entries.map(entry => {
        const team = rankingTeamCode(entry);
        const selected = selectedTeams.has(team);
        return (
          <button
            key={team}
            type="button"
            onClick={() => toggleTeam(team)}
            className={`w-full grid grid-cols-[2rem_2.5rem_1fr] gap-x-2 items-center pl-3 pr-1 py-1.5 text-sm text-left border-b border-[var(--border)] last:border-b-0 transition-colors ${
              selected ? 'bg-[var(--foreground)]/8' : 'hover:bg-[var(--foreground)]/5'
            }`}
          >
            <div className="flex justify-center pl-1">
              <RankBadge rank={entry.rank} display={entry.rankDisplay} />
            </div>
            <span className={`font-mono ${selected ? 'font-bold' : 'font-medium'}`}>{team}</span>
            <span className={`font-mono text-right ${selected ? 'font-bold' : ''}`}>
              {formatRankingValue(entry.value, isPct)}
            </span>
          </button>
        );
      })}
    </BottomSheet>
  );
}

function PlayoffCutoffDivider({ label = 'PLAYOFF CUTOFF' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-1.5">
      <div className="flex-1 border-t border-dashed border-[var(--border)]" />
      <span className="text-[8px] font-bold uppercase tracking-wide text-[var(--muted)]">{label}</span>
      <div className="flex-1 border-t border-dashed border-[var(--border)]" />
    </div>
  );
}

function PlayoffConferenceSection({
  conference,
  entries,
  selectedTeams,
  onToggleTeam,
  playoffCutoff,
}: {
  conference: string;
  entries: PlayoffChanceEntry[];
  selectedTeams: Set<string>;
  onToggleTeam: (team: string) => void;
  playoffCutoff: number;
}) {
  return (
    <div className="mb-4 last:mb-0">
      <div className="text-xs font-bold font-mono uppercase text-[var(--muted)] mb-1">{conference}</div>
      <div className="grid grid-cols-[1.25rem_2.5rem_2.25rem_2.25rem_3.25rem_3.25rem] gap-x-1 pl-3 pr-1 py-1 text-[9px] font-bold uppercase text-[var(--muted)] border-b border-[var(--border)]">
        <div className="text-center pl-1">#</div>
        <div>Team</div>
        <div className="text-right">W-L</div>
        <div className="text-right">Win%</div>
        <div className="text-center pl-2">Playoff</div>
        <div className="text-center pl-2">WS</div>
      </div>
      {entries.map((entry, index) => {
        const seed = index + 1;
        const team = rankingTeamCode(entry);
        const selected = selectedTeams.has(team);
        const wl =
          entry.wins != null && entry.losses != null ? `${entry.wins}-${entry.losses}` : '-';

        return (
          <div key={`${conference}-${team}`}>
            {playoffCutoff > 0 && seed === playoffCutoff + 1 && <PlayoffCutoffDivider />}
            <button
              type="button"
              onClick={() => onToggleTeam(team)}
              className={`w-full grid grid-cols-[1.25rem_2.5rem_2.25rem_2.25rem_3.25rem_3.25rem] gap-x-1 items-center pl-3 pr-1 py-1.5 text-sm text-left border-b border-[var(--border)] last:border-b-0 transition-colors ${
                selected ? 'bg-[var(--foreground)]/8' : 'hover:bg-[var(--foreground)]/5'
              }`}
            >
              <span className="text-center font-mono text-xs text-[var(--muted)] pl-1">{seed}</span>
              <span className={`font-mono ${selected ? 'font-bold' : 'font-medium'}`}>{team}</span>
              <span className="font-mono text-right text-xs">{wl}</span>
              <span className="font-mono text-right text-xs">{formatWinPct(entry.winPct ?? null)}</span>
              <div className="flex justify-center pl-2">
                <ProbBadge prob={entry.playoffProb ?? null} />
              </div>
              <div className="flex justify-center pl-2">
                <ProbBadge prob={entry.champProb ?? null} />
              </div>
            </button>
          </div>
        );
      })}
    </div>
  );
}

export function PlayoffChancesSheet({
  open,
  onClose,
  title,
  entries,
  highlightedTeam,
  subtitle = 'Season Projections',
  source = 'PlayoffStatus.com',
  playoffCutoff = 6,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  entries: PlayoffChanceEntry[];
  highlightedTeam: string;
  subtitle?: string;
  source?: string;
  playoffCutoff?: number;
}) {
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(() => new Set([highlightedTeam.toUpperCase()]));

  useEffect(() => {
    if (open) {
      setSelectedTeams(new Set([highlightedTeam.toUpperCase()]));
    }
  }, [open, highlightedTeam]);

  const hasConferenceData = entries.some(e => e.conference);

  const conferenceGroups = useMemo(() => {
    if (!hasConferenceData) return null;
    const grouped = entries.reduce<Record<string, PlayoffChanceEntry[]>>((acc, entry) => {
      const conf = entry.conference ?? 'Unknown';
      if (!acc[conf]) acc[conf] = [];
      acc[conf].push(entry);
      return acc;
    }, {});

    const sortEntries = (list: PlayoffChanceEntry[]) =>
      [...list].sort((a, b) => (b.playoffProb ?? 0) - (a.playoffProb ?? 0));

    return Object.entries(grouped)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([conference, confEntries]) => [conference, sortEntries(confEntries)] as const);
  }, [entries, hasConferenceData]);

  const flatEntries = useMemo(
    () => [...entries].sort((a, b) => (b.playoffProb ?? 0) - (a.playoffProb ?? 0)),
    [entries]
  );

  const toggleTeam = (team: string) => {
    const code = team.toUpperCase();
    setSelectedTeams(prev => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={title}
      subtitle={subtitle}
      source={source}
    >
      {conferenceGroups ? (
        conferenceGroups.map(([conference, confEntries]) => (
          <PlayoffConferenceSection
            key={conference}
            conference={conference}
            entries={confEntries}
            selectedTeams={selectedTeams}
            onToggleTeam={toggleTeam}
            playoffCutoff={playoffCutoff}
          />
        ))
      ) : (
        <PlayoffConferenceSection
          conference="League"
          entries={flatEntries}
          selectedTeams={selectedTeams}
          onToggleTeam={toggleTeam}
          playoffCutoff={playoffCutoff}
        />
      )}
    </BottomSheet>
  );
}

export function ChartInfoSheet({
  open,
  onClose,
  title,
  description,
  source,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description: string;
  source?: string;
}) {
  return (
    <BottomSheet open={open} onClose={onClose} title={title} source={source}>
      <p className="text-sm text-[var(--muted)] leading-relaxed whitespace-pre-wrap pb-6">
        {description}
      </p>
    </BottomSheet>
  );
}

export function formatReportCardRankingLabel(seasonLabel: string, key: string): string {
  switch (key) {
    case 'record':
      return `${seasonLabel} / Record`;
    case 'overallComposite':
      return `${seasonLabel} / Overall Composite`;
    case 'hittersComposite':
      return `${seasonLabel} / Hitters Composite`;
    case 'startersComposite':
      return `${seasonLabel} / Starting Pitchers Composite`;
    case 'relieversComposite':
      return `${seasonLabel} / Bullpen Composite`;
    case 'fieldersComposite':
      return `${seasonLabel} / Fielders Composite`;
    default:
      return `${seasonLabel} / ${key}`;
  }
}
