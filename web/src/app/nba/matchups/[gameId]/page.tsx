import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { NBAMatchupData, NBAMatchupDataPoint, NBAMatchupResults } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { NBAMatchupNav } from '@/components/ui/NBAMatchupNav';
import { MatchupComparisons } from '@/components/charts/MatchupComparisons';
import { CumNetRatingChart } from '@/components/charts/CumNetRatingChart';
import { WeeklyEfficiencyChart } from '@/components/charts/WeeklyEfficiencyChart';
import { getOrderedLeagues } from '@/lib/leagues';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ gameId: string }>;
}

async function getNBAMatchupData(): Promise<NBAMatchupData | null> {
  const registry = await fetchChartRegistry();
  const keys = Object.keys(registry);

  for (const key of keys) {
    const data = await fetchChartData(key);
    if (data.sport?.toLowerCase() === 'nba' && data.visualizationType === 'NBA_MATCHUP') {
      return data as NBAMatchupData;
    }
  }

  return null;
}

export async function generateStaticParams() {
  const matchupData = await getNBAMatchupData();
  if (!matchupData) return [];

  return matchupData.dataPoints.map(game => ({
    gameId: game.gameId,
  }));
}

export async function generateMetadata({ params }: Props) {
  const { gameId } = await params;
  const matchupData = await getNBAMatchupData();
  const game = matchupData?.dataPoints.find(g => g.gameId === gameId);

  if (!game) {
    return { title: 'NBA Matchup' };
  }

  return {
    title: `${game.awayTeam.abbreviation} @ ${game.homeTeam.abbreviation} - NBA Matchup`,
    description: `${game.awayTeam.name} vs ${game.homeTeam.name} matchup analysis`,
  };
}

function formatGameTime(gameTime: string): { date: string; time: string } {
  const d = new Date(gameTime);
  return {
    date: d.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }),
    time: d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }),
  };
}

