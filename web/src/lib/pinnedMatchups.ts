import {
  MatchupData,
  MatchupV2Data,
  MatchupV2DataPoint,
  MLBMatchupData,
  NBAMatchupData,
  NHLMatchupData,
} from '@/types/chart';
import { getDateKey } from '@/lib/mlbMatchups';

type AnyMatchupData =
  | MatchupData
  | NBAMatchupData
  | NHLMatchupData
  | MLBMatchupData
  | MatchupV2Data;

export function findPinnedMatchupIdForDay(
  data: AnyMatchupData,
  dayKey: string,
  pinnedTeamCodes: string[]
): string | null {
  if (pinnedTeamCodes.length === 0) return null;

  const codes = pinnedTeamCodes.map(code => code.toUpperCase());

  if (data.visualizationType === 'MLB_MATCHUP') {
    const games = (data as MLBMatchupData).dataPoints.filter(
      game => getDateKey(new Date(game.gameDate)) === dayKey
    );
    for (const code of codes) {
      const game = games.find(
        game =>
          game.awayTeam.abbreviation.toUpperCase() === code ||
          game.homeTeam.abbreviation.toUpperCase() === code
      );
      if (game) return game.gameId;
    }
    return null;
  }

  if (data.visualizationType === 'NBA_MATCHUP') {
    const games = (data as NBAMatchupData).dataPoints.filter(
      game => getDateKey(new Date(game.gameDate)) === dayKey
    );
    for (const code of codes) {
      const game = games.find(
        game =>
          game.awayTeam.abbreviation.toUpperCase() === code ||
          game.homeTeam.abbreviation.toUpperCase() === code
      );
      if (game) return game.gameId;
    }
    return null;
  }

  if (data.visualizationType === 'NHL_MATCHUP') {
    const games = (data as NHLMatchupData).dataPoints.filter(
      game => getDateKey(new Date(game.gameDate)) === dayKey
    );
    for (const code of codes) {
      const game = games.find(
        game =>
          game.awayTeam.abbreviation.toUpperCase() === code ||
          game.homeTeam.abbreviation.toUpperCase() === code
      );
      if (game) return game.gameId;
    }
    return null;
  }

  if (data.visualizationType === 'MATCHUP_V2') {
    const dataPointsObj = (data as MatchupV2Data).dataPoints as unknown as Record<
      string,
      MatchupV2DataPoint
    >;
    for (const [key, matchup] of Object.entries(dataPointsObj)) {
      if (getDateKey(new Date(matchup.game_datetime)) !== dayKey) continue;
      if (key.split('-').some(part => codes.includes(part.toUpperCase()))) {
        return key;
      }
    }
  }

  return null;
}
