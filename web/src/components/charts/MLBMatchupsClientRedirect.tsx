'use client';

import { useLayoutEffect } from 'react';
import { useRouter } from 'next/navigation';
import { MLBMatchupDataPoint } from '@/types/chart';
import { getDefaultMLBMatchupGame } from '@/lib/mlbMatchups';
import {
  getPinnedTeamCodesForSport,
  readPinnedTeamsFromStorage,
  syncPinnedTeams,
} from '@/lib/pinnedTeamsStorage';

interface Props {
  games: MLBMatchupDataPoint[];
}

export function MLBMatchupsClientRedirect({ games }: Props) {
  const router = useRouter();

  useLayoutEffect(() => {
    const pinnedTeams = readPinnedTeamsFromStorage();
    syncPinnedTeams(pinnedTeams);

    const pinnedMlbTeamCodes = getPinnedTeamCodesForSport(pinnedTeams, 'mlb');
    const targetGame = getDefaultMLBMatchupGame(games, pinnedMlbTeamCodes);

    if (targetGame) {
      router.replace(`/mlb/matchups/${targetGame.gameId}`);
    }
  }, [games, router]);

  return null;
}
