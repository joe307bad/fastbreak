import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { MLBMatchupData } from '@/types/chart';
import { MLBMatchupsClientRedirect } from '@/components/charts/MLBMatchupsClientRedirect';

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

  // Pinned teams used to be read here from the cookie the settings page writes,
  // so the redirect could happen server-side. A static export has no request to
  // read a cookie from, and the client component already does the same pick off
  // localStorage - falling back to today's game when nothing is pinned.
  return <MLBMatchupsClientRedirect games={matchupData.dataPoints} />;
}
