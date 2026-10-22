'use client';

import Link from 'next/link';
import { useMemo } from 'react';
import { NFLMatchupDataPoint } from '@/types/chart';
import { usePinnedTeams } from '@/lib/usePinnedTeams';

interface WeekGroup {
  key: string;
  label: string;
  games: NFLMatchupDataPoint[];
}

// Football is played in weeks, not days, so the top row is the week and the
// row beneath it the games inside it - a Thursday night game and the Sunday
// slate belong together.
function getWeekKey(game: NFLMatchupDataPoint): string {
  const type = game.seasonType ?? 'REG';
  return `${game.season ?? ''}-${type}-${game.week ?? 0}`;
}

function formatWeekLabel(game: NFLMatchupDataPoint): string {
  if (game.seasonType && game.seasonType !== 'REG') {
    return game.seasonTypeLabel ?? game.seasonType;
  }
  return game.week != null ? `Week ${game.week}` : 'Week';
}

function groupGamesByWeek(games: NFLMatchupDataPoint[]): WeekGroup[] {
  const groups = new Map<string, WeekGroup>();

  const sortedGames = [...games].sort(
    (a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime()
  );

  for (const game of sortedGames) {
    const key = getWeekKey(game);
    if (!groups.has(key)) {
      groups.set(key, { key, label: formatWeekLabel(game), games: [] });
    }
    groups.get(key)!.games.push(game);
  }

  return Array.from(groups.values());
}

function gameIncludesTeam(game: NFLMatchupDataPoint, teamCode: string): boolean {
  const code = teamCode.toUpperCase();
  return (
    game.awayTeam.abbreviation.toUpperCase() === code ||
    game.homeTeam.abbreviation.toUpperCase() === code
  );
}

function preferredGameForWeek(
  group: WeekGroup,
  pinnedTeamCodes: string[]
): NFLMatchupDataPoint | undefined {
  for (const teamCode of pinnedTeamCodes) {
    const match = group.games.find(game => gameIncludesTeam(game, teamCode));
    if (match) return match;
  }
  return group.games[0];
}

interface NFLMatchupNavProps {
  games: NFLMatchupDataPoint[];
  selectedGameId: string;
}

export function NFLMatchupNav({ games, selectedGameId }: NFLMatchupNavProps) {
  const { getPinnedForSport, mounted } = usePinnedTeams();
  const pinnedNflTeamCodes = useMemo(
    () => (mounted ? getPinnedForSport('nfl').map(team => team.teamCode) : []),
    [getPinnedForSport, mounted]
  );

  const weekGroups = groupGamesByWeek(games);

  const selectedGame = games.find(g => g.gameId === selectedGameId);
  const selectedWeekKey = selectedGame ? getWeekKey(selectedGame) : null;
  const selectedWeekGroup = weekGroups.find(g => g.key === selectedWeekKey);

  const badgeClasses = (isActive: boolean) =>
    `px-3 py-1 text-xs font-medium rounded-full whitespace-nowrap transition-colors border ${
      isActive
        ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
        : 'bg-white dark:bg-white/20 text-black dark:text-[var(--muted)] border-gray-200 dark:border-transparent hover:bg-gray-100 dark:hover:bg-white/30 dark:hover:text-[var(--foreground)]'
    }`;

  return (
    <nav className="py-1">
      <div className="overflow-x-auto scrollbar-hide">
        <div className="flex gap-2 pb-2">
          {weekGroups.map(group => {
            const targetGame = preferredGameForWeek(group, pinnedNflTeamCodes);
            if (!targetGame) return null;

            return (
              <Link
                key={group.key}
                href={`/nfl/matchups/${targetGame.gameId}`}
                className={badgeClasses(group.key === selectedWeekKey)}
              >
                {group.label} ({group.games.length})
              </Link>
            );
          })}
        </div>
      </div>

      {selectedWeekGroup && (
        <div className="overflow-x-auto scrollbar-hide">
          <div className="flex gap-2 pb-2">
            {selectedWeekGroup.games.map(game => (
              <Link
                key={game.gameId}
                href={`/nfl/matchups/${game.gameId}`}
                className={badgeClasses(game.gameId === selectedGameId)}
              >
                {game.awayTeam.abbreviation} @ {game.homeTeam.abbreviation}
              </Link>
            ))}
          </div>
        </div>
      )}
    </nav>
  );
}
