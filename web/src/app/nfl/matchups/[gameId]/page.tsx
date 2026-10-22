import { fetchChartRegistry, fetchChartData, fetchOrderedSportsWithCharts } from '@/lib/api';
import { NFLMatchupData, NFLMatchupDataPoint, NFLGameResults, NFLTeamBoxScore } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { NFLMatchupNav } from '@/components/ui/NFLMatchupNav';
import { MatchupComparisons } from '@/components/charts/MatchupComparisons';
import { CumPointDiffChart } from '@/components/charts/CumPointDiffChart';
import { WeeklyPointsChart } from '@/components/charts/WeeklyPointsChart';
import { ChartDownloadWrapper } from '@/components/charts/ChartDownloadWrapper';
import { pageMetadata } from '@/lib/og';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ gameId: string }>;
}

async function getNFLMatchupData(): Promise<NFLMatchupData | null> {
  const registry = await fetchChartRegistry();
  const keys = Object.keys(registry);

  for (const key of keys) {
    const data = await fetchChartData(key);
    if (data.sport?.toLowerCase() === 'nfl' && data.visualizationType === 'NFL_MATCHUP') {
      return data as NFLMatchupData;
    }
  }

  return null;
}

export async function generateStaticParams() {
  const matchupData = await getNFLMatchupData();
  if (!matchupData) return [];

  return matchupData.dataPoints.map(game => ({ gameId: game.gameId }));
}

export async function generateMetadata({ params }: Props) {
  const { gameId } = await params;
  const matchupData = await getNFLMatchupData();
  const game = matchupData?.dataPoints.find(g => g.gameId === gameId);

  if (!game) {
    return pageMetadata({
      title: 'NFL Matchup',
      description: 'Head-to-head NFL matchup worksheet on fastbreak',
    });
  }

  const title = `${game.awayTeam.abbreviation} @ ${game.homeTeam.abbreviation} - NFL Matchup`;
  return pageMetadata({
    title,
    description: `${game.awayTeam.name} vs ${game.homeTeam.name} matchup analysis`,
  });
}

function formatGameTime(gameTime: string): { date: string; time: string } {
  const d = new Date(gameTime);
  return {
    date: d.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }),
    time: d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }),
  };
}

function getMascot(teamName: string): string {
  const words = teamName.split(' ');
  return words[words.length - 1];
}

function weekLabel(game: NFLMatchupDataPoint): string | null {
  if (game.seasonType && game.seasonType !== 'REG') {
    return game.seasonTypeLabel ?? game.seasonType;
  }
  return game.week != null ? `Week ${game.week}` : null;
}

// The NFL stat bag is free-form on the wire; read the handful of values this
// page shows through one narrow accessor instead of typing the whole catalog.
function nflStat(team: NFLMatchupDataPoint['homeTeam'], key: string): number | null {
  const stat = team.stats?.[key] as { value?: number | null } | undefined;
  return stat?.value ?? null;
}

function formatSigned(value: number | null, decimals = 1): string {
  if (value == null) return '-';
  return `${value >= 0 ? '+' : ''}${value.toFixed(decimals)}`;
}

