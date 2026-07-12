import { MLBMonthTrend, MLBStatValue, MLBTeamStats } from '@/types/chart';

export function parseMLBStat(stat: unknown): MLBStatValue | null {
  if (stat == null || typeof stat !== 'object') return null;
  const s = stat as Record<string, unknown>;
  return {
    value: typeof s.value === 'number' ? s.value : null,
    rank: typeof s.rank === 'number' ? s.rank : null,
    rankDisplay: typeof s.rankDisplay === 'string' ? s.rankDisplay : null,
  };
}

export function getRunDiffPerGame(stats: MLBTeamStats | null | undefined): MLBStatValue | null {
  const fromTrend = parseMLBStat(stats?.monthTrend?.runDiffPerGame);
  if (fromTrend?.value != null) return fromTrend;

  const runs = parseMLBStat(stats?.runsPerGame);
  const allowed = parseMLBStat(stats?.runsAllowedPerGame);
  if (runs?.value != null && allowed?.value != null) {
    return { value: runs.value - allowed.value, rank: null, rankDisplay: null };
  }

  return null;
}

export function getRecordRank(trend: MLBMonthTrend | null | undefined): number | null {
  return trend?.record?.rank ?? null;
}

export function formatRunDiff(value: number | null | undefined): string {
  if (value == null) return '-';
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}`;
}

export function getLeagueAbbrev(league: string | undefined): string {
  if (!league) return '';
  const lower = league.toLowerCase();
  if (lower === 'al' || lower.includes('american')) return 'AL';
  if (lower === 'nl' || lower.includes('national')) return 'NL';
  return league.slice(0, 2).toUpperCase();
}
