import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { MLBMatchupData } from '@/types/chart';
import { MLBMatchupsClientRedirect } from '@/components/charts/MLBMatchupsClientRedirect';
import { getDefaultMLBMatchupGame } from '@/lib/mlbMatchups';
import {
  getPinnedTeamCodesForSport,
  getPinnedTeamsFromCookieValue,
  PINNED_TEAMS_COOKIE_KEY,
} from '@/lib/pinnedTeamsStorage';
import { cookies } from 'next/headers';
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

  const cookieStore = await cookies();
  const pinnedTeamsCookie = cookieStore.get(PINNED_TEAMS_COOKIE_KEY);

  if (pinnedTeamsCookie) {
    const pinnedTeams = getPinnedTeamsFromCookieValue(pinnedTeamsCookie.value);
    const pinnedMlbTeamCodes = getPinnedTeamCodesForSport(pinnedTeams, 'mlb');
    const targetGame = getDefaultMLBMatchupGame(matchupData.dataPoints, pinnedMlbTeamCodes);

    if (targetGame) {
      redirect(`/mlb/matchups/${targetGame.gameId}`);
    }
  }

  return <MLBMatchupsClientRedirect games={matchupData.dataPoints} />;
}
