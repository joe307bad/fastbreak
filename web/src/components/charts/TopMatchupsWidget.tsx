'use client';

import { MLBMatchupData, MLBMatchupDataPoint, NBAMatchupData, NBAMatchupDataPoint, NHLMatchupData, NHLMatchupDataPoint } from '@/types/chart';
import { formatRunDiff, getLeagueAbbrev, getRecordRank, getRunDiffPerGame } from '@/lib/mlbStats';

type AnyMatchupWidgetData = NBAMatchupData | NHLMatchupData | MLBMatchupData;

interface Props {
  data: AnyMatchupWidgetData;
  selectedGameIds: string[];
}

function getDayLabel(gameDate: string): string {
  const datePart = gameDate.slice(0, 10);
  const [y, m, d] = datePart.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

function getConferenceAbbrev(conference: string): string {
  const lower = conference.toLowerCase();
  if (lower.includes('east')) return 'E';
  if (lower.includes('west')) return 'W';
  return conference.slice(0, 1).toUpperCase();
}

// NBA Matchup Card
function NBAMatchupCard({ matchup, dayLabel }: { matchup: NBAMatchupDataPoint; dayLabel: string }) {
  const totalNetRating = matchup.homeTeam.stats.netRating + matchup.awayTeam.stats.netRating;

  return (
    <div className="border border-[var(--border)] rounded p-2 bg-[var(--card)] text-xs">
      {/* Header */}
      <div className="flex justify-between items-center text-[10px] text-[var(--muted)] mb-1.5">
        <span>{dayLabel}</span>
        <div className="flex items-center gap-2">
          <span>Rank</span>
          <span className="w-8 text-right">Net Rtg</span>
        </div>
      </div>

      {/* Away Team */}
      <div className="flex items-center justify-between mb-1">
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--muted)] w-3">
            {getConferenceAbbrev(matchup.awayTeam.conference)}
          </span>
          <span className="font-bold">{matchup.awayTeam.abbreviation}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[var(--muted)]">#{matchup.awayTeam.conferenceRank}</span>
          <span className={`font-mono w-8 text-right ${(matchup.awayTeam.stats.netRating ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {matchup.awayTeam.stats.netRating != null ? `${matchup.awayTeam.stats.netRating >= 0 ? '+' : ''}${matchup.awayTeam.stats.netRating.toFixed(1)}` : '-'}
          </span>
        </div>
      </div>

      {/* Home Team */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--muted)] w-3">
            {getConferenceAbbrev(matchup.homeTeam.conference)}
          </span>
          <span className="font-bold">{matchup.homeTeam.abbreviation}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[var(--muted)]">#{matchup.homeTeam.conferenceRank}</span>
          <span className={`font-mono w-8 text-right ${(matchup.homeTeam.stats.netRating ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {matchup.homeTeam.stats.netRating != null ? `${matchup.homeTeam.stats.netRating >= 0 ? '+' : ''}${matchup.homeTeam.stats.netRating.toFixed(1)}` : '-'}
          </span>
        </div>
      </div>

      {/* Total */}
      <div className="flex justify-end mt-1.5 pt-1.5 border-t border-[var(--border)]">
        <span className="text-[10px] text-[var(--muted)] mr-1">Total:</span>
        <span className="font-mono text-[10px]">+{totalNetRating.toFixed(1)}</span>
      </div>
    </div>
  );
}

// NHL Matchup Card
function NHLMatchupCard({ matchup, dayLabel }: { matchup: NHLMatchupDataPoint; dayLabel: string }) {
  const totalGoalDiff = matchup.homeTeam.stats.goalDiffPerGame + matchup.awayTeam.stats.goalDiffPerGame;

  return (
    <div className="border border-[var(--border)] rounded p-2 bg-[var(--card)] text-xs">
      {/* Header */}
      <div className="flex justify-between items-center text-[10px] text-[var(--muted)] mb-1.5">
        <span>{dayLabel}</span>
        <div className="flex items-center gap-2">
          <span>Rank</span>
          <span className="w-8 text-right">GD/G</span>
        </div>
      </div>

      {/* Away Team */}
      <div className="flex items-center justify-between mb-1">
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--muted)] w-3">
            {getConferenceAbbrev(matchup.awayTeam.conference)}
          </span>
          <span className="font-bold">{matchup.awayTeam.abbreviation}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[var(--muted)]">#{matchup.awayTeam.conferenceRank}</span>
          <span className={`font-mono w-8 text-right ${matchup.awayTeam.stats.goalDiffPerGame >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {matchup.awayTeam.stats.goalDiffPerGame >= 0 ? '+' : ''}{matchup.awayTeam.stats.goalDiffPerGame.toFixed(2)}
          </span>
        </div>
      </div>

      {/* Home Team */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--muted)] w-3">
            {getConferenceAbbrev(matchup.homeTeam.conference)}
          </span>
          <span className="font-bold">{matchup.homeTeam.abbreviation}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[var(--muted)]">#{matchup.homeTeam.conferenceRank}</span>
          <span className={`font-mono w-8 text-right ${matchup.homeTeam.stats.goalDiffPerGame >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {matchup.homeTeam.stats.goalDiffPerGame >= 0 ? '+' : ''}{matchup.homeTeam.stats.goalDiffPerGame.toFixed(2)}
          </span>
        </div>
      </div>

      {/* Total */}
      <div className="flex justify-end mt-1.5 pt-1.5 border-t border-[var(--border)]">
        <span className="text-[10px] text-[var(--muted)] mr-1">Total:</span>
        <span className="font-mono text-[10px]">{totalGoalDiff >= 0 ? '+' : ''}{totalGoalDiff.toFixed(2)}</span>
      </div>
    </div>
  );
}

function MLBMatchupCard({ matchup, dayLabel }: { matchup: MLBMatchupDataPoint; dayLabel: string }) {
  const awayRunDiff = getRunDiffPerGame(matchup.awayTeam.stats);
  const homeRunDiff = getRunDiffPerGame(matchup.homeTeam.stats);
  const totalRunDiff = (awayRunDiff?.value ?? 0) + (homeRunDiff?.value ?? 0);

  return (
    <div className="border border-[var(--border)] rounded p-2 bg-[var(--card)] text-xs">
      <div className="flex justify-between items-center text-[10px] text-[var(--muted)] mb-1.5">
        <span>{dayLabel}</span>
        <div className="flex items-center gap-2">
          <span>Rank</span>
          <span className="w-10 text-right">RD/G</span>
        </div>
      </div>

      <div className="flex items-center justify-between mb-1">
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--muted)] w-3">
            {getLeagueAbbrev(matchup.awayTeam.league)}
          </span>
          <span className="font-bold">{matchup.awayTeam.abbreviation}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[var(--muted)]">
            #{getRecordRank(matchup.awayTeam.stats.monthTrend) ?? '-'}
          </span>
          <span className={`font-mono w-10 text-right ${(awayRunDiff?.value ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {formatRunDiff(awayRunDiff?.value)}
          </span>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--muted)] w-3">
            {getLeagueAbbrev(matchup.homeTeam.league)}
          </span>
          <span className="font-bold">{matchup.homeTeam.abbreviation}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[var(--muted)]">
            #{getRecordRank(matchup.homeTeam.stats.monthTrend) ?? '-'}
          </span>
          <span className={`font-mono w-10 text-right ${(homeRunDiff?.value ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {formatRunDiff(homeRunDiff?.value)}
          </span>
        </div>
      </div>

      <div className="flex justify-end mt-1.5 pt-1.5 border-t border-[var(--border)]">
        <span className="text-[10px] text-[var(--muted)] mr-1">Total:</span>
        <span className="font-mono text-[10px]">{formatRunDiff(totalRunDiff)}</span>
      </div>
    </div>
  );
}

function resolveSelectedMatchups<T extends { gameId: string }>(
  selectedGameIds: string[],
  byId: Map<string, T>
): T[] {
  const seen = new Set<string>();
  const matchups: T[] = [];

  for (const id of selectedGameIds) {
    if (seen.has(id)) continue;
    const matchup = byId.get(id);
    if (!matchup) continue;
    seen.add(id);
    matchups.push(matchup);
  }

  return matchups;
}

export function TopMatchupsWidget({ data, selectedGameIds }: Props) {
  if (selectedGameIds.length === 0) return null;

  if (data.visualizationType === 'NBA_MATCHUP') {
    const byId = new Map<string, NBAMatchupDataPoint>(
      (data as NBAMatchupData).dataPoints.map(g => [g.gameId, g])
    );
    const matchups = resolveSelectedMatchups(selectedGameIds, byId);
    if (matchups.length === 0) return null;

    return (
      <div className="grid grid-cols-2 gap-2">
        {matchups.map(matchup => (
          <NBAMatchupCard
            key={matchup.gameId}
            matchup={matchup}
            dayLabel={getDayLabel(matchup.gameDate)}
          />
        ))}
      </div>
    );
  }

  if (data.visualizationType === 'NHL_MATCHUP') {
    const byId = new Map<string, NHLMatchupDataPoint>(
      (data as NHLMatchupData).dataPoints.map(g => [g.gameId, g])
    );
    const matchups = resolveSelectedMatchups(selectedGameIds, byId);
    if (matchups.length === 0) return null;

    return (
      <div className="grid grid-cols-2 gap-2">
        {matchups.map(matchup => (
          <NHLMatchupCard
            key={matchup.gameId}
            matchup={matchup}
            dayLabel={getDayLabel(matchup.gameDate)}
          />
        ))}
      </div>
    );
  }

  if (data.visualizationType === 'MLB_MATCHUP') {
    const byId = new Map<string, MLBMatchupDataPoint>(
      (data as MLBMatchupData).dataPoints.map(g => [g.gameId, g])
    );
    const matchups = resolveSelectedMatchups(selectedGameIds, byId);
    if (matchups.length === 0) return null;

    return (
      <div className="grid grid-cols-2 gap-2">
        {matchups.map(matchup => (
          <MLBMatchupCard
            key={matchup.gameId}
            matchup={matchup}
            dayLabel={getDayLabel(matchup.gameDate)}
          />
        ))}
      </div>
    );
  }

  return null;
}
