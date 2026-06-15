import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { MLBMatchupData } from '@/types/chart';
import { redirect } from 'next/navigation';

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

function getTodayKey(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default async function MLBMatchupsPage() {
  const matchupData = await getMLBMatchupData();

  if (!matchupData || matchupData.dataPoints.length === 0) {
    return (
      <main className="max-w-[2000px] mx-auto px-4">
        <div className="p-4">
          <p className="text-[var(--muted)]">No matchups available for MLB</p>
        </div>
      </main>
    );
  }

  const todayKey = getTodayKey();
  const sortedGames = [...matchupData.dataPoints].sort(
    (a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime()
  );

  let targetGame = sortedGames.find(g => getDateKey(new Date(g.gameDate)) === todayKey);
  if (!targetGame) {
    targetGame = sortedGames.find(g => getDateKey(new Date(g.gameDate)) > todayKey);
  }
  if (!targetGame) {
    targetGame = sortedGames[sortedGames.length - 1];
  }

  redirect(`/mlb/matchups/${targetGame.gameId}`);
}