function MatchupHeader({ game }: { game: NBAMatchupDataPoint }) {
  const { date, time } = formatGameTime(game.gameDate);
  const isCompleted = game.gameCompleted;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      {/* Date/Time */}
      <div className="text-center text-xs text-[var(--muted)] mb-2">
        {date} · {isCompleted ? 'Final' : time}
        {game.odds && (
          <span className="ml-4">
            {game.odds.spread && <span>Spread: {game.odds.spread}</span>}
            {game.odds.overUnder && <span className="ml-2">O/U: {game.odds.overUnder}</span>}
          </span>
        )}
      </div>

      {/* Teams - 5 column grid matching stat comparisons */}
      <div className="grid grid-cols-5 gap-2 text-sm">
        {/* Away Team Info */}
        <div className="text-right">
          <div className="font-bold">{game.awayTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{game.awayTeam.name}</div>
        </div>

        {/* Away Record/Rank */}
        <div className="text-right text-xs text-[var(--muted)]">
          <div>{game.awayTeam.wins}-{game.awayTeam.losses}</div>
          <div>#{game.awayTeam.conferenceRank} {game.awayTeam.conference}</div>
        </div>

        {/* Center - Net Ratings */}
        <div className="text-center">
          <div className={`font-mono text-xs ${game.awayTeam.stats.netRating >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {game.awayTeam.stats.netRating >= 0 ? '+' : ''}{game.awayTeam.stats.netRating.toFixed(1)}
          </div>
          <div className="text-[10px] text-[var(--muted)]">Net Rtg</div>
          <div className={`font-mono text-xs ${game.homeTeam.stats.netRating >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {game.homeTeam.stats.netRating >= 0 ? '+' : ''}{game.homeTeam.stats.netRating.toFixed(1)}
          </div>
        </div>

        {/* Home Record/Rank */}
        <div className="text-left text-xs text-[var(--muted)]">
          <div>{game.homeTeam.wins}-{game.homeTeam.losses}</div>
          <div>#{game.homeTeam.conferenceRank} {game.homeTeam.conference}</div>
        </div>

        {/* Home Team Info */}
        <div className="text-left">
          <div className="font-bold">{game.homeTeam.abbreviation}</div>
          <div className="text-xs text-[var(--muted)]">{game.homeTeam.name}</div>
        </div>
      </div>
    </div>
  );
}

function formatDiff(diff: number | undefined, isPct?: boolean): string {
  if (diff === undefined || diff === null) return '-';
  const sign = diff >= 0 ? '+' : '';
  if (isPct) {
    return `${sign}${(diff * 100).toFixed(1)}%`;
  }
  return `${sign}${diff.toFixed(1)}`;
}

function MatchupResults({ results, awayAbbrev, homeAbbrev }: {
  results: NBAMatchupResults;
  awayAbbrev: string;
  homeAbbrev: string;
}) {
  const { finalScore, winner, margin, homeWon, teamBoxScore, vsSeasonAvg } = results;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 mb-2">
      {/* Final Score - 5 column grid */}
      <div className="grid grid-cols-5 gap-2 text-sm items-center">
        <div className={`text-right text-2xl font-bold ${!homeWon ? 'text-green-500' : ''}`}>
          {finalScore.away}
        </div>
        <div className="text-right text-xs text-[var(--muted)]">
          {!homeWon && <span className="text-green-500">W</span>}
        </div>
        <div className="text-center">
          <div className="text-xs text-[var(--muted)]">Final</div>
          <div className="text-xs font-mono">{winner} +{margin}</div>
        </div>
        <div className="text-left text-xs text-[var(--muted)]">
          {homeWon && <span className="text-green-500">W</span>}
        </div>
        <div className={`text-left text-2xl font-bold ${homeWon ? 'text-green-500' : ''}`}>
          {finalScore.home}
        </div>
      </div>

      {/* Box Score Summary with vs Season Avg inline */}
      {teamBoxScore && (
        <div className="mt-2 pt-2 border-t border-[var(--border)]">
          <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs text-[var(--muted)] items-center px-1">
            <div className="text-right font-bold">{awayAbbrev}</div>
            <div className="text-center font-bold">Box (vs Avg)</div>
            <div className="text-left font-bold">{homeAbbrev}</div>
          </div>
          {/* FG */}
          {(() => {
            const awayPct = teamBoxScore.away.fgPct ?? (teamBoxScore.away.fgm / teamBoxScore.away.fga);
            const homePct = teamBoxScore.home.fgPct ?? (teamBoxScore.home.fgm / teamBoxScore.home.fga);
            const awayEdge = awayPct > homePct;
            const homeEdge = homePct > awayPct;
            return (
              <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5">
                <div className="flex items-center justify-end gap-1">
                  <span className="w-3 text-right shrink-0">
                    {awayEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
                  </span>
                  <span className="w-10 font-mono text-right shrink-0">
                    {teamBoxScore.away.fgm}-{teamBoxScore.away.fga}
                  </span>
                  <span className={`w-12 text-right shrink-0 ${vsSeasonAvg?.away?.fieldGoalPct ? (vsSeasonAvg.away.fieldGoalPct.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.away?.fieldGoalPct ? formatDiff(vsSeasonAvg.away.fieldGoalPct.difference, true) : ''}
                  </span>
                </div>
                <div className="text-center text-[var(--muted)]">FG</div>
                <div className="flex items-center justify-start gap-1">
                  <span className={`w-12 text-left shrink-0 ${vsSeasonAvg?.home?.fieldGoalPct ? (vsSeasonAvg.home.fieldGoalPct.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.home?.fieldGoalPct ? formatDiff(vsSeasonAvg.home.fieldGoalPct.difference, true) : ''}
                  </span>
                  <span className="w-10 font-mono text-left shrink-0">
                    {teamBoxScore.home.fgm}-{teamBoxScore.home.fga}
                  </span>
                  <span className="w-3 text-left shrink-0">
                    {homeEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
                  </span>
                </div>
              </div>
            );
          })()}
          {/* 3PT */}
          {teamBoxScore.away.fg3m !== undefined && (() => {
            const awayPct = teamBoxScore.away.fg3Pct ?? ((teamBoxScore.away.fg3m ?? 0) / (teamBoxScore.away.fg3a ?? 1));
            const homePct = teamBoxScore.home.fg3Pct ?? ((teamBoxScore.home.fg3m ?? 0) / (teamBoxScore.home.fg3a ?? 1));
            const awayEdge = awayPct > homePct;
            const homeEdge = homePct > awayPct;
            return (
              <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5">
                <div className="flex items-center justify-end gap-1">
                  <span className="w-3 text-right shrink-0">
                    {awayEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
                  </span>
                  <span className="w-10 font-mono text-right shrink-0">
                    {teamBoxScore.away.fg3m}-{teamBoxScore.away.fg3a}
                  </span>
                  <span className={`w-12 text-right shrink-0 ${vsSeasonAvg?.away?.threePtPct ? (vsSeasonAvg.away.threePtPct.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.away?.threePtPct ? formatDiff(vsSeasonAvg.away.threePtPct.difference, true) : ''}
                  </span>
                </div>
                <div className="text-center text-[var(--muted)]">3PT</div>
                <div className="flex items-center justify-start gap-1">
                  <span className={`w-12 text-left shrink-0 ${vsSeasonAvg?.home?.threePtPct ? (vsSeasonAvg.home.threePtPct.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.home?.threePtPct ? formatDiff(vsSeasonAvg.home.threePtPct.difference, true) : ''}
                  </span>
                  <span className="w-10 font-mono text-left shrink-0">
                    {teamBoxScore.home.fg3m}-{teamBoxScore.home.fg3a}
                  </span>
                  <span className="w-3 text-left shrink-0">
                    {homeEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
                  </span>
                </div>
              </div>
            );
          })()}
          {/* REB */}
          {teamBoxScore.away.reb !== undefined && (() => {
            const awayVal = teamBoxScore.away.reb ?? 0;
            const homeVal = teamBoxScore.home.reb ?? 0;
            const awayEdge = awayVal > homeVal;
            const homeEdge = homeVal > awayVal;
            return (
              <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5">
                <div className="flex items-center justify-end gap-1">
                  <span className="w-3 text-right shrink-0">
                    {awayEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
                  </span>
                  <span className="w-10 font-mono text-right shrink-0">
                    {teamBoxScore.away.reb}
                  </span>
                  <span className={`w-12 text-right shrink-0 ${vsSeasonAvg?.away?.rebounds ? (vsSeasonAvg.away.rebounds.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.away?.rebounds ? formatDiff(vsSeasonAvg.away.rebounds.difference) : ''}
                  </span>
                </div>
                <div className="text-center text-[var(--muted)]">REB</div>
                <div className="flex items-center justify-start gap-1">
                  <span className={`w-12 text-left shrink-0 ${vsSeasonAvg?.home?.rebounds ? (vsSeasonAvg.home.rebounds.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.home?.rebounds ? formatDiff(vsSeasonAvg.home.rebounds.difference) : ''}
                  </span>
                  <span className="w-10 font-mono text-left shrink-0">
                    {teamBoxScore.home.reb}
                  </span>
                  <span className="w-3 text-left shrink-0">
                    {homeEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
                  </span>
                </div>
              </div>
            );
          })()}
          {/* AST */}
          {teamBoxScore.away.ast !== undefined && (() => {
            const awayVal = teamBoxScore.away.ast ?? 0;
            const homeVal = teamBoxScore.home.ast ?? 0;
            const awayEdge = awayVal > homeVal;
            const homeEdge = homeVal > awayVal;
            return (
              <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5">
                <div className="flex items-center justify-end gap-1">
                  <span className="w-3 text-right shrink-0">
                    {awayEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
                  </span>
                  <span className="w-10 font-mono text-right shrink-0">
                    {teamBoxScore.away.ast}
                  </span>
                  <span className={`w-12 text-right shrink-0 ${vsSeasonAvg?.away?.assists ? (vsSeasonAvg.away.assists.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.away?.assists ? formatDiff(vsSeasonAvg.away.assists.difference) : ''}
                  </span>
                </div>
                <div className="text-center text-[var(--muted)]">AST</div>
                <div className="flex items-center justify-start gap-1">
                  <span className={`w-12 text-left shrink-0 ${vsSeasonAvg?.home?.assists ? (vsSeasonAvg.home.assists.difference >= 0 ? 'text-green-500' : 'text-red-500') : ''}`}>
                    {vsSeasonAvg?.home?.assists ? formatDiff(vsSeasonAvg.home.assists.difference) : ''}
                  </span>
                  <span className="w-10 font-mono text-left shrink-0">
                    {teamBoxScore.home.ast}
                  </span>
                  <span className="w-3 text-left shrink-0">
                    {homeEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
                  </span>
                </div>
              </div>
            );
          })()}
          {/* TOV - lower is better */}
          {teamBoxScore.away.tov !== undefined && (() => {
            const awayVal = teamBoxScore.away.tov ?? 0;
            const homeVal = teamBoxScore.home.tov ?? 0;
            const awayEdge = awayVal < homeVal;
            const homeEdge = homeVal < awayVal;
            return (
              <div className="grid grid-cols-[1fr_minmax(50px,1fr)_1fr] gap-0 text-xs items-center py-0.5">
                <div className="flex items-center justify-end gap-1">
                  <span className="w-3 text-right shrink-0">
                    {awayEdge && <span className="text-green-500 text-[10px]">&#9664;</span>}
                  </span>
                  <span className="w-10 font-mono text-right shrink-0">{teamBoxScore.away.tov}</span>
                  <span className="w-12 shrink-0"></span>
                </div>
                <div className="text-center text-[var(--muted)]">TOV</div>
                <div className="flex items-center justify-start gap-1">
                  <span className="w-12 shrink-0"></span>
                  <span className="w-10 font-mono text-left shrink-0">{teamBoxScore.home.tov}</span>
                  <span className="w-3 text-left shrink-0">
                    {homeEdge && <span className="text-green-500 text-[10px]">&#9654;</span>}
                  </span>
                </div>
              </div>
            );
          })()}
        </div>
      )}
    </div>
  );
}

export default async function NBAMatchupPage({ params }: Props) {
  const { gameId } = await params;
  const orderedSports = getOrderedLeagues();
  const matchupData = await getNBAMatchupData();

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
        <NBAMatchupNav games={matchupData.dataPoints} selectedGameId={gameId} />

        {/* 50/50 split layout */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
          {/* Charts - viewport height on desktop */}
          <div className="flex flex-col gap-2 h-[400px] lg:h-[calc(100vh-120px)] order-2 lg:order-1">
            <div className="flex-1">
              <CumNetRatingChart
                homeTeamStats={game.homeTeam.stats}
                awayTeamStats={game.awayTeam.stats}
                homeAbbrev={game.homeTeam.abbreviation}
                awayAbbrev={game.awayTeam.abbreviation}
                tenthNetRatingByWeek={game.tenthNetRatingByWeek}
                leagueStats={game.leagueCumNetRatingStats}
              />
            </div>
            <div className="flex-1">
              <WeeklyEfficiencyChart
                homeTeamStats={game.homeTeam.stats}
                awayTeamStats={game.awayTeam.stats}
                homeAbbrev={game.homeTeam.abbreviation}
                awayAbbrev={game.awayTeam.abbreviation}
                leagueStats={game.leagueEfficiencyStats}
              />
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
          </div>
        </div>

        <div className="mt-2 py-2 border-t border-[var(--border)] text-xs text-[var(--muted)]">
          {matchupData.source} · {new Date(matchupData.lastUpdated).toLocaleDateString()}
        </div>
      </div>
    </main>
  );
}
