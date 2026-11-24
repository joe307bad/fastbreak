import type { MatchupNavGame } from '@/lib/matchupNav';

/**
 * These helpers only ever read a game's date and its two abbreviations, so they
 * are generic over anything with that shape rather than tied to a full data
 * point. That lets the nav call them with the slim list it is handed (see
 * `matchupNav.ts`) while a caller holding real data points still gets a real
 * data point back.
 */

export function getTodayKey(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function gameIncludesTeam(game: MatchupNavGame, teamCode: string): boolean {
  const code = teamCode.toUpperCase();
  return (
    game.awayTeam.abbreviation.toUpperCase() === code ||
    game.homeTeam.abbreviation.toUpperCase() === code
  );
}

export function findPinnedTeamGameForDay<T extends MatchupNavGame>(
  games: T[],
  dateKey: string,
  pinnedMlbTeamCodes: string[]
): T | undefined {
  if (pinnedMlbTeamCodes.length === 0) return undefined;

  const dayGames = games.filter(game => getDateKey(new Date(game.gameDate)) === dateKey);
  for (const teamCode of pinnedMlbTeamCodes) {
    const match = dayGames.find(game => gameIncludesTeam(game, teamCode));
    if (match) return match;
  }

  return undefined;
}

export function getPreferredGameForDay<T extends MatchupNavGame>(
  games: T[],
  dateKey: string,
  pinnedMlbTeamCodes: string[],
  fallbackGame?: T
): T | undefined {
  return (
    findPinnedTeamGameForDay(games, dateKey, pinnedMlbTeamCodes) ??
    fallbackGame ??
    games.find(game => getDateKey(new Date(game.gameDate)) === dateKey)
  );
}

export function findNextMatchupForTeam<T extends MatchupNavGame>(
  sortedGames: T[],
  teamCode: string,
  todayKey = getTodayKey()
): T | undefined {
  const teamGames = sortedGames.filter(game => gameIncludesTeam(game, teamCode));

  const futureGame = teamGames.find(
    game => getDateKey(new Date(game.gameDate)) >= todayKey
  );
  if (futureGame) return futureGame;

  for (let index = teamGames.length - 1; index >= 0; index -= 1) {
    const game = teamGames[index];
    if (getDateKey(new Date(game.gameDate)) < todayKey) {
      return game;
    }
  }

  return undefined;
}

export function getDefaultMLBMatchupGame<T extends MatchupNavGame>(
  games: T[],
  pinnedMlbTeamCodes: string[] = []
): T | undefined {
  if (games.length === 0) return undefined;

  const sortedGames = [...games].sort(
    (a, b) => new Date(a.gameDate).getTime() - new Date(b.gameDate).getTime()
  );
  const todayKey = getTodayKey();

  for (const teamCode of pinnedMlbTeamCodes) {
    const pinnedGame = findNextMatchupForTeam(sortedGames, teamCode, todayKey);
    if (pinnedGame) return pinnedGame;
  }

  let targetGame = sortedGames.find(game => getDateKey(new Date(game.gameDate)) === todayKey);
  if (!targetGame) {
    targetGame = sortedGames.find(game => getDateKey(new Date(game.gameDate)) > todayKey);
  }
  if (!targetGame) {
    targetGame = sortedGames[sortedGames.length - 1];
  }

  return targetGame;
}
