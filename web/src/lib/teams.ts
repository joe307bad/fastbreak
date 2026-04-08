import type { TeamRoster } from '@/types/pinnedTeams';

const CLOUDFRONT_URL = 'https://d2jyizt5xogu23.cloudfront.net';
const SPORTS = ['nfl', 'nba', 'nhl'] as const;

function getTeamRosterUrl(sport: string): string {
  const prefix = process.env.FASTBREAK_ENV === 'prod' ? '' : 'dev/';
  return `${CLOUDFRONT_URL}/${prefix}teams/${sport}__teams.json`;
}

export async function fetchAllTeamRosters(): Promise<TeamRoster[]> {
  const rosters = await Promise.all(
    SPORTS.map(async (sport) => {
      const res = await fetch(getTeamRosterUrl(sport));
      if (!res.ok) {
        throw new Error(`Failed to fetch ${sport} team roster: ${res.status}`);
      }
      return res.json() as Promise<TeamRoster>;
    })
  );
  return rosters;
}
