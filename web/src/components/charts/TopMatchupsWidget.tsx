'use client';

import { NBAMatchupData, NBAMatchupDataPoint, NHLMatchupData, NHLMatchupDataPoint } from '@/types/chart';

type AnyMatchupWidgetData = NBAMatchupData | NHLMatchupData;

interface Props {
  data: AnyMatchupWidgetData;
}

function getDaysFromNow(gameDate: string): number {
  const now = new Date();
  const game = new Date(gameDate);

  // Normalize to start of day in local timezone
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const gameStart = new Date(game.getFullYear(), game.getMonth(), game.getDate());

  return Math.round((gameStart.getTime() - todayStart.getTime()) / (1000 * 60 * 60 * 24));
}

function getRelativeDayLabel(gameDate: string): string {
  const diffDays = getDaysFromNow(gameDate);

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Tomorrow';
  if (diffDays === 2) return '2 Days';
  if (diffDays === 3) return '3 Days';

  // Fallback to date format for games further out
  const game = new Date(gameDate);
  return game.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
}

function getDayKey(gameDate: string): string {
  const game = new Date(gameDate);
  return `${game.getFullYear()}-${game.getMonth()}-${game.getDate()}`;
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
          <span className={`font-mono w-8 text-right ${matchup.awayTeam.stats.netRating >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {matchup.awayTeam.stats.netRating >= 0 ? '+' : ''}{matchup.awayTeam.stats.netRating.toFixed(1)}
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
          <span className={`font-mono w-8 text-right ${matchup.homeTeam.stats.netRating >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {matchup.homeTeam.stats.netRating >= 0 ? '+' : ''}{matchup.homeTeam.stats.netRating.toFixed(1)}
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

export function TopMatchupsWidget({ data }: Props) {
  if (data.visualizationType === 'NBA_MATCHUP') {
    const nbaData = data as NBAMatchupData;
    // Filter to upcoming games within 4 days (today, tomorrow, 2 days, 3 days)
    const upcomingGames = nbaData.dataPoints.filter(m => {
      if (m.gameCompleted) return false;
      const days = getDaysFromNow(m.gameDate);
      return days >= 0 && days <= 3;
    });

    // Group by day
    const gamesByDay = new Map<string, NBAMatchupDataPoint[]>();
    for (const game of upcomingGames) {
      const dayKey = getDayKey(game.gameDate);
      if (!gamesByDay.has(dayKey)) {
        gamesByDay.set(dayKey, []);
      }
      gamesByDay.get(dayKey)!.push(game);
    }

    // Sort each day's games by net rating and take top 2
    const topMatchups: { matchup: NBAMatchupDataPoint; dayLabel: string }[] = [];

    // Sort days chronologically
    const sortedDays = [...gamesByDay.entries()].sort((a, b) => {
      const dateA = new Date(a[1][0].gameDate);
      const dateB = new Date(b[1][0].gameDate);
      return dateA.getTime() - dateB.getTime();
    });

    for (const [, games] of sortedDays) {
      const sortedGames = [...games].sort((a, b) => {
        const totalA = a.homeTeam.stats.netRating + a.awayTeam.stats.netRating;
        const totalB = b.homeTeam.stats.netRating + b.awayTeam.stats.netRating;
        return totalB - totalA;
      });

      const top2 = sortedGames.slice(0, 2);
      for (const game of top2) {
        topMatchups.push({
          matchup: game,
          dayLabel: getRelativeDayLabel(game.gameDate),
        });
      }
    }

    if (topMatchups.length === 0) return null;

    return (
      <div className="grid grid-cols-2 gap-2">
        {topMatchups.map(({ matchup, dayLabel }) => (
          <NBAMatchupCard key={matchup.gameId} matchup={matchup} dayLabel={dayLabel} />
        ))}
      </div>
    );
  }

  if (data.visualizationType === 'NHL_MATCHUP') {
    const nhlData = data as NHLMatchupData;
    // Filter to upcoming games within 4 days (today, tomorrow, 2 days, 3 days)
    const upcomingGames = nhlData.dataPoints.filter(m => {
      if (m.gameCompleted) return false;
      const days = getDaysFromNow(m.gameDate);
      return days >= 0 && days <= 3;
    });

    // Group by day
    const gamesByDay = new Map<string, NHLMatchupDataPoint[]>();
    for (const game of upcomingGames) {
      const dayKey = getDayKey(game.gameDate);
      if (!gamesByDay.has(dayKey)) {
        gamesByDay.set(dayKey, []);
      }
      gamesByDay.get(dayKey)!.push(game);
    }

    // Sort each day's games by goal diff and take top 2
    const topMatchups: { matchup: NHLMatchupDataPoint; dayLabel: string }[] = [];

    // Sort days chronologically
    const sortedDays = [...gamesByDay.entries()].sort((a, b) => {
      const dateA = new Date(a[1][0].gameDate);
      const dateB = new Date(b[1][0].gameDate);
      return dateA.getTime() - dateB.getTime();
    });

    for (const [, games] of sortedDays) {
      const sortedGames = [...games].sort((a, b) => {
        const totalA = a.homeTeam.stats.goalDiffPerGame + a.awayTeam.stats.goalDiffPerGame;
        const totalB = b.homeTeam.stats.goalDiffPerGame + b.awayTeam.stats.goalDiffPerGame;
        return totalB - totalA;
      });

      const top2 = sortedGames.slice(0, 2);
      for (const game of top2) {
        topMatchups.push({
          matchup: game,
          dayLabel: getRelativeDayLabel(game.gameDate),
        });
      }
    }

    if (topMatchups.length === 0) return null;

    return (
      <div className="grid grid-cols-2 gap-2">
        {topMatchups.map(({ matchup, dayLabel }) => (
          <NHLMatchupCard key={matchup.gameId} matchup={matchup} dayLabel={dayLabel} />
        ))}
      </div>
    );
  }

  return null;
}
