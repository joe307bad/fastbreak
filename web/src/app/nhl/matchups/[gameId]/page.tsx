import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { NHLMatchupData, NHLMatchupDataPoint, NHLMatchupResults, NHLBoxScore, VsSeasonAvgStat } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { NHLMatchupNav } from '@/components/ui/NHLMatchupNav';
import { MatchupComparisons } from '@/components/charts/MatchupComparisons';
import { CumXgChart } from '@/components/charts/CumXgChart';
import { XgVsPointsChart } from '@/components/charts/XgVsPointsChart';
import { ChartDownloadWrapper } from '@/components/charts/ChartDownloadWrapper';
import { NHLPlayerComparison } from '@/components/charts/NHLPlayerComparison';
import { fetchOrderedSportsWithCharts } from '@/lib/api';
import { pageMetadata } from '@/lib/og';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ gameId: string }>;
}

async function getNHLMatchupData(): Promise<NHLMatchupData | null> {
  const registry = await fetchChartRegistry();
  const keys = Object.keys(registry);

  for (const key of keys) {
    const data = await fetchChartData(key);
    if (data.sport?.toLowerCase() === 'nhl' && data.visualizationType === 'NHL_MATCHUP') {
      return data as NHLMatchupData;
    }
  }

  return null;
}

export async function generateStaticParams() {
  const matchupData = await getNHLMatchupData();
  if (!matchupData) return [];

  return matchupData.dataPoints.map(game => ({
    gameId: game.gameId,
  }));
}