function MatchupHeader({ game }: { game: NFLMatchupDataPoint }) {
  const { date, time } = formatGameTime(game.gameDate);
  const week = weekLabel(game);
  const awayDiff = nflStat(game.awayTeam, 'pointDiffPerGame');
  const homeDiff = nflStat(game.homeTeam, 'pointDiffPerGame');
  const hasFinalScore = game.results?.homeScore != null && game.results?.awayScore != null;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      <div className="text-center text-xs text-[var(--muted)] mb-2">
        {[week, date, game.gameCompleted ? 'Final' : time].filter(Boolean).join(' · ')}
        {game.location?.stadium && <span className="ml-2">· {game.location.stadium}</span>}
      </div>

      <div className="grid grid-cols-5 gap-2 text-sm">
        <div className="text-right">
          <div className="font-bold">{game.awayTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{getMascot(game.awayTeam.name)}</div>
        </div>

        <div className="text-right text-xs text-[var(--muted)]">
          <div>{game.awayTeam.record ?? '-'}</div>
          <div>{game.awayTeam.division ?? game.awayTeam.conference ?? ''}</div>
          <div className={`font-mono ${(awayDiff ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {formatSigned(awayDiff)} PD/G
          </div>
        </div>

        <div className="text-center text-[var(--muted)] flex items-center justify-center">
          {hasFinalScore ? (
            <span className="font-bold text-base">
              {game.results!.awayScore} - {game.results!.homeScore}
            </span>
          ) : (
            '@'
          )}
        </div>

        <div className="text-left text-xs text-[var(--muted)]">
          <div>{game.homeTeam.record ?? '-'}</div>
          <div>{game.homeTeam.division ?? game.homeTeam.conference ?? ''}</div>
          <div className={`font-mono ${(homeDiff ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {formatSigned(homeDiff)} PD/G
          </div>
        </div>

        <div className="text-left">
          <div className="font-bold">{game.homeTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{getMascot(game.homeTeam.name)}</div>
        </div>
      </div>

      {game.odds && (
        <div className="mt-2 pt-2 border-t border-[var(--border)] flex justify-center gap-4 text-xs text-[var(--muted)]">
          {game.odds.details && <span className="font-mono">{game.odds.details}</span>}
          {game.odds.overUnder != null && <span>O/U {game.odds.overUnder}</span>}
          {game.odds.awayMoneyline != null && (
            <span>
              {game.awayTeam.abbreviation} {game.odds.awayMoneyline > 0 ? '+' : ''}
              {game.odds.awayMoneyline}
            </span>
          )}
          {game.odds.homeMoneyline != null && (
            <span>
              {game.homeTeam.abbreviation} {game.odds.homeMoneyline > 0 ? '+' : ''}
              {game.odds.homeMoneyline}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

interface BoxRowSpec {
  key: string;
  label: string;
  decimals?: number;
  suffix?: string;
  higherIsBetter?: boolean;
}

// Box score keys and their vs-season-average counterparts line up one to one,
// so one table drives both halves of every row.
const BOX_ROWS: BoxRowSpec[] = [
  { key: 'totalYards', label: 'YDS' },
  { key: 'passYards', label: 'PASS' },
  { key: 'rushYards', label: 'RUSH' },
  { key: 'yardsPerPlay', label: 'Y/P', decimals: 1 },
  { key: 'thirdDownPct', label: '3RD%', decimals: 1, suffix: '%' },
  { key: 'explosivePlays', label: 'EXPL' },
  { key: 'sacks', label: 'SACKS' },
  { key: 'turnovers', label: 'TO', higherIsBetter: false },
];

function boxValue(box: NFLTeamBoxScore | null | undefined, key: string): number | null {
  const value = box?.[key];
  return typeof value === 'number' ? value : null;
}

function vsAvgDiff(
  vsSeasonAvg: Record<string, unknown> | null | undefined,
  side: 'home' | 'away',
  key: string
): number | null {
  const sideStats = vsSeasonAvg?.[side] as Record<string, { difference?: number | null }> | undefined;
  const diff = sideStats?.[key]?.difference;
  // A difference that rounds away to nothing is not a difference worth painting.
  if (diff == null || Math.abs(diff) < 0.05) return null;
  return diff;
}

function formatBoxValue(value: number | null, spec: BoxRowSpec): string {
  if (value == null) return '-';
  return `${value.toFixed(spec.decimals ?? 0)}${spec.suffix ?? ''}`;
}

function MatchupResults({
  results,
  awayAbbrev,
  homeAbbrev,
}: {
  results: NFLGameResults;
  awayAbbrev: string;
  homeAbbrev: string;
}) {
  const { awayScore, homeScore, winner, margin, homeWon, teamBoxScore, vsSeasonAvg } = results;
  const awayBox = teamBoxScore?.away;
  const homeBox = teamBoxScore?.home;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      <div className="grid grid-cols-5 gap-2 text-sm items-center">
        <div className={`text-right text-2xl font-bold ${homeWon === false ? 'text-green-500' : ''}`}>
          {awayScore ?? '-'}
        </div>
        <div className="text-right text-xs text-[var(--muted)]">
          {homeWon === false && <span className="text-green-500">W</span>}
        </div>
        <div className="text-center">
          <div className="text-xs text-[var(--muted)]">Final</div>
          {winner && <div className="text-xs font-mono">{winner} +{margin ?? 0}</div>}
        </div>
        <div className="text-left text-xs text-[var(--muted)]">
          {homeWon === true && <span className="text-green-500">W</span>}
        </div>
        <div className={`text-left text-2xl font-bold ${homeWon === true ? 'text-green-500' : ''}`}>
          {homeScore ?? '-'}
        </div>
      </div>

      {(awayBox || homeBox) && (
        <div className="mt-2 pt-2 border-t border-[var(--border)]">
          <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs text-[var(--muted)] items-center px-1">
            <div className="text-right font-bold">{awayAbbrev}</div>
            <div className="text-center font-bold">Box (vs Avg)</div>
            <div className="text-left font-bold">{homeAbbrev}</div>
          </div>
          {BOX_ROWS.map(spec => {
            const away = boxValue(awayBox, spec.key);
            const home = boxValue(homeBox, spec.key);
            if (away == null && home == null) return null;

            const higherIsBetter = spec.higherIsBetter ?? true;
            const awayEdge =
              away != null && home != null && (higherIsBetter ? away > home : away < home);
            const homeEdge =
              away != null && home != null && (higherIsBetter ? home > away : home < away);
            const awayDiff = vsAvgDiff(vsSeasonAvg, 'away', spec.key);
            const homeDiff = vsAvgDiff(vsSeasonAvg, 'home', spec.key);

            return (
              <div
                key={spec.key}
                className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5"
              >
                <div className="flex items-center justify-end gap-1">
                  <span className="w-3 text-right shrink-0">
                    {awayEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
                  </span>
                  <span className="w-12 font-mono text-right shrink-0">
                    {formatBoxValue(away, spec)}
                  </span>
                  <span
                    className={`w-12 text-right shrink-0 font-mono ${
                      awayDiff == null ? '' : awayDiff >= 0 ? 'text-green-500' : 'text-red-500'
                    }`}
                  >
                    {awayDiff == null ? '' : formatSigned(awayDiff, spec.decimals ?? 1)}
                  </span>
                </div>
                <div className="text-center text-[var(--muted)]">{spec.label}</div>
                <div className="flex items-center justify-start gap-1">
                  <span
                    className={`w-12 text-left shrink-0 font-mono ${
                      homeDiff == null ? '' : homeDiff >= 0 ? 'text-green-500' : 'text-red-500'
                    }`}
                  >
                    {homeDiff == null ? '' : formatSigned(homeDiff, spec.decimals ?? 1)}
                  </span>
                  <span className="w-12 font-mono text-left shrink-0">
                    {formatBoxValue(home, spec)}
                  </span>
                  <span className="w-3 text-left shrink-0">
                    {homeEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default async function NFLMatchupPage({ params }: Props) {
  const { gameId } = await params;
  const orderedSports = await fetchOrderedSportsWithCharts();
  const matchupData = await getNFLMatchupData();

  if (!matchupData) {
    notFound();
  }

  const game = matchupData.dataPoints.find(g => g.gameId === gameId);

  if (!game) {
    notFound();
  }

  return (
    <main className="max-w-[2000px] mx-auto px-0 md:px-2">
      <SportTabs orderedSports={orderedSports} />

      <div className="px-1 md:px-0">
        <div className="sticky top-10 z-30 mb-4 bg-[var(--background)] py-1">
          <NFLMatchupNav games={matchupData.dataPoints} selectedGameId={gameId} />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
          <div className="flex flex-col gap-2 h-[700px] lg:h-[calc(100vh-80px)] order-2 lg:order-1">
            <div className="flex-1">
              <ChartDownloadWrapper title="Cumulative Point Diff by Week" source={matchupData.source}>
                <CumPointDiffChart
                  homeTeamStats={game.homeTeam.stats}
                  awayTeamStats={game.awayTeam.stats}
                  homeAbbrev={game.homeTeam.abbreviation}
                  awayAbbrev={game.awayTeam.abbreviation}
                  leagueStats={matchupData.leagueCumPointDiffStats}
                />
              </ChartDownloadWrapper>
            </div>
            <div className="flex-1">
              <ChartDownloadWrapper title="Weekly Points Scored vs Allowed" source={matchupData.source}>
                <WeeklyPointsChart
                  homeTeamStats={game.homeTeam.stats}
                  awayTeamStats={game.awayTeam.stats}
                  homeAbbrev={game.homeTeam.abbreviation}
                  awayAbbrev={game.awayTeam.abbreviation}
                  leagueStats={matchupData.leagueWeeklyStats}
                />
              </ChartDownloadWrapper>
            </div>
          </div>

          <div className="order-1 lg:order-2">
            <MatchupHeader game={game} />
            {game.gameCompleted && game.results && (
              <MatchupResults
                results={game.results}
                awayAbbrev={game.awayTeam.abbreviation}
                homeAbbrev={game.homeTeam.abbreviation}
              />
            )}
            {game.comparisons ? (
              <MatchupComparisons
                comparisons={game.comparisons}
                awayAbbrev={game.awayTeam.abbreviation}
                homeAbbrev={game.homeTeam.abbreviation}
                percentScale="percent"
              />
            ) : (
              <p className="text-[var(--muted)] text-sm">No detailed stats available for this matchup.</p>
            )}
          </div>
        </div>

        <div className="mt-4 py-4 border-t border-[var(--border)] text-xs text-[var(--muted)]">
          {matchupData.source} · {new Date(matchupData.lastUpdated).toLocaleDateString()}
        </div>
      </div>
    </main>
  );
}
