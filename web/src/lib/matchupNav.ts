/**
 * The slice of a matchup that its nav strip needs.
 *
 * The nav lists every game in the schedule so you can jump between them, and
 * each badge is two team abbreviations grouped under a day (or, for football, a
 * week). That is all of it -- no players, no box score, no comparisons.
 *
 * It matters because the nav is a client component, so whatever it is handed
 * crosses into the page as serialized props, twice: once in the HTML and again
 * in the RSC payload beside it. Handing it `dataPoints` put the entire league's
 * schedule *in full* into every single game page -- 1 to 3 MB a page, to render
 * a row of abbreviations from it. Mapping down to this shape first is a ~100x
 * cut, and the nav renders identically from it.
 *
 * Every sport's data point satisfies this structurally, so a page can pass its
 * own typed games straight into `toMatchupNavGames` with nothing to cast.
 */
export interface MatchupNavGame {
  gameId: string;
  gameDate: string;
  awayTeam: { abbreviation: string };
  homeTeam: { abbreviation: string };
}

/** Football is grouped by week rather than by day, so its nav needs the week too. */
export interface NFLMatchupNavGame extends MatchupNavGame {
  season?: number;
  week?: number;
  seasonType?: string;
  seasonTypeLabel?: string;
}

/**
 * Rebuild each game as a fresh object rather than passing it through. A spread
 * of the original would carry every other key with it, which is the whole thing
 * this exists to avoid.
 */
export function toMatchupNavGames(games: MatchupNavGame[]): MatchupNavGame[] {
  return games.map(game => ({
    gameId: game.gameId,
    gameDate: game.gameDate,
    awayTeam: { abbreviation: game.awayTeam.abbreviation },
    homeTeam: { abbreviation: game.homeTeam.abbreviation },
  }));
}

export function toNFLMatchupNavGames(games: NFLMatchupNavGame[]): NFLMatchupNavGame[] {
  return games.map(game => ({
    gameId: game.gameId,
    gameDate: game.gameDate,
    awayTeam: { abbreviation: game.awayTeam.abbreviation },
    homeTeam: { abbreviation: game.homeTeam.abbreviation },
    season: game.season,
    week: game.week,
    seasonType: game.seasonType,
    seasonTypeLabel: game.seasonTypeLabel,
  }));
}
