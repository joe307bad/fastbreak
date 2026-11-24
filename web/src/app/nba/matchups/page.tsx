import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { NBAMatchupData } from '@/types/chart';
import { MatchupsClientRedirect } from '@/components/charts/MatchupsClientRedirect';

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

export default async function NBAMatchupsPage() {
  const matchupData = await getNBAMatchupData();

  if (!matchupData || matchupData.dataPoints.length === 0) {
    return (
      <main className="max-w-[2000px] mx-auto px-4">
        <div className="p-4">
          <p className="text-[var(--muted)]">No matchups available for NBA</p>
        </div>
      </main>
    );
  }

  // Which game to open is decided in the browser, not here: this page is
  // exported once a day, and "today" has to mean the visitor's today.
  //
  // Only the id and the date cross into the client component. Handing it
  // `dataPoints` instead would serialize every game's players, box score and
  // league splits into this page to choose between two fields of them - 3 MB
  // of payload for a page whose entire job is to redirect.
  const schedule = matchupData.dataPoints.map(game => ({
    gameId: game.gameId,
    gameDate: game.gameDate,
  }));

  return <MatchupsClientRedirect sport="nba" games={schedule} />;
}
