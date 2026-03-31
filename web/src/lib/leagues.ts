// League season dates (approximate)
// Ordered by proximity to championship without going past it
// Uses month-day format, year is determined at runtime

interface LeagueSeason {
  id: string;
  label: string;
  // Start of regular season (month-day)
  seasonStart: string;
  // Championship/finals end date (month-day)
  championshipEnd: string;
}

const LEAGUES: LeagueSeason[] = [
  {
    id: 'nfl',
    label: 'NFL',
    seasonStart: '09-05', // Early September
    championshipEnd: '02-15', // Super Bowl mid-February
  },
  {
    id: 'nba',
    label: 'NBA',
    seasonStart: '10-20', // Late October
    championshipEnd: '06-20', // Finals mid-June
  },
  {
    id: 'nhl',
    label: 'NHL',
    seasonStart: '10-10', // Early October
    championshipEnd: '06-25', // Stanley Cup late June
  },
];

function parseMonthDay(monthDay: string, year: number): Date {
  const [month, day] = monthDay.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function isInSeason(league: LeagueSeason, now: Date): boolean {
  const currentYear = now.getFullYear();
  const seasonStart = parseMonthDay(league.seasonStart, currentYear);
  let championshipEnd = parseMonthDay(league.championshipEnd, currentYear);

  // If championship is before season start, it's in the next year
  if (championshipEnd < seasonStart) {
    // Check if we're in the part of the season that spans years
    const lastYearStart = parseMonthDay(league.seasonStart, currentYear - 1);
    const thisYearEnd = parseMonthDay(league.championshipEnd, currentYear);

    // If we're between last year's start and this year's end
    if (now >= lastYearStart || now <= thisYearEnd) {
      return true;
    }

    // Or if we're between this year's start and next year's end
    const nextYearEnd = parseMonthDay(league.championshipEnd, currentYear + 1);
    if (now >= seasonStart && now <= nextYearEnd) {
      return true;
    }

    return false;
  }

  return now >= seasonStart && now <= championshipEnd;
}

function getDaysToChampionship(league: LeagueSeason, now: Date): number {
  const currentYear = now.getFullYear();
  let championshipEnd = parseMonthDay(league.championshipEnd, currentYear);
  const seasonStart = parseMonthDay(league.seasonStart, currentYear);

  // If championship is before season start in calendar year, adjust
  if (championshipEnd < seasonStart) {
    // If we're past the championship this year, use next year's
    if (now > championshipEnd) {
      championshipEnd = parseMonthDay(league.championshipEnd, currentYear + 1);
    }
  } else {
    // If we're past this year's championship, use next year's
    if (now > championshipEnd) {
      championshipEnd = parseMonthDay(league.championshipEnd, currentYear + 1);
    }
  }

  const diffTime = championshipEnd.getTime() - now.getTime();
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
}

export function getOrderedLeagues(): string[] {
  const now = new Date();

  // Separate leagues into in-season and off-season
  const inSeason: { id: string; daysToChamp: number }[] = [];
  const offSeason: { id: string; daysToChamp: number }[] = [];

  for (const league of LEAGUES) {
    const daysToChamp = getDaysToChampionship(league, now);

    if (isInSeason(league, now)) {
      inSeason.push({ id: league.id, daysToChamp });
    } else {
      offSeason.push({ id: league.id, daysToChamp });
    }
  }

  // Sort in-season leagues by days to championship (closest first)
  inSeason.sort((a, b) => a.daysToChamp - b.daysToChamp);

  // Sort off-season leagues by days to championship (closest first)
  offSeason.sort((a, b) => a.daysToChamp - b.daysToChamp);

  // In-season leagues come first, then off-season
  return [...inSeason, ...offSeason].map((l) => l.id);
}

export function getLeagues() {
  return LEAGUES;
}
