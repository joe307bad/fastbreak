'use client';

import { useEffect, useRef } from 'react';
import Link from 'next/link';
import { NHLMatchupDataPoint } from '@/types/chart';

interface DayGroup {
  dateKey: string;
  label: string;
  games: NHLMatchupDataPoint[];
}

function getDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDayLabel(date: Date): string {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const targetDate = new Date(date);
  targetDate.setHours(0, 0, 0, 0);

  const diffDays = Math.floor((targetDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Tomorrow';
  if (diffDays === -1) return 'Yesterday';

  return date.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
}

function groupGamesByDay(games: NHLMatchupDataPoint[]): DayGroup[] {
  const groups: Map<string, DayGroup> = new Map();

  const sortedGames = [...games].sort(
    (a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime()
  );

  for (const game of sortedGames) {
    const date = new Date(game.gameDate);
    const dateKey = getDateKey(date);

    if (!groups.has(dateKey)) {
      groups.set(dateKey, {
        dateKey,
        label: formatDayLabel(date),
        games: [],
      });
    }

    groups.get(dateKey)!.games.push(game);
  }

  return Array.from(groups.values());
}

interface NHLMatchupNavProps {
  games: NHLMatchupDataPoint[];
  selectedGameId: string;
}

export function NHLMatchupNav({ games, selectedGameId }: NHLMatchupNavProps) {
  const dayGroups = groupGamesByDay(games);
  const selectedDayRef = useRef<HTMLAnchorElement>(null);

  const selectedGame = games.find(g => g.gameId === selectedGameId);
  const selectedDayKey = selectedGame ? getDateKey(new Date(selectedGame.gameDate)) : null;
  const selectedDayGroup = dayGroups.find(g => g.dateKey === selectedDayKey);

  useEffect(() => {
    if (selectedDayRef.current) {
      selectedDayRef.current.scrollIntoView({ behavior: 'instant', block: 'nearest', inline: 'center' });
    }
  }, [selectedGameId]);

  const dayBadgeClasses = (isActive: boolean) =>
    `px-3 py-1 text-xs font-medium rounded-full whitespace-nowrap transition-colors border ${
      isActive
        ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
        : 'bg-white dark:bg-white/20 text-black dark:text-[var(--muted)] border-gray-200 dark:border-transparent hover:bg-gray-100 dark:hover:bg-white/30 dark:hover:text-[var(--foreground)]'
    }`;

  const gameBadgeClasses = (isActive: boolean) =>
    `px-3 py-1 text-xs font-medium rounded-full whitespace-nowrap transition-colors border ${
      isActive
        ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
        : 'bg-white dark:bg-white/20 text-black dark:text-[var(--muted)] border-gray-200 dark:border-transparent hover:bg-gray-100 dark:hover:bg-white/30 dark:hover:text-[var(--foreground)]'
    }`;

  return (
    <nav className="mb-4 sticky top-10 z-30 bg-[var(--background)] py-1">
      {/* Days Row */}
      <div className="overflow-x-auto scrollbar-hide">
        <div className="flex gap-2 pb-2">
          {dayGroups.map(group => {
            const isSelected = group.dateKey === selectedDayKey;
            const firstGameOfDay = group.games[0];
            return (
              <Link
                key={group.dateKey}
                ref={isSelected ? selectedDayRef : undefined}
                href={`/nhl/matchups/${firstGameOfDay.gameId}`}
                className={dayBadgeClasses(isSelected)}
              >
                {group.label} ({group.games.length})
              </Link>
            );
          })}
        </div>
      </div>

      {/* Games Row for selected day */}
      {selectedDayGroup && (
        <div className="overflow-x-auto scrollbar-hide">
          <div className="flex gap-2 pb-2">
            {selectedDayGroup.games.map(game => (
              <Link
                key={game.gameId}
                href={`/nhl/matchups/${game.gameId}`}
                className={gameBadgeClasses(game.gameId === selectedGameId)}
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
