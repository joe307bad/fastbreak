import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { NFLMatchupData } from '@/types/chart';
import { redirect } from 'next/navigation';

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

function getDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default async function NFLMatchupsPage() {
  const matchupData = await getNFLMatchupData();

  if (!matchupData || matchupData.dataPoints.length === 0) {
    return (
      <main className="max-w-[2000px] mx-auto px-4">
        <div className="p-4">
          <p className="text-[var(--muted)]">No matchups available for NFL</p>
        </div>
      </main>
    );
  }

  const todayKey = getDateKey(new Date());

  const sortedGames = [...matchupData.dataPoints].sort(
    (a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime()
  );

  // Today's game if there is one, otherwise the next one up, otherwise the
  // most recent - a week with no football should still land somewhere real.
  let targetGame = sortedGames.find(g => getDateKey(new Date(g.gameDate)) === todayKey);

  if (!targetGame) {
    targetGame = sortedGames.find(g => getDateKey(new Date(g.gameDate)) > todayKey);
  }

  if (!targetGame) {
    targetGame = sortedGames[sortedGames.length - 1];
  }

  redirect(`/nfl/matchups/${targetGame.gameId}`);
}
