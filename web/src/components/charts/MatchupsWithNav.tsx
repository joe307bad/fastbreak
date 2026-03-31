'use client';

import { useState, useMemo } from 'react';
import { MatchupData, NBAMatchupData, NBAMatchupDataPoint, NHLMatchupData, NHLMatchupDataPoint, MatchupV2Data, MatchupV2DataPoint } from '@/types/chart';
import { MatchupNav, getFilteredMatchups } from '@/components/ui/MatchupNav';

type AnyMatchupData = MatchupData | NBAMatchupData | NHLMatchupData | MatchupV2Data;

interface Props {
  data: AnyMatchupData;
}

function formatGameTime(gameTime: string): { date: string; time: string } {
  const d = new Date(gameTime);
  return {
    date: d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' }),
    time: d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }),
  };
}

function getConferenceAbbrev(conference: string): string {
  const lower = conference.toLowerCase();
  if (lower.includes('east')) return 'E';
  if (lower.includes('west')) return 'W';
  if (lower.includes('afc')) return 'AFC';
  if (lower.includes('nfc')) return 'NFC';
  return conference.slice(0, 3).toUpperCase();
}

function getTodayKey(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// NBA Matchup Card
function NBAMatchupCard({ matchup, expanded }: { matchup: NBAMatchupDataPoint; expanded?: boolean }) {
  const { date, time } = formatGameTime(matchup.gameDate);
  const isCompleted = matchup.gameCompleted;

  return (
    <div className={`border border-[var(--border)] rounded p-3 bg-[var(--card)] ${expanded ? 'ring-2 ring-[var(--foreground)]/20' : ''}`}>
      {/* Header: Date/Time and Status */}
      <div className="flex justify-between items-center text-xs text-[var(--muted)] mb-2">
        <span>{date}</span>
        <span>{isCompleted ? 'Final' : time}</span>
      </div>

      {/* Teams */}
      <div className="space-y-2">
        {/* Away Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--muted)] w-4">
              {getConferenceAbbrev(matchup.awayTeam.conference)}
            </span>
            <span className="font-bold">{matchup.awayTeam.abbreviation}</span>
            <span className="text-xs text-[var(--muted)]">
              ({matchup.awayTeam.wins}-{matchup.awayTeam.losses})
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-[var(--muted)]">#{matchup.awayTeam.conferenceRank}</span>
            <span className={`font-mono w-10 text-right ${matchup.awayTeam.stats.netRating >= 0 ? 'text-green-500' : 'text-red-500'}`}>
              {matchup.awayTeam.stats.netRating >= 0 ? '+' : ''}{matchup.awayTeam.stats.netRating.toFixed(1)}
            </span>
          </div>
        </div>

        {/* Home Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--muted)] w-4">
              {getConferenceAbbrev(matchup.homeTeam.conference)}
            </span>
            <span className="font-bold">{matchup.homeTeam.abbreviation}</span>
            <span className="text-xs text-[var(--muted)]">
              ({matchup.homeTeam.wins}-{matchup.homeTeam.losses})
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-[var(--muted)]">#{matchup.homeTeam.conferenceRank}</span>
            <span className={`font-mono w-10 text-right ${matchup.homeTeam.stats.netRating >= 0 ? 'text-green-500' : 'text-red-500'}`}>
              {matchup.homeTeam.stats.netRating >= 0 ? '+' : ''}{matchup.homeTeam.stats.netRating.toFixed(1)}
            </span>
          </div>
        </div>
      </div>

      {/* Stats Footer */}
      <div className="flex justify-between items-center mt-3 pt-2 border-t border-[var(--border)] text-xs text-[var(--muted)]">
        <div>
          <span>Off Rtg: </span>
          <span className="font-mono">{matchup.awayTeam.stats.offensiveRating.toFixed(1)}</span>
          <span> vs </span>
          <span className="font-mono">{matchup.homeTeam.stats.offensiveRating.toFixed(1)}</span>
        </div>
        <div>
          <span>Def Rtg: </span>
          <span className="font-mono">{matchup.awayTeam.stats.defensiveRating.toFixed(1)}</span>
          <span> vs </span>
          <span className="font-mono">{matchup.homeTeam.stats.defensiveRating.toFixed(1)}</span>
        </div>
      </div>
    </div>
  );
}

// NHL Matchup Card
function NHLMatchupCard({ matchup, expanded }: { matchup: NHLMatchupDataPoint; expanded?: boolean }) {
  const { date, time } = formatGameTime(matchup.gameDate);
  const isCompleted = matchup.gameCompleted;

  return (
    <div className={`border border-[var(--border)] rounded p-3 bg-[var(--card)] ${expanded ? 'ring-2 ring-[var(--foreground)]/20' : ''}`}>
      {/* Header: Date/Time and Status */}
      <div className="flex justify-between items-center text-xs text-[var(--muted)] mb-2">
        <span>{date}</span>
        <span>{isCompleted ? 'Final' : time}</span>
      </div>

      {/* Teams */}
      <div className="space-y-2">
        {/* Away Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--muted)] w-4">
              {getConferenceAbbrev(matchup.awayTeam.conference)}
            </span>
            <span className="font-bold">{matchup.awayTeam.abbreviation}</span>
            <span className="text-xs text-[var(--muted)]">
              ({matchup.awayTeam.wins}-{matchup.awayTeam.losses}-{matchup.awayTeam.otLosses})
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-[var(--muted)]">#{matchup.awayTeam.conferenceRank}</span>
            <span className={`font-mono w-10 text-right ${matchup.awayTeam.stats.goalDiffPerGame >= 0 ? 'text-green-500' : 'text-red-500'}`}>
              {matchup.awayTeam.stats.goalDiffPerGame >= 0 ? '+' : ''}{matchup.awayTeam.stats.goalDiffPerGame.toFixed(2)}
            </span>
          </div>
        </div>

        {/* Home Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--muted)] w-4">
              {getConferenceAbbrev(matchup.homeTeam.conference)}
            </span>
            <span className="font-bold">{matchup.homeTeam.abbreviation}</span>
            <span className="text-xs text-[var(--muted)]">
              ({matchup.homeTeam.wins}-{matchup.homeTeam.losses}-{matchup.homeTeam.otLosses})
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-[var(--muted)]">#{matchup.homeTeam.conferenceRank}</span>
            <span className={`font-mono w-10 text-right ${matchup.homeTeam.stats.goalDiffPerGame >= 0 ? 'text-green-500' : 'text-red-500'}`}>
              {matchup.homeTeam.stats.goalDiffPerGame >= 0 ? '+' : ''}{matchup.homeTeam.stats.goalDiffPerGame.toFixed(2)}
            </span>
          </div>
        </div>
      </div>

      {/* Stats Footer */}
      <div className="flex justify-between items-center mt-3 pt-2 border-t border-[var(--border)] text-xs text-[var(--muted)]">
        <div>
          <span>GF/G: </span>
          <span className="font-mono">{matchup.awayTeam.stats.goalsPerGame.toFixed(2)}</span>
          <span> vs </span>
          <span className="font-mono">{matchup.homeTeam.stats.goalsPerGame.toFixed(2)}</span>
        </div>
        <div>
          <span>GA/G: </span>
          <span className="font-mono">{matchup.awayTeam.stats.goalsAgainstPerGame.toFixed(2)}</span>
          <span> vs </span>
          <span className="font-mono">{matchup.homeTeam.stats.goalsAgainstPerGame.toFixed(2)}</span>
        </div>
      </div>
    </div>
  );
}

// NFL/V2 Matchup Card
function NFLMatchupCard({ matchupKey, matchup, expanded }: { matchupKey: string; matchup: MatchupV2DataPoint; expanded?: boolean }) {
  const { date, time } = formatGameTime(matchup.game_datetime);
  const [awayCode, homeCode] = matchupKey.split('-').map(s => s.toUpperCase());
  const odds = matchup.odds;

  return (
    <div className={`border border-[var(--border)] rounded p-3 bg-[var(--card)] ${expanded ? 'ring-2 ring-[var(--foreground)]/20' : ''}`}>
      {/* Header: Date/Time */}
      <div className="flex justify-between items-center text-xs text-[var(--muted)] mb-2">
        <span>{date}</span>
        <span>{time}</span>
      </div>

      {/* Teams */}
      <div className="space-y-2">
        {/* Away Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="font-bold">{awayCode}</span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            {odds && (
              <>
                <span className="font-mono">{odds.away_spread}</span>
                <span className="font-mono text-[var(--muted)]">{odds.away_moneyline}</span>
              </>
            )}
          </div>
        </div>

        {/* Home Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="font-bold">{homeCode}</span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            {odds && (
              <>
                <span className="font-mono">{odds.home_spread}</span>
                <span className="font-mono text-[var(--muted)]">{odds.home_moneyline}</span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Betting Lines Footer */}
      {odds && (
        <div className="flex justify-center items-center mt-3 pt-2 border-t border-[var(--border)] text-xs">
          <span className="text-[var(--muted)]">O/U:</span>
          <span className="font-mono ml-1">{odds.over_under}</span>
        </div>
      )}
    </div>
  );
}

export function MatchupsWithNav({ data }: Props) {
  // Default to today
  const [selectedDay, setSelectedDay] = useState<string | null>(getTodayKey());
  const [selectedMatchup, setSelectedMatchup] = useState<string | null>(null);

  const { matchupIds, showAll } = useMemo(
    () => getFilteredMatchups(data, selectedDay, selectedMatchup),
    [data, selectedDay, selectedMatchup]
  );

  // Render filtered matchups based on data type
  const renderMatchups = () => {
    if (data.visualizationType === 'NBA_MATCHUP') {
      const nbaData = data as NBAMatchupData;
      const games = nbaData.dataPoints
        .sort((a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime());

      const filteredGames = showAll
        ? games
        : games.filter(g => matchupIds.includes(g.gameId));

      if (filteredGames.length === 0) {
        return <p className="text-[var(--muted)] text-sm">No upcoming games</p>;
      }

      return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filteredGames.map(matchup => (
            <NBAMatchupCard
              key={matchup.gameId}
              matchup={matchup}
              expanded={selectedMatchup === matchup.gameId}
            />
          ))}
        </div>
      );
    }

    if (data.visualizationType === 'NHL_MATCHUP') {
      const nhlData = data as NHLMatchupData;
      const games = nhlData.dataPoints
        .sort((a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime());

      const filteredGames = showAll
        ? games
        : games.filter(g => matchupIds.includes(g.gameId));

      if (filteredGames.length === 0) {
        return <p className="text-[var(--muted)] text-sm">No upcoming games</p>;
      }

      return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filteredGames.map(matchup => (
            <NHLMatchupCard
              key={matchup.gameId}
              matchup={matchup}
              expanded={selectedMatchup === matchup.gameId}
            />
          ))}
        </div>
      );
    }

    if (data.visualizationType === 'MATCHUP_V2') {
      const v2Data = data as MatchupV2Data;
      const dataPointsObj = v2Data.dataPoints as unknown as Record<string, MatchupV2DataPoint>;
      const matchupEntries = Object.entries(dataPointsObj)
        .sort(([, a], [, b]) => new Date(a.game_datetime).getTime() - new Date(b.game_datetime).getTime());

      const filteredEntries = showAll
        ? matchupEntries
        : matchupEntries.filter(([key]) => matchupIds.includes(key));

      if (filteredEntries.length === 0) {
        return <p className="text-[var(--muted)] text-sm">No upcoming games</p>;
      }

      return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filteredEntries.map(([key, matchup]) => (
            <NFLMatchupCard
              key={key}
              matchupKey={key}
              matchup={matchup}
              expanded={selectedMatchup === key}
            />
          ))}
        </div>
      );
    }

    // Legacy MATCHUP type
    const legacyData = data as MatchupData;
    const sortedMatchups = [...legacyData.dataPoints]
      .sort((a, b) => new Date(a.gameTime).getTime() - new Date(b.gameTime).getTime());

    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {sortedMatchups.map((matchup, i) => {
          const id = `legacy-${i}`;
          if (!showAll && !matchupIds.includes(id)) return null;
          return (
            <div key={i} className={`border border-[var(--border)] rounded p-3 bg-[var(--card)] ${selectedMatchup === id ? 'ring-2 ring-[var(--foreground)]/20' : ''}`}>
              <div className="flex justify-between items-center text-xs text-[var(--muted)] mb-2">
                <span>{new Date(matchup.gameTime).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}</span>
                <span>{new Date(matchup.gameTime).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}</span>
              </div>
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <span className="font-bold">{matchup.awayTeam}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-bold">{matchup.homeTeam}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div>
      <MatchupNav
        data={data}
        selectedDay={selectedDay}
        selectedMatchup={selectedMatchup}
        onDaySelect={setSelectedDay}
        onMatchupSelect={setSelectedMatchup}
      />
      {renderMatchups()}
    </div>
  );
}
