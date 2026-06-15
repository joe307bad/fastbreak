import {
  MLBMatchupData,
  MLBMatchupDataPoint,
  NBAMatchupData,
  NBAMatchupDataPoint,
  NHLMatchupData,
  NHLMatchupDataPoint,
} from '@/types/chart';
import { getRunDiffPerGame } from '@/lib/mlbStats';

const MAX_DAYS = 4;
const GAMES_PER_DAY = 2;

function dayKey(gameDate: string): string {
  const d = new Date(gameDate);
  return `${d.getUTCFullYear()}-${d.getUTCMonth()}-${d.getUTCDate()}`;
}

function groupByDay<T extends { gameDate: string }>(games: T[]): Map<string, T[]> {
  const out = new Map<string, T[]>();
  for (const g of games) {
    const k = dayKey(g.gameDate);
    const bucket = out.get(k);
    if (bucket) bucket.push(g);
    else out.set(k, [g]);
  }
  return out;
}

function orderedDays<T extends { gameDate: string }>(groups: Map<string, T[]>): [string, T[]][] {
  return [...groups.entries()].sort((a, b) => {
    const da = new Date(a[1][0].gameDate).getTime();
    const db = new Date(b[1][0].gameDate).getTime();
    return da - db;
  });
}

function selectNBA(data: NBAMatchupData): string[] {
  const upcoming = data.dataPoints.filter(m => !m.gameCompleted);
  const byDay = groupByDay(upcoming);
  const days = orderedDays(byDay).slice(0, MAX_DAYS);
  const ids: string[] = [];
  for (const [, games] of days) {
    const ranked = [...games].sort((a: NBAMatchupDataPoint, b: NBAMatchupDataPoint) => {
      const ta = (a.homeTeam.stats.netRating ?? 0) + (a.awayTeam.stats.netRating ?? 0);
      const tb = (b.homeTeam.stats.netRating ?? 0) + (b.awayTeam.stats.netRating ?? 0);
      return tb - ta;
    });
    for (const g of ranked.slice(0, GAMES_PER_DAY)) ids.push(g.gameId);
  }
  return ids;
}

function selectNHL(data: NHLMatchupData): string[] {
  const upcoming = data.dataPoints.filter(m => !m.gameCompleted);
  const byDay = groupByDay(upcoming);
  const days = orderedDays(byDay).slice(0, MAX_DAYS);
  const ids: string[] = [];
  for (const [, games] of days) {
    const ranked = [...games].sort((a: NHLMatchupDataPoint, b: NHLMatchupDataPoint) => {
      const ta = a.homeTeam.stats.goalDiffPerGame + a.awayTeam.stats.goalDiffPerGame;
      const tb = b.homeTeam.stats.goalDiffPerGame + b.awayTeam.stats.goalDiffPerGame;
      return tb - ta;
    });
    for (const g of ranked.slice(0, GAMES_PER_DAY)) ids.push(g.gameId);
  }
  return ids;
}

function getTeamRunDiff(team: MLBMatchupDataPoint['homeTeam']): number {
  return getRunDiffPerGame(team.stats)?.value ?? 0;
}

function selectMLB(data: MLBMatchupData): string[] {
  const upcoming = data.dataPoints.filter(m => !m.gameCompleted);
  const byDay = groupByDay(upcoming);
  const days = orderedDays(byDay).slice(0, MAX_DAYS);
  const ids: string[] = [];
  for (const [, games] of days) {
    const ranked = [...games].sort((a: MLBMatchupDataPoint, b: MLBMatchupDataPoint) => {
      const ta = getTeamRunDiff(a.homeTeam) + getTeamRunDiff(a.awayTeam);
      const tb = getTeamRunDiff(b.homeTeam) + getTeamRunDiff(b.awayTeam);
      return tb - ta;
    });
    for (const g of ranked.slice(0, GAMES_PER_DAY)) ids.push(g.gameId);
  }
  return ids;
}

export function selectTopMatchups(
  data: NBAMatchupData | NHLMatchupData | MLBMatchupData
): string[] {
  if (data.visualizationType === 'NBA_MATCHUP') return selectNBA(data);
  if (data.visualizationType === 'NHL_MATCHUP') return selectNHL(data);
  if (data.visualizationType === 'MLB_MATCHUP') return selectMLB(data);
  return [];
}
