import type { TeamRoster } from '@/types/pinnedTeams';
import { getRegistryPrefix } from '@/lib/registry';

const CLOUDFRONT_URL = 'https://d2jyizt5xogu23.cloudfront.net';
const SPORTS = ['nfl', 'nba', 'nhl', 'mlb'] as const;

function getTeamRosterUrl(sport: string): string {
  return `${CLOUDFRONT_URL}/${getRegistryPrefix()}teams/${sport}__teams.json`;
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
