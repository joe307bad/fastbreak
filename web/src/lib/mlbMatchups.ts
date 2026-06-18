import { MLBMatchupDataPoint } from '@/types/chart';

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

function gameIncludesTeam(game: MLBMatchupDataPoint, teamCode: string): boolean {
  const code = teamCode.toUpperCase();
  return (
    game.awayTeam.abbreviation.toUpperCase() === code ||
    game.homeTeam.abbreviation.toUpperCase() === code
  );
}

export function findPinnedTeamGameForDay(
  games: MLBMatchupDataPoint[],
  dateKey: string,
  pinnedMlbTeamCodes: string[]
): MLBMatchupDataPoint | undefined {
  if (pinnedMlbTeamCodes.length === 0) return undefined;

  const dayGames = games.filter(game => getDateKey(new Date(game.gameDate)) === dateKey);
  for (const teamCode of pinnedMlbTeamCodes) {
    const match = dayGames.find(game => gameIncludesTeam(game, teamCode));
    if (match) return match;
  }

  return undefined;
}

export function getPreferredGameForDay(
  games: MLBMatchupDataPoint[],
  dateKey: string,
  pinnedMlbTeamCodes: string[],
  fallbackGame?: MLBMatchupDataPoint
): MLBMatchupDataPoint | undefined {
  return (
    findPinnedTeamGameForDay(games, dateKey, pinnedMlbTeamCodes) ??
    fallbackGame ??
    games.find(game => getDateKey(new Date(game.gameDate)) === dateKey)
  );
}

export function findNextMatchupForTeam(
  sortedGames: MLBMatchupDataPoint[],
  teamCode: string,
  todayKey = getTodayKey()
): MLBMatchupDataPoint | undefined {
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

export function getDefaultMLBMatchupGame(
  games: MLBMatchupDataPoint[],
  pinnedMlbTeamCodes: string[] = []
): MLBMatchupDataPoint | undefined {
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
