'use client';

import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import { MatchupData, NBAMatchupData, NBAMatchupDataPoint, NHLMatchupData, NHLMatchupDataPoint, MatchupV2Data, MatchupV2DataPoint } from '@/types/chart';

type AnyMatchupData = MatchupData | NBAMatchupData | NHLMatchupData | MatchupV2Data;

interface MatchupNavProps {
  data: AnyMatchupData;
  selectedDay: string | null;
  selectedMatchup: string | null;
  onDaySelect: (day: string | null) => void;
  onMatchupSelect: (matchupId: string | null) => void;
}

interface DayGroup {
  dateKey: string;
  label: string;
  matchups: { id: string; label: string }[];
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

function getDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function groupMatchupsByDay(data: AnyMatchupData): DayGroup[] {
  const groups: Map<string, DayGroup> = new Map();

  if (data.visualizationType === 'NBA_MATCHUP') {
    const nbaData = data as NBAMatchupData;
    const sortedGames = [...nbaData.dataPoints]
      .sort((a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime());

    for (const matchup of sortedGames) {
      const date = new Date(matchup.gameDate);
      const dateKey = getDateKey(date);

      if (!groups.has(dateKey)) {
        groups.set(dateKey, {
          dateKey,
          label: formatDayLabel(date),
          matchups: [],
        });
      }

      groups.get(dateKey)!.matchups.push({
        id: matchup.gameId,
        label: `${matchup.awayTeam.abbreviation} @ ${matchup.homeTeam.abbreviation}`,
      });
    }
  } else if (data.visualizationType === 'NHL_MATCHUP') {
    const nhlData = data as NHLMatchupData;
    const sortedGames = [...nhlData.dataPoints]
      .sort((a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime());

    for (const matchup of sortedGames) {
      const date = new Date(matchup.gameDate);
      const dateKey = getDateKey(date);

      if (!groups.has(dateKey)) {
        groups.set(dateKey, {
          dateKey,
          label: formatDayLabel(date),
          matchups: [],
        });
      }

      groups.get(dateKey)!.matchups.push({
        id: matchup.gameId,
        label: `${matchup.awayTeam.abbreviation} @ ${matchup.homeTeam.abbreviation}`,
      });
    }
  } else if (data.visualizationType === 'MATCHUP_V2') {
    const v2Data = data as MatchupV2Data;
    const dataPointsObj = v2Data.dataPoints as unknown as Record<string, MatchupV2DataPoint>;
    const matchupEntries = Object.entries(dataPointsObj)
      .sort(([, a], [, b]) => new Date(a.game_datetime).getTime() - new Date(b.game_datetime).getTime());

    for (const [key, matchup] of matchupEntries) {
      const date = new Date(matchup.game_datetime);
      const dateKey = getDateKey(date);
      const [awayCode, homeCode] = key.split('-').map(s => s.toUpperCase());

      if (!groups.has(dateKey)) {
        groups.set(dateKey, {
          dateKey,
          label: formatDayLabel(date),
          matchups: [],
        });
      }

      groups.get(dateKey)!.matchups.push({
        id: key,
        label: `${awayCode} @ ${homeCode}`,
      });
    }
  } else {
    // Legacy MATCHUP type
    const legacyData = data as MatchupData;
    const sortedMatchups = [...legacyData.dataPoints]
      .sort((a, b) => new Date(a.gameTime).getTime() - new Date(b.gameTime).getTime());

    for (let i = 0; i < sortedMatchups.length; i++) {
      const matchup = sortedMatchups[i];
      const date = new Date(matchup.gameTime);
      const dateKey = getDateKey(date);

      if (!groups.has(dateKey)) {
        groups.set(dateKey, {
          dateKey,
          label: formatDayLabel(date),
          matchups: [],
        });
      }

      groups.get(dateKey)!.matchups.push({
        id: `legacy-${i}`,
        label: `${matchup.awayTeam} @ ${matchup.homeTeam}`,
      });
    }
  }

  return Array.from(groups.values());
}

export function MatchupNav({
  data,
  selectedDay,
  selectedMatchup,
  onDaySelect,
  onMatchupSelect,
}: MatchupNavProps) {
  const daysContainerRef = useRef<HTMLDivElement>(null);
  const dayGroups = useMemo(() => groupMatchupsByDay(data), [data]);

  // Default to first day if none selected
  const effectiveDay = selectedDay || dayGroups[0]?.dateKey || null;
  const currentDayMatchups = dayGroups.find(g => g.dateKey === effectiveDay)?.matchups || [];

  // Scroll to selected day on mount
  useEffect(() => {
    if (effectiveDay && daysContainerRef.current) {
      const selectedButton = daysContainerRef.current.querySelector(`[data-date="${effectiveDay}"]`);
      if (selectedButton) {
        selectedButton.scrollIntoView({ inline: 'center', block: 'nearest' });
      }
    }
  }, []);

  const handleDayClick = useCallback((dateKey: string) => {
    if (dateKey === effectiveDay) {
      // Clicking same day clears selection
      onDaySelect(null);
      onMatchupSelect(null);
    } else {
      onDaySelect(dateKey);
      onMatchupSelect(null);
    }
  }, [effectiveDay, onDaySelect, onMatchupSelect]);

  const handleMatchupClick = useCallback((matchupId: string) => {
    if (matchupId === selectedMatchup) {
      onMatchupSelect(null);
    } else {
      onMatchupSelect(matchupId);
    }
  }, [selectedMatchup, onMatchupSelect]);

  if (dayGroups.length === 0) return null;

  const badgeClasses = (isActive: boolean) =>
    `px-3 py-1 text-xs font-medium rounded-full whitespace-nowrap transition-colors border ${
      isActive
        ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
        : 'bg-white dark:bg-white/20 text-black dark:text-[var(--muted)] border-gray-200 dark:border-transparent hover:bg-gray-100 dark:hover:bg-white/30 dark:hover:text-[var(--foreground)]'
    }`;

  return (
    <nav className="mb-4 sticky top-10 z-30 bg-[var(--background)] py-1">
      {/* Days Row */}
      <div ref={daysContainerRef} className="overflow-x-auto scrollbar-hide">
        <div className="flex gap-2 pb-2">
          {dayGroups.map(group => (
            <button
              key={group.dateKey}
              data-date={group.dateKey}
              onClick={() => handleDayClick(group.dateKey)}
              className={badgeClasses(effectiveDay === group.dateKey)}
            >
              {group.label} ({group.matchups.length})
            </button>
          ))}
        </div>
      </div>

      {/* Matchups Row */}
      {currentDayMatchups.length > 0 && (
        <div className="overflow-x-auto scrollbar-hide">
          <div className="flex gap-2 pb-2">
            {currentDayMatchups.map(matchup => (
              <button
                key={matchup.id}
                onClick={() => handleMatchupClick(matchup.id)}
                className={badgeClasses(selectedMatchup === matchup.id)}
              >
                {matchup.label}
              </button>
            ))}
          </div>
        </div>
      )}
    </nav>
  );
}

// Export helper to get filtered matchups based on selection
export function getFilteredMatchups(
  data: AnyMatchupData,
  selectedDay: string | null,
  selectedMatchup: string | null
): { matchupIds: string[]; showAll: boolean } {
  if (selectedMatchup) {
    return { matchupIds: [selectedMatchup], showAll: false };
  }

  if (!selectedDay) {
    return { matchupIds: [], showAll: true };
  }

  // Get all matchup IDs for the selected day
  const dayGroups = groupMatchupsByDay(data);
  const dayGroup = dayGroups.find(g => g.dateKey === selectedDay);

  if (!dayGroup) {
    return { matchupIds: [], showAll: true };
  }

  return { matchupIds: dayGroup.matchups.map(m => m.id), showAll: false };
}
