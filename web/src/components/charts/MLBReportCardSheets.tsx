'use client';

import { useEffect, useMemo, useState } from 'react';
import { BottomSheet } from '@/components/ui/BottomSheet';
import { PlayoffChanceEntry, RankingEntry, ReportCardTeam } from '@/types/chart';

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

function playerRankColor(rank: number | null | undefined): string {
  if (rank == null) return 'bg-[var(--muted)]';
  if (rank <= 30) return 'bg-green-600';
  if (rank <= 60) return 'bg-lime-600';
  if (rank <= 100) return 'bg-orange-500';
  return 'bg-red-600';
}

function rankingEntryLabel(entry: RankingEntry): string {
  return entry.player?.trim() ? entry.player : rankingTeamCode(entry);
}

function entriesArePlayerRankings(entries: RankingEntry[]): boolean {
  return entries.some(entry => !!entry.player?.trim());
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

function formatPlayoffGamesBack(gamesBack: number | null | undefined): string {
  if (gamesBack == null || gamesBack <= 0) return '-';
  const rounded = Math.round(gamesBack * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

const MLB_STANDINGS_SECTION_ORDER: Record<string, string[]> = {
  National: ['NL East', 'NL Central', 'NL West', 'Wild Card'],
  American: ['AL East', 'AL Central', 'AL West', 'Wild Card'],
};

function mlbGamesBack(w: number, l: number, wRef: number, lRef: number): number {
  return ((wRef - w) + (l - lRef)) / 2;
}

function displayMlbStandingsGamesBack(
  sectionName: string,
  entry: PlayoffChanceEntry,
  sectionEntries: PlayoffChanceEntry[]
): number | null {
  if (sectionName === 'Wild Card') {
    return entry.gamesBackFromPlayoff ?? null;
  }
  const leader =
    sectionEntries.find(item => item.divisionRank === 1) ??
    sectionEntries.reduce<PlayoffChanceEntry | null>((best, item) => {
      if (!best) return item;
      const bestRank = best.divisionRank ?? Number.MAX_SAFE_INTEGER;
      const itemRank = item.divisionRank ?? Number.MAX_SAFE_INTEGER;
      return itemRank < bestRank ? item : best;
    }, null);
  if (
    leader?.wins == null ||
    leader.losses == null ||
    entry.wins == null ||
    entry.losses == null
  ) {
    return null;
  }
  const gb = mlbGamesBack(entry.wins, entry.losses, leader.wins, leader.losses);
  return gb <= 0 ? 0 : Math.round(gb * 10) / 10;
}

function dedupeMlbPlayoffChanceEntries(entries: PlayoffChanceEntry[]): PlayoffChanceEntry[] {
  const byTeam = new Map<string, PlayoffChanceEntry[]>();
  entries.forEach(entry => {
    const code = rankingTeamCode(entry);
    const group = byTeam.get(code) ?? [];
    group.push(entry);
    byTeam.set(code, group);
  });
  return [...byTeam.values()].map(group =>
    group.find(entry => entry.standingsSection !== 'Wild Card') ?? group[0]
  );
}

function wildCardEntriesForConference(entries: PlayoffChanceEntry[]): PlayoffChanceEntry[] {
  return entries
    .filter(entry => (entry.divisionRank ?? Number.MAX_SAFE_INTEGER) > 1)
    .sort((a, b) => {
      const pctDiff = (b.winPct ?? 0) - (a.winPct ?? 0);
      if (pctDiff !== 0) return pctDiff;
      const winsDiff = (b.wins ?? 0) - (a.wins ?? 0);
      if (winsDiff !== 0) return winsDiff;
      return (a.losses ?? Number.MAX_SAFE_INTEGER) - (b.losses ?? Number.MAX_SAFE_INTEGER);
    });
}

function organizeMlbPlayoffChancesForDisplay(
  entries: PlayoffChanceEntry[],
  teams: Record<string, ReportCardTeam>
): PlayoffChanceEntry[] {
  const uniqueEntries = dedupeMlbPlayoffChanceEntries(entries);
  if (uniqueEntries.some(entry => entry.standingsSection && entry.standingsSection !== 'Wild Card')) {
    return uniqueEntries;
  }

  type Enriched = {
    entry: PlayoffChanceEntry;
    division?: string | null;
    league?: string | null;
    divisionRank?: number | null;
    wins?: number | null;
    losses?: number | null;
    winPct?: number | null;
  };

  const enrich = (entry: PlayoffChanceEntry): Enriched => {
    const code = rankingTeamCode(entry);
    const team = teams[code] ?? Object.values(teams).find(t => t.teamCode.toUpperCase() === code);
    const wins = entry.wins ?? team?.wins ?? null;
    const losses = entry.losses ?? team?.losses ?? null;
    const winPct =
      entry.winPct ??
      (wins != null && losses != null && wins + losses > 0 ? wins / (wins + losses) : null);
    const division = entry.division ?? team?.division ?? null;
    const league =
      division?.startsWith('AL') ? 'American'
      : division?.startsWith('NL') ? 'National'
      : entry.conference ?? team?.league ?? null;
    return {
      entry,
      division,
      league,
      divisionRank: entry.divisionRank ?? team?.divisionRank ?? null,
      wins,
      losses,
      winPct,
    };
  };

  const organizeLeague = (
    leagueEntries: Enriched[],
    divisions: string[]
  ): PlayoffChanceEntry[] => {
    if (leagueEntries.length === 0) return [];

    const divLeaders = leagueEntries.filter(item => item.divisionRank === 1);
    const nonLeaders = leagueEntries
      .filter(item => (item.divisionRank ?? Number.MAX_SAFE_INTEGER) > 1)
      .sort((a, b) => {
        const pctDiff = (b.winPct ?? 0) - (a.winPct ?? 0);
        if (pctDiff !== 0) return pctDiff;
        const winsDiff = (b.wins ?? 0) - (a.wins ?? 0);
        if (winsDiff !== 0) return winsDiff;
        return (a.losses ?? Number.MAX_SAFE_INTEGER) - (b.losses ?? Number.MAX_SAFE_INTEGER);
      });
    const wcWinners = nonLeaders.slice(0, 3);
    const playoffTeams = [...divLeaders, ...wcWinners].sort((a, b) => {
      const pctDiff = (a.winPct ?? 1) - (b.winPct ?? 1);
      if (pctDiff !== 0) return pctDiff;
      const winsDiff = (a.wins ?? Number.MAX_SAFE_INTEGER) - (b.wins ?? Number.MAX_SAFE_INTEGER);
      if (winsDiff !== 0) return winsDiff;
      return (b.losses ?? 0) - (a.losses ?? 0);
    });
    const cutoff = playoffTeams[0];
    const cutoffW = cutoff?.wins;
    const cutoffL = cutoff?.losses;

    const gamesBackFor = (item: Enriched): number | null => {
      if (cutoffW == null || cutoffL == null || item.wins == null || item.losses == null) return null;
      const gb = mlbGamesBack(item.wins, item.losses, cutoffW, cutoffL);
      return gb <= 0 ? 0 : Math.round(gb * 10) / 10;
    };

    const toEntry = (item: Enriched, section: string): PlayoffChanceEntry => ({
      ...item.entry,
      conference: item.league ?? item.entry.conference,
      division: item.division,
      divisionRank: item.divisionRank,
      wins: item.wins,
      losses: item.losses,
      winPct: item.winPct,
      gamesBackFromPlayoff: gamesBackFor(item),
      standingsSection: section,
    });

    const organized: PlayoffChanceEntry[] = [];
    divisions.forEach(division => {
      leagueEntries
        .filter(item => item.division === division)
        .sort((a, b) => {
          const rankDiff = (a.divisionRank ?? Number.MAX_SAFE_INTEGER) - (b.divisionRank ?? Number.MAX_SAFE_INTEGER);
          if (rankDiff !== 0) return rankDiff;
          const pctDiff = (b.winPct ?? 0) - (a.winPct ?? 0);
          if (pctDiff !== 0) return pctDiff;
          return (b.wins ?? 0) - (a.wins ?? 0);
        })
        .forEach(item => organized.push(toEntry(item, division)));
    });
    return organized;
  };

  const enriched = uniqueEntries.map(enrich);
  return [
    ...organizeLeague(enriched.filter(item => item.league === 'National'), ['NL East', 'NL Central', 'NL West']),
    ...organizeLeague(enriched.filter(item => item.league === 'American'), ['AL East', 'AL Central', 'AL West']),
  ];
}

function buildMlbStandingsConferenceGroups(entries: PlayoffChanceEntry[]) {
  const uniqueEntries = dedupeMlbPlayoffChanceEntries(entries);
  if (!uniqueEntries.some(entry => entry.standingsSection && entry.standingsSection !== 'Wild Card')) {
    return null;
  }

  const grouped = uniqueEntries.reduce<Record<string, PlayoffChanceEntry[]>>((acc, entry) => {
    const conf = entry.conference ?? 'Unknown';
    if (!acc[conf]) acc[conf] = [];
    acc[conf].push(entry);
    return acc;
  }, {});

  return Object.entries(grouped)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([conference, confEntries]) => {
      const sectionOrder = MLB_STANDINGS_SECTION_ORDER[conference]
        ?? [...new Set(confEntries.map(entry => entry.standingsSection).filter(Boolean))] as string[];
      const sections = sectionOrder
        .map(section => {
          const sectionEntries =
            section === 'Wild Card'
              ? wildCardEntriesForConference(confEntries)
              : confEntries
                  .filter(entry => entry.standingsSection === section || entry.division === section)
                  .sort(
                    (a, b) =>
                      (a.divisionRank ?? Number.MAX_SAFE_INTEGER) -
                      (b.divisionRank ?? Number.MAX_SAFE_INTEGER)
                  );
          return sectionEntries.length > 0 ? [section, sectionEntries] as const : null;
        })
        .filter((section): section is readonly [string, PlayoffChanceEntry[]] => section != null);
      return [conference, sections] as const;
    });
}

function RankBadge({
  rank,
  display,
  playerRankings = false,
}: {
  rank: number;
  display?: string | null;
  playerRankings?: boolean;
}) {
  const colorFn = playerRankings ? playerRankColor : teamRankColor;
  return (
    <span
      className={`inline-flex items-center justify-center min-w-8 h-5 px-1 rounded text-[10px] font-bold text-white ${colorFn(rank)}`}
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

  const isPlayerRankings = entriesArePlayerRankings(entries);
  const nameHeader = isPlayerRankings ? 'Player' : 'Team';
  const gridCols = isPlayerRankings
    ? 'grid-cols-[2rem_1fr_4.5rem]'
    : 'grid-cols-[2rem_2.5rem_1fr]';

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={title}
      subtitle={subtitle}
      source={source}
    >
      <div
        className={`grid ${gridCols} gap-x-2 pl-3 pr-1 py-1 text-[10px] font-bold uppercase text-[var(--muted)] border-b border-[var(--border)]`}
      >
        <div className="text-center pl-1">RK</div>
        <div>{nameHeader}</div>
        <div className="text-right">Value</div>
      </div>

      {entries.map((entry, index) => {
        const team = rankingTeamCode(entry);
        const selected = selectedTeams.has(team);
        const nameLabel = isPlayerRankings
          ? `${rankingEntryLabel(entry)} (${team})`
          : team;
        return (
          <button
            key={`${team}-${entry.player ?? ''}-${entry.rank}-${index}`}
            type="button"
            onClick={() => toggleTeam(team)}
            className={`w-full grid ${gridCols} gap-x-2 items-center pl-3 pr-1 py-1.5 text-sm text-left border-b border-[var(--border)] last:border-b-0 transition-colors ${
              selected ? 'bg-[var(--foreground)]/8' : 'hover:bg-[var(--foreground)]/5'
            }`}
          >
            <div className="flex justify-center pl-1">
              <RankBadge
                rank={entry.rank}
                display={entry.rankDisplay}
                playerRankings={isPlayerRankings}
              />
            </div>
            <span
              className={`truncate ${isPlayerRankings ? '' : 'font-mono'} ${
                selected ? 'font-bold' : 'font-medium'
              }`}
            >
              {nameLabel}
            </span>
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

function PlayoffStandingsSection({
  conference,
  sections,
  selectedTeams,
  onToggleTeam,
  wildCardPlayoffCutoff = 3,
}: {
  conference: string;
  sections: ReadonlyArray<readonly [string, PlayoffChanceEntry[]]>;
  selectedTeams: Set<string>;
  onToggleTeam: (team: string) => void;
  wildCardPlayoffCutoff?: number;
}) {
  const showGamesBackColumn = sections.some(([, sectionEntries]) =>
    sectionEntries.some(entry => entry.gamesBackFromPlayoff != null)
  );

  return (
    <div className="mb-4 last:mb-0">
      <div className="text-xs font-bold font-mono uppercase text-[var(--muted)] mb-1">{conference}</div>
      {sections.map(([sectionName, sectionEntries]) => (
        <div key={`${conference}-${sectionName}`} className="mb-3 last:mb-0">
          <div className="text-[11px] font-semibold font-mono text-[var(--muted)] mb-1">{sectionName}</div>
          <div
            className={`grid gap-x-1 pl-3 pr-1 py-1 text-[9px] font-bold uppercase text-[var(--muted)] border-b border-[var(--border)] ${
              showGamesBackColumn
                ? 'grid-cols-[1.25rem_2.5rem_2.25rem_2.25rem_1.5rem_3.25rem_3.25rem]'
                : 'grid-cols-[1.25rem_2.5rem_2.25rem_2.25rem_3.25rem_3.25rem]'
            }`}
          >
            <div className="text-center pl-1">#</div>
            <div>Team</div>
            <div className="text-right">W-L</div>
            <div className="text-right">Win%</div>
            {showGamesBackColumn && <div className="text-right">GB</div>}
            <div className="text-center pl-2">Playoff</div>
            <div className="text-center pl-2">WS</div>
          </div>
          {sectionEntries.map((entry, index) => {
            const seed = sectionName === 'Wild Card' ? index + 1 : entry.divisionRank ?? index + 1;
            const team = rankingTeamCode(entry);
            const selected = selectedTeams.has(team);
            const wl =
              entry.wins != null && entry.losses != null ? `${entry.wins}-${entry.losses}` : '-';

            return (
              <div key={`${conference}-${sectionName}-${team}`}>
                {sectionName === 'Wild Card' &&
                  wildCardPlayoffCutoff > 0 &&
                  seed === wildCardPlayoffCutoff + 1 && <PlayoffCutoffDivider />}
                <button
                  type="button"
                  onClick={() => onToggleTeam(team)}
                  className={`w-full grid gap-x-1 items-center pl-3 pr-1 py-1.5 text-sm text-left border-b border-[var(--border)] last:border-b-0 transition-colors ${
                    showGamesBackColumn
                      ? 'grid-cols-[1.25rem_2.5rem_2.25rem_2.25rem_1.5rem_3.25rem_3.25rem]'
                      : 'grid-cols-[1.25rem_2.5rem_2.25rem_2.25rem_3.25rem_3.25rem]'
                  } ${selected ? 'bg-[var(--foreground)]/8' : 'hover:bg-[var(--foreground)]/5'}`}
                >
                  <span className="text-center font-mono text-xs text-[var(--muted)] pl-1">{seed}</span>
                  <span className={`font-mono ${selected ? 'font-bold' : 'font-medium'}`}>{team}</span>
                  <span className="font-mono text-right text-xs">{wl}</span>
                  <span className="font-mono text-right text-xs">{formatWinPct(entry.winPct ?? null)}</span>
                  {showGamesBackColumn && (
                    <span className="font-mono text-right text-xs">
                      {formatPlayoffGamesBack(
                        displayMlbStandingsGamesBack(sectionName, entry, sectionEntries)
                      )}
                    </span>
                  )}
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
      ))}
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
  teams = {},
  highlightedTeam,
  subtitle = 'Season Projections',
  source = 'PlayoffStatus.com',
  playoffCutoff = 6,
  wildCardPlayoffCutoff = 3,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  entries: PlayoffChanceEntry[];
  teams?: Record<string, ReportCardTeam>;
  highlightedTeam: string;
  subtitle?: string;
  source?: string;
  playoffCutoff?: number;
  wildCardPlayoffCutoff?: number;
}) {
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(() => new Set([highlightedTeam.toUpperCase()]));

  useEffect(() => {
    if (open) {
      setSelectedTeams(new Set([highlightedTeam.toUpperCase()]));
    }
  }, [open, highlightedTeam]);

  const displayEntries = useMemo(
    () => organizeMlbPlayoffChancesForDisplay(entries, teams),
    [entries, teams]
  );

  const standingsConferenceGroups = useMemo(
    () => buildMlbStandingsConferenceGroups(displayEntries),
    [displayEntries]
  );

  const hasConferenceData = displayEntries.some(e => e.conference);

  const conferenceGroups = useMemo(() => {
    if (standingsConferenceGroups || !hasConferenceData) return null;
    const grouped = displayEntries.reduce<Record<string, PlayoffChanceEntry[]>>((acc, entry) => {
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
  }, [displayEntries, hasConferenceData, standingsConferenceGroups]);

  const flatEntries = useMemo(
    () => [...displayEntries].sort((a, b) => (b.playoffProb ?? 0) - (a.playoffProb ?? 0)),
    [displayEntries]
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
      {standingsConferenceGroups ? (
        standingsConferenceGroups.map(([conference, sections]) => (
          <PlayoffStandingsSection
            key={conference}
            conference={conference}
            sections={sections}
            selectedTeams={selectedTeams}
            onToggleTeam={toggleTeam}
            wildCardPlayoffCutoff={wildCardPlayoffCutoff}
          />
        ))
      ) : conferenceGroups ? (
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

export function isReportCardPlayerRankingKey(key: string): boolean {
  return key.includes('.player.');
}

function parseReportCardRankingKey(key: string): {
  categoryKey: string;
  statKey: string;
  isPlayer: boolean;
} | null {
  const playerMarker = '.player.';
  const playerIndex = key.indexOf(playerMarker);
  if (playerIndex > 0) {
    return {
      categoryKey: key.slice(0, playerIndex),
      statKey: key.slice(playerIndex + playerMarker.length),
      isPlayer: true,
    };
  }
  const dotIndex = key.indexOf('.');
  if (dotIndex > 0) {
    return {
      categoryKey: key.slice(0, dotIndex),
      statKey: key.slice(dotIndex + 1),
      isPlayer: false,
    };
  }
  return null;
}

export function formatReportCardRankingLabel(seasonLabel: string, key: string): string {
  const parsed = parseReportCardRankingKey(key);
  if (parsed) {
    const categoryLabel = formatReportCardCategoryLabel(parsed.categoryKey);
    const statLabel = formatReportCardStatLabel(parsed.categoryKey, parsed.statKey);
    const suffix = parsed.isPlayer ? ' / Players' : '';
    return `${seasonLabel} / ${categoryLabel} / ${statLabel}${suffix}`;
  }

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
    case 'injuriesComposite':
      return `${seasonLabel} / Injury Report Composite`;
    default:
      return `${seasonLabel} / ${key}`;
  }
}

function formatReportCardCategoryLabel(categoryKey: string): string {
  switch (categoryKey) {
    case 'hitters':
      return 'Hitters';
    case 'starters':
      return 'Starting Pitchers';
    case 'relievers':
      return 'Bullpen';
    case 'fielders':
      return 'Fielders';
    case 'injuries':
      return 'Injury Report';
    default:
      return categoryKey;
  }
}

function formatReportCardStatLabel(categoryKey: string, statKey: string): string {
  switch (categoryKey) {
    case 'hitters':
      switch (statKey) {
        case 'wRC_plus':
          return 'wRC+';
        case 'xwOBA':
          return 'xwOBA';
        case 'xBA':
          return 'xBA';
        case 'Barrel_pct':
          return 'Barrel%';
        default:
          return statKey;
      }
    case 'starters':
      switch (statKey) {
        case 'K-BB_pct':
          return 'K-BB%';
        case 'xFIP':
          return 'xFIP';
        case 'SIERA':
          return 'SIERA';
        case 'ERA':
          return 'ERA';
        default:
          return statKey;
      }
    case 'relievers':
      switch (statKey) {
        case 'K-BB_pct':
          return 'K-BB%';
        case 'FIP':
          return 'FIP';
        case 'SV_per_G':
          return 'SV/G';
        case 'SIERA':
          return 'SIERA';
        case 'ERA':
          return 'ERA';
        default:
          return statKey;
      }
    case 'fielders':
      switch (statKey) {
        case 'OAA':
          return 'OAA';
        case 'DRS':
          return 'DRS';
        case 'FRP':
          return 'FRP';
        default:
          return statKey;
      }
    case 'injuries':
      switch (statKey) {
        case 'injured_count':
          return 'Injured';
        case 'injury_war':
          return 'WAR Lost';
        default:
          return statKey;
      }
    default:
      return statKey;
  }
}

export function isReportCardRankingPct(key: string): boolean {
  return key === 'record' || key.endsWith('.K-BB_pct') || key.endsWith('.Barrel_pct');
}
