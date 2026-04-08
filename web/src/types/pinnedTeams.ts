export interface PinnedTeam {
  sport: string;
  teamCode: string;
  teamLabel: string;
  pinnedAt: string;
}

export interface TeamRosterEntry {
  code: string;
  longLabel: string;
  conference: string;
  division: string;
}

export interface TeamRoster {
  sport: string;
  lastUpdated: string;
  teams: TeamRosterEntry[];
}
