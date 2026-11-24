'use client';

import { useLayoutEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getDefaultMatchupGame, type DatedGame } from '@/lib/matchupDefaults';

interface Props {
  sport: string;
  games: DatedGame[];
}

/**
 * The matchup index pages used to answer with a server redirect. A static
 * export has no server to answer with, so the hop happens here instead: the
 * exported page is an empty shell that picks a game against the visitor's own
 * clock and replaces itself with it.
 */
export function MatchupsClientRedirect({ sport, games }: Props) {
  const router = useRouter();

  useLayoutEffect(() => {
    const targetGame = getDefaultMatchupGame(games);
    if (targetGame) {
      router.replace(`/${sport}/matchups/${targetGame.gameId}`);
    }
  }, [games, router, sport]);

  return null;
}
