import { getDateKey, getTodayKey } from '@/lib/mlbMatchups';

/**
 * The slice of a matchup data point this module needs: when the game is, and
 * what to navigate to. Every sport's data point satisfies it.
 */
export interface DatedGame {
  gameId: string;
  gameDate: string;
}

/**
 * Today's game if there is one, otherwise the next one up, otherwise the most
 * recent — an off-day should still land somewhere real.
 *
 * This runs in the browser rather than at build time on purpose. The site is
 * exported once a day, so "today" at build time goes stale within hours; asking
 * the visitor's clock means a build from yesterday still opens today's game.
 */
export function getDefaultMatchupGame<T extends DatedGame>(games: T[]): T | undefined {
  if (games.length === 0) return undefined;

  const sortedGames = [...games].sort(
    (a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime()
  );
  const todayKey = getTodayKey();

  return (
    sortedGames.find(game => getDateKey(new Date(game.gameDate)) === todayKey) ??
    sortedGames.find(game => getDateKey(new Date(game.gameDate)) > todayKey) ??
    sortedGames[sortedGames.length - 1]
  );
}
