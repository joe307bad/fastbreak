'use client';

import { MatchupData, NBAMatchupData, NBAMatchupDataPoint, NHLMatchupData, NHLMatchupDataPoint, MatchupV2Data, MatchupV2DataPoint, MatchupV2Odds } from '@/types/chart';

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

// NBA Matchup Card
function NBAMatchupCard({ matchup }: { matchup: NBAMatchupDataPoint }) {
  const { date, time } = formatGameTime(matchup.gameDate);
  const isCompleted = matchup.gameCompleted;

  return (
    <div className="border border-[var(--border)] rounded p-3 bg-[var(--card)]">
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
function NHLMatchupCard({ matchup }: { matchup: NHLMatchupDataPoint }) {
  const { date, time } = formatGameTime(matchup.gameDate);
  const isCompleted = matchup.gameCompleted;

  return (
    <div className="border border-[var(--border)] rounded p-3 bg-[var(--card)]">
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
function NFLMatchupCard({ matchupKey, matchup }: { matchupKey: string; matchup: MatchupV2DataPoint }) {
  const { date, time } = formatGameTime(matchup.game_datetime);
  const [awayCode, homeCode] = matchupKey.split('-').map(s => s.toUpperCase());
  const odds = matchup.odds;

  return (
    <div className="border border-[var(--border)] rounded p-3 bg-[var(--card)]">
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

// Legacy Matchup Card (original MATCHUP type)
function LegacyMatchupCard({ matchup }: { matchup: { homeTeam: string; awayTeam: string; gameTime: string; homeTeamConference: string; awayTeamConference: string; comparisons: Array<{ title: string; homeTeamValue: string | number; awayTeamValue: string | number }> } }) {
  const { date, time } = formatGameTime(matchup.gameTime);

  const getComparison = (titleIncludes: string) => {
    const comp = matchup.comparisons?.find(c => c.title.toLowerCase().includes(titleIncludes.toLowerCase()));
    return comp ? { home: comp.homeTeamValue, away: comp.awayTeamValue } : null;
  };

  const spread = getComparison('spread');
  const moneyline = getComparison('moneyline') || getComparison('money');
  const confRank = getComparison('conf') || getComparison('rank');
  const netRating = getComparison('net') || getComparison('rating');
  const record = getComparison('record') || getComparison('w-l');

  return (
    <div className="border border-[var(--border)] rounded p-3 bg-[var(--card)]">
      {/* Header */}
      <div className="flex justify-between items-center text-xs text-[var(--muted)] mb-2">
        <span>{date}</span>
        <span>{time}</span>
      </div>

      {/* Teams */}
      <div className="space-y-2">
        {/* Away Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--muted)] w-4">
              {getConferenceAbbrev(matchup.awayTeamConference)}
            </span>
            <span className="font-bold">{matchup.awayTeam}</span>
            {record && <span className="text-xs text-[var(--muted)]">({record.away})</span>}
          </div>
          <div className="flex items-center gap-3 text-xs">
            {confRank && <span className="text-[var(--muted)]">#{confRank.away}</span>}
            {netRating && (
              <span className={`font-mono ${Number(netRating.away) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                {Number(netRating.away) >= 0 ? '+' : ''}{netRating.away}
              </span>
            )}
            {moneyline && <span className="font-mono w-12 text-right">{moneyline.away}</span>}
          </div>
        </div>

        {/* Home Team */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--muted)] w-4">
              {getConferenceAbbrev(matchup.homeTeamConference)}
            </span>
            <span className="font-bold">{matchup.homeTeam}</span>
            {record && <span className="text-xs text-[var(--muted)]">({record.home})</span>}
          </div>
          <div className="flex items-center gap-3 text-xs">
            {confRank && <span className="text-[var(--muted)]">#{confRank.home}</span>}
            {netRating && (
              <span className={`font-mono ${Number(netRating.home) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                {Number(netRating.home) >= 0 ? '+' : ''}{netRating.home}
              </span>
            )}
            {moneyline && <span className="font-mono w-12 text-right">{moneyline.home}</span>}
          </div>
        </div>
      </div>

      {/* Spread Footer */}
      {spread && (
        <div className="flex justify-center items-center mt-3 pt-2 border-t border-[var(--border)] text-xs">
          <span className="text-[var(--muted)]">Spread:</span>
          <span className="font-mono ml-1">{spread.home}</span>
        </div>
      )}
    </div>
  );
}

export function UpcomingMatchups({ data }: Props) {
  // Handle NBA_MATCHUP type
  if (data.visualizationType === 'NBA_MATCHUP') {
    const nbaData = data as NBAMatchupData;
    // Filter to upcoming games only and sort by date
    const upcomingGames = nbaData.dataPoints
      .filter(m => !m.gameCompleted)
      .sort((a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime());

    if (upcomingGames.length === 0) {
      // Show recent completed games if no upcoming
      const recentGames = nbaData.dataPoints
        .filter(m => m.gameCompleted)
        .sort((a, b) => new Date(b.gameDate).getTime() - new Date(a.gameDate).getTime())
        .slice(0, 8);

      return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {recentGames.map((matchup) => (
            <NBAMatchupCard key={matchup.gameId} matchup={matchup} />
          ))}
        </div>
      );
    }

    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {upcomingGames.slice(0, 12).map((matchup) => (
          <NBAMatchupCard key={matchup.gameId} matchup={matchup} />
        ))}
      </div>
    );
  }

  // Handle NHL_MATCHUP type
  if (data.visualizationType === 'NHL_MATCHUP') {
    const nhlData = data as NHLMatchupData;
    // Filter to upcoming games only and sort by date
    const upcomingGames = nhlData.dataPoints
      .filter(m => !m.gameCompleted)
      .sort((a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime());

    if (upcomingGames.length === 0) {
      // Show recent completed games if no upcoming
      const recentGames = nhlData.dataPoints
        .filter(m => m.gameCompleted)
        .sort((a, b) => new Date(b.gameDate).getTime() - new Date(a.gameDate).getTime())
        .slice(0, 8);

      return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {recentGames.map((matchup) => (
            <NHLMatchupCard key={matchup.gameId} matchup={matchup} />
          ))}
        </div>
      );
    }

    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {upcomingGames.slice(0, 12).map((matchup) => (
          <NHLMatchupCard key={matchup.gameId} matchup={matchup} />
        ))}
      </div>
    );
  }

  // Handle MATCHUP_V2 type (NFL style with object dataPoints)
  if (data.visualizationType === 'MATCHUP_V2') {
    const v2Data = data as MatchupV2Data;
    // dataPoints is an object keyed by matchup ID
    const dataPointsObj = v2Data.dataPoints as unknown as Record<string, MatchupV2DataPoint>;
    const matchupEntries = Object.entries(dataPointsObj)
      .sort(([, a], [, b]) => new Date(a.game_datetime).getTime() - new Date(b.game_datetime).getTime());

    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {matchupEntries.map(([key, matchup]) => (
          <NFLMatchupCard key={key} matchupKey={key} matchup={matchup} />
        ))}
      </div>
    );
  }

  // Handle legacy MATCHUP type
  const legacyData = data as MatchupData;
  const sortedMatchups = [...legacyData.dataPoints].sort(
    (a, b) => new Date(a.gameTime).getTime() - new Date(b.gameTime).getTime()
  );

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {sortedMatchups.map((matchup, i) => (
        <LegacyMatchupCard key={i} matchup={matchup} />
      ))}
    </div>
  );
}
