import { fetchChartRegistry, fetchChartData, fetchOrderedSportsWithCharts } from '@/lib/api';
import { MLBMatchupData, MLBMatchupDataPoint } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { MLBMatchupNav } from '@/components/ui/MLBMatchupNav';
import { MatchupComparisons } from '@/components/charts/MatchupComparisons';
import { CumRunDiffChart } from '@/components/charts/CumRunDiffChart';
import { WeeklyRunsChart } from '@/components/charts/WeeklyRunsChart';
import { ChartDownloadWrapper } from '@/components/charts/ChartDownloadWrapper';
import { formatRunDiff, getLeagueAbbrev, getRecordRank, getRunDiffPerGame } from '@/lib/mlbStats';
import { pageMetadata } from '@/lib/og';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ gameId: string }>;
}

async function getMLBMatchupData(): Promise<MLBMatchupData | null> {
  const registry = await fetchChartRegistry();
  const keys = Object.keys(registry);

  for (const key of keys) {
    const data = await fetchChartData(key);
    if (data.sport?.toLowerCase() === 'mlb' && data.visualizationType === 'MLB_MATCHUP') {
      return data as MLBMatchupData;
    }
  }

  return null;
}

export async function generateStaticParams() {
  const matchupData = await getMLBMatchupData();
  if (!matchupData) return [];

  return matchupData.dataPoints.map(game => ({
    gameId: game.gameId,
  }));
}

export async function generateMetadata({ params }: Props) {
  const { gameId } = await params;
  const matchupData = await getMLBMatchupData();
  const game = matchupData?.dataPoints.find(g => g.gameId === gameId);

  if (!game) {
    return pageMetadata({ title: 'MLB Matchup' });
  }

  const title = `${game.awayTeam.abbreviation} @ ${game.homeTeam.abbreviation} - MLB Matchup`;
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

function MatchupHeader({ game }: { game: MLBMatchupDataPoint }) {
  const { date, time } = formatGameTime(game.gameDate);
  const isCompleted = game.gameCompleted;
  const awayRunDiff = getRunDiffPerGame(game.awayTeam.stats);
  const homeRunDiff = getRunDiffPerGame(game.homeTeam.stats);

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      <div className="text-center text-xs text-[var(--muted)] mb-2">
        {date} · {isCompleted ? 'Final' : time}
        {game.location?.stadium && <span className="ml-2">· {game.location.stadium}</span>}
      </div>

      <div className="grid grid-cols-5 gap-2 text-sm">
        <div className="text-right">
          <div className="font-bold">{game.awayTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{getMascot(game.awayTeam.name)}</div>
        </div>

        <div className="text-right text-xs text-[var(--muted)]">
          <div>{game.awayTeam.record}</div>
          <div>{getLeagueAbbrev(game.awayTeam.league)} · #{getRecordRank(game.awayTeam.stats.monthTrend) ?? '-'}</div>
          <div className={`font-mono ${(awayRunDiff?.value ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {formatRunDiff(awayRunDiff?.value)} RD/G
          </div>
        </div>

        <div className="text-center text-[var(--muted)] flex items-center justify-center">
          {game.results?.homeScore != null && game.results?.awayScore != null ? (
            <span className="font-bold text-base">
              {game.results.awayScore} - {game.results.homeScore}
            </span>
          ) : (
            '@'
          )}
        </div>

        <div className="text-left text-xs text-[var(--muted)]">
          <div>{game.homeTeam.record}</div>
          <div>{getLeagueAbbrev(game.homeTeam.league)} · #{getRecordRank(game.homeTeam.stats.monthTrend) ?? '-'}</div>
          <div className={`font-mono ${(homeRunDiff?.value ?? 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {formatRunDiff(homeRunDiff?.value)} RD/G
          </div>
        </div>

        <div className="text-left">
          <div className="font-bold">{game.homeTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{getMascot(game.homeTeam.name)}</div>
        </div>
      </div>

      {game.odds && (
        <div className="mt-2 pt-2 border-t border-[var(--border)] flex justify-center gap-4 text-xs text-[var(--muted)]">
          {game.odds.overUnder != null && <span>O/U {game.odds.overUnder}</span>}
          {game.odds.homeMoneyline != null && <span>{game.homeTeam.abbreviation} {game.odds.homeMoneyline > 0 ? '+' : ''}{game.odds.homeMoneyline}</span>}
          {game.odds.awayMoneyline != null && <span>{game.awayTeam.abbreviation} {game.odds.awayMoneyline > 0 ? '+' : ''}{game.odds.awayMoneyline}</span>}
        </div>
      )}
    </div>
  );
}

export default async function MLBMatchupPage({ params }: Props) {
  const { gameId } = await params;
  const orderedSports = await fetchOrderedSportsWithCharts();
  const matchupData = await getMLBMatchupData();

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
        <MLBMatchupNav games={matchupData.dataPoints} selectedGameId={gameId} />

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
          <div className="flex flex-col gap-2 h-[700px] lg:h-[calc(100vh-80px)] order-2 lg:order-1">
            <div className="flex-1">
              <ChartDownloadWrapper title="Cumulative Run Diff by Week" source={matchupData.source}>
                <CumRunDiffChart
                  homeTeamStats={game.homeTeam.stats}
                  awayTeamStats={game.awayTeam.stats}
                  homeAbbrev={game.homeTeam.abbreviation}
                  awayAbbrev={game.awayTeam.abbreviation}
                  leagueStats={matchupData.leagueCumRunDiffStats}
                />
              </ChartDownloadWrapper>
            </div>
            <div className="flex-1">
              <ChartDownloadWrapper title="Weekly Runs Scored vs Allowed" source={matchupData.source}>
                <WeeklyRunsChart
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
            {game.comparisons && (
              <MatchupComparisons
                comparisons={game.comparisons}
                awayAbbrev={game.awayTeam.abbreviation}
                homeAbbrev={game.homeTeam.abbreviation}
              />
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