export async function generateMetadata({ params }: Props) {
  const { gameId } = await params;
  const matchupData = await getNHLMatchupData();
  const game = matchupData?.dataPoints.find(g => g.gameId === gameId);

  if (!game) {
    return pageMetadata({ title: 'NHL Matchup' });
  }

  const title = `${game.awayTeam.abbreviation} @ ${game.homeTeam.abbreviation} - NHL Matchup`;
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

function getConferenceRankBadgeClasses(rank: number): string {
  const base = 'inline-flex items-center justify-center px-1.5 h-4 rounded text-[10px] font-medium';
  if (rank <= 8) return `${base} bg-green-500/20 text-green-500`;   // Playoff spot
  if (rank <= 10) return `${base} bg-yellow-500/20 text-yellow-500`; // Bubble
  return `${base} bg-red-500/20 text-red-500`;
}

function MatchupHeader({ game }: { game: NHLMatchupDataPoint }) {
  const { date, time } = formatGameTime(game.gameDate);
  const isCompleted = game.gameCompleted;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      {/* Date/Time + Location */}
      <div className="text-center text-xs text-[var(--muted)] mb-2">
        {date} · {isCompleted ? 'Final' : time}
        {game.location?.stadium && (
          <span className="ml-2">· {game.location.stadium}</span>
        )}
      </div>

      {/* Teams - 5 column grid */}
      <div className="grid grid-cols-5 gap-2 text-sm">
        {/* Away Team Info */}
        <div className="text-right">
          <div className="font-bold">{game.awayTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{getMascot(game.awayTeam.name)}</div>
        </div>

        {/* Away Record/Rank/Stats */}
        <div className="text-right text-xs text-[var(--muted)]">
          <div>{game.awayTeam.wins}-{game.awayTeam.losses}-{game.awayTeam.otLosses}</div>
          <div className="flex items-center justify-end gap-1">
            <span className={getConferenceRankBadgeClasses(game.awayTeam.conferenceRank)}>
              {game.awayTeam.conferenceRank}
            </span>
            <span>{game.awayTeam.conference === 'Eastern' ? 'E' : 'W'}</span>
          </div>
          <div className={`font-mono ${game.awayTeam.stats.goalDiffPerGame >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {game.awayTeam.stats.goalDiffPerGame >= 0 ? '+' : ''}{game.awayTeam.stats.goalDiffPerGame.toFixed(2)}
          </div>
          {game.awayTeam.streak && (
            <div className="text-[10px]">{game.awayTeam.streak}</div>
          )}
        </div>

        {/* Center - @ */}
        <div className="text-center text-[var(--muted)] flex items-center justify-center">
          @
        </div>

        {/* Home Record/Rank/Stats */}
        <div className="text-left text-xs text-[var(--muted)]">
          <div>{game.homeTeam.wins}-{game.homeTeam.losses}-{game.homeTeam.otLosses}</div>
          <div className="flex items-center justify-start gap-1">
            <span className={getConferenceRankBadgeClasses(game.homeTeam.conferenceRank)}>
              {game.homeTeam.conferenceRank}
            </span>
            <span>{game.homeTeam.conference === 'Eastern' ? 'E' : 'W'}</span>
          </div>
          <div className={`font-mono ${game.homeTeam.stats.goalDiffPerGame >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {game.homeTeam.stats.goalDiffPerGame >= 0 ? '+' : ''}{game.homeTeam.stats.goalDiffPerGame.toFixed(2)}
          </div>
          {game.homeTeam.streak && (
            <div className="text-[10px]">{game.homeTeam.streak}</div>
          )}
        </div>

        {/* Home Team Info */}
        <div className="text-left">
          <div className="font-bold">{game.homeTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{getMascot(game.homeTeam.name)}</div>
        </div>
      </div>

      {/* Playoff Probabilities */}
      {(game.awayTeam.playoffProbability || game.homeTeam.playoffProbability) && (
        <div className="mt-2 pt-2 border-t border-[var(--border)] grid grid-cols-5 gap-2 text-[10px] text-[var(--muted)]">
          <div className="text-right">Playoffs</div>
          <div className="text-right font-mono">
            {game.awayTeam.playoffProbability ? `${game.awayTeam.playoffProbability.playoffProb}%` : '-'}
          </div>
          <div className="text-center">Prob</div>
          <div className="text-left font-mono">
            {game.homeTeam.playoffProbability ? `${game.homeTeam.playoffProbability.playoffProb}%` : '-'}
          </div>
          <div className="text-left">Playoffs</div>
        </div>
      )}
    </div>
  );
}

function formatDiff(diff: number | undefined): string {
  if (diff === undefined || diff === null) return '-';
  const sign = diff >= 0 ? '+' : '';
  return `${sign}${diff.toFixed(1)}`;
}

function formatPctDiff(diff: number | undefined): string {
  if (diff === undefined || diff === null) return '-';
  const sign = diff >= 0 ? '+' : '';
  return `${sign}${diff.toFixed(1)}%`;
}

interface BoxStatRowProps {
  label: string;
  awayValue: number;
  homeValue: number;
  awayVsAvg?: VsSeasonAvgStat;
  homeVsAvg?: VsSeasonAvgStat;
  higherIsBetter?: boolean;
  isPct?: boolean;
}

function BoxStatRow({ label, awayValue, homeValue, awayVsAvg, homeVsAvg, higherIsBetter = true, isPct = false }: BoxStatRowProps) {
  const awayBetter = higherIsBetter ? awayValue > homeValue : awayValue < homeValue;
  const homeBetter = higherIsBetter ? homeValue > awayValue : homeValue < awayValue;
  const formatVal = isPct ? (v: number) => `${v.toFixed(1)}%` : (v: number) => String(v);
  const formatVsAvg = isPct ? formatPctDiff : formatDiff;

  return (
    <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5">
      <div className="flex items-center justify-end gap-1">
        <span className="w-3 text-right shrink-0">
          {awayBetter && <span className="text-green-500 text-[10px]">&#9664;</span>}
        </span>
        <span className="w-10 font-mono text-right shrink-0">{formatVal(awayValue)}</span>
        <span className={`w-12 text-right shrink-0 ${awayVsAvg ? (awayVsAvg.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
          {awayVsAvg ? formatVsAvg(awayVsAvg.difference) : ''}
        </span>
      </div>
      <div className="text-center text-[var(--muted)]">{label}</div>
      <div className="flex items-center justify-start gap-1">
        <span className={`w-12 text-left shrink-0 ${homeVsAvg ? (homeVsAvg.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
          {homeVsAvg ? formatVsAvg(homeVsAvg.difference) : ''}
        </span>
        <span className="w-10 font-mono text-left shrink-0">{formatVal(homeValue)}</span>
        <span className="w-3 text-left shrink-0">
          {homeBetter && <span className="text-green-500 text-[10px]">&#9654;</span>}
        </span>
      </div>
    </div>
  );
}

function MatchupResults({ results, awayAbbrev, homeAbbrev }: {
  results: NHLMatchupResults;
  awayAbbrev: string;
  homeAbbrev: string;
}) {
  const { finalScore, teamBoxScore, vsSeasonAvg } = results;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      {/* Final Score - 5 column grid */}
      <div className="grid grid-cols-5 gap-2 text-sm items-center">
        <div className={`text-right text-2xl font-bold ${!finalScore.homeWon ? 'text-green-500' : ''}`}>
          {finalScore.away}
        </div>
        <div className="text-right text-xs text-[var(--muted)]">
          {!finalScore.homeWon && <span className="text-green-500">W</span>}
        </div>
        <div className="text-center">
          <div className="text-xs text-[var(--muted)]">Final</div>
          <div className="text-xs font-mono">{finalScore.winner} +{finalScore.margin}</div>
        </div>
        <div className="text-left text-xs text-[var(--muted)]">
          {finalScore.homeWon && <span className="text-green-500">W</span>}
        </div>
        <div className={`text-left text-2xl font-bold ${finalScore.homeWon ? 'text-green-500' : ''}`}>
          {finalScore.home}
        </div>
      </div>

      {/* Box Score Summary */}
      {teamBoxScore && (
        <div className="mt-2 pt-2 border-t border-[var(--border)]">
          <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs text-[var(--muted)] items-center px-1">
            <div className="text-right font-bold">{awayAbbrev}</div>
            <div className="text-center font-bold">Box (vs Avg)</div>
            <div className="text-left font-bold">{homeAbbrev}</div>
          </div>
          <BoxStatRow
            label="SOG"
            awayValue={teamBoxScore.away.sog}
            homeValue={teamBoxScore.home.sog}
            awayVsAvg={vsSeasonAvg?.away?.shots as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.shots as VsSeasonAvgStat | undefined}
          />
          <BoxStatRow
            label="Hits"
            awayValue={teamBoxScore.away.hits}
            homeValue={teamBoxScore.home.hits}
            awayVsAvg={vsSeasonAvg?.away?.hits as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.hits as VsSeasonAvgStat | undefined}
          />
          <BoxStatRow
            label="Blocks"
            awayValue={teamBoxScore.away.blocks}
            homeValue={teamBoxScore.home.blocks}
            awayVsAvg={vsSeasonAvg?.away?.blocks as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.blocks as VsSeasonAvgStat | undefined}
          />
          <BoxStatRow
            label="PP Goals"
            awayValue={teamBoxScore.away.powerPlayGoals}
            homeValue={teamBoxScore.home.powerPlayGoals}
            awayVsAvg={vsSeasonAvg?.away?.ppGoals as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.ppGoals as VsSeasonAvgStat | undefined}
          />
          <BoxStatRow
            label="FO%"
            awayValue={teamBoxScore.away.faceoffWinPct}
            homeValue={teamBoxScore.home.faceoffWinPct}
            awayVsAvg={vsSeasonAvg?.away?.faceoffPct as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.faceoffPct as VsSeasonAvgStat | undefined}
            isPct={true}
          />
          <BoxStatRow
            label="Saves"
            awayValue={teamBoxScore.away.saves}
            homeValue={teamBoxScore.home.saves}
          />
          <BoxStatRow
            label="Sv%"
            awayValue={teamBoxScore.away.savePct}
            homeValue={teamBoxScore.home.savePct}
            awayVsAvg={vsSeasonAvg?.away?.savePct as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.savePct as VsSeasonAvgStat | undefined}
            isPct={true}
          />
          <BoxStatRow
            label="PIM"
            awayValue={teamBoxScore.away.pim}
            homeValue={teamBoxScore.home.pim}
            awayVsAvg={vsSeasonAvg?.away?.pim as VsSeasonAvgStat | undefined}
            homeVsAvg={vsSeasonAvg?.home?.pim as VsSeasonAvgStat | undefined}
            higherIsBetter={false}
          />
        </div>
      )}
    </div>
  );
}

export default async function NHLMatchupPage({ params }: Props) {
  const { gameId } = await params;
  const orderedSports = await fetchOrderedSportsWithCharts();
  const matchupData = await getNHLMatchupData();

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
        <NHLMatchupNav games={matchupData.dataPoints} selectedGameId={gameId} />

        {/* 50/50 split layout */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
          {/* Charts - square-ish dimensions */}
          <div className="flex flex-col gap-2 h-[700px] lg:h-[calc(100vh-80px)] order-2 lg:order-1">
            <div className="flex-1">
              <ChartDownloadWrapper title="Cumulative xGF% by Week" source={matchupData.source}>
                <CumXgChart
                  homeTeamStats={game.homeTeam.stats}
                  awayTeamStats={game.awayTeam.stats}
                  homeAbbrev={game.homeTeam.abbreviation}
                  awayAbbrev={game.awayTeam.abbreviation}
                  tenthXgfPctByWeek={game.tenthXgfPctByWeek}
                  leagueStats={game.leagueCumXgStats}
                />
              </ChartDownloadWrapper>
            </div>
            <div className="flex-1">
              <ChartDownloadWrapper title="xG For vs Points Pace" source={matchupData.source} quadrantLegend={[
                { label: 'Elite', color: '#22c55e' },
                { label: 'Lucky', color: '#f59e0b' },
                { label: 'Unlucky', color: '#3b82f6' },
                { label: 'Struggling', color: '#ef4444' },
              ]}>
                <XgVsPointsChart
                  homeTeamStats={game.homeTeam.stats}
                  awayTeamStats={game.awayTeam.stats}
                  homeAbbrev={game.homeTeam.abbreviation}
                  awayAbbrev={game.awayTeam.abbreviation}
                  leagueStats={game.leagueXgVsPointsStats}
                />
              </ChartDownloadWrapper>
            </div>
          </div>

          {/* Header + Results + Stat Comparisons - on top for mobile */}
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
              />
            ) : (
              <p className="text-[var(--muted)] text-sm">No detailed stats available for this matchup.</p>
            )}
            {game.homePlayers && game.awayPlayers && (
              <div className="mt-2">
                <NHLPlayerComparison
                  homePlayers={game.homePlayers}
                  awayPlayers={game.awayPlayers}
                  homeAbbrev={game.homeTeam.abbreviation}
                  awayAbbrev={game.awayTeam.abbreviation}
                />
              </div>
            )}
          </div>
        </div>

        <div className="mt-2 py-2 border-t border-[var(--border)] text-xs text-[var(--muted)]">
          {matchupData.source} · {new Date(matchupData.lastUpdated).toLocaleDateString()}
        </div>
      </div>
    </main>
  );
}
