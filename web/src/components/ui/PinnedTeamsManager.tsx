'use client';

import { useState, useMemo } from 'react';
import { usePinnedTeams } from '@/lib/usePinnedTeams';
import type { TeamRoster, TeamRosterEntry } from '@/types/pinnedTeams';

interface Props {
  rosters: TeamRoster[];
}

function extractTeamLabel(longLabel: string): string {
  // longLabel is like "NFL - Philadelphia Eagles", extract the team name
  const idx = longLabel.indexOf(' - ');
  return idx >= 0 ? longLabel.slice(idx + 3) : longLabel;
}

export function PinnedTeamsManager({ rosters }: Props) {
  const { pinnedTeams, mounted, pinTeam, unpinTeam, isPinned } = usePinnedTeams();
  const [search, setSearch] = useState('');

  const filteredRosters = useMemo(() => {
    const query = search.toLowerCase().trim();
    if (!query) return rosters.map(r => ({ ...r, teams: r.teams }));

    return rosters
      .map(roster => ({
        ...roster,
        teams: roster.teams.filter(t =>
          t.code.toLowerCase().includes(query) ||
          t.longLabel.toLowerCase().includes(query) ||
          t.conference.toLowerCase().includes(query) ||
          t.division.toLowerCase().includes(query)
        ),
      }))
      .filter(r => r.teams.length > 0);
  }, [rosters, search]);

  const pinnedBySport = useMemo(() => {
    const grouped: Record<string, typeof pinnedTeams> = {};
    for (const t of pinnedTeams) {
      const key = t.sport.toUpperCase();
      if (!grouped[key]) grouped[key] = [];
      grouped[key].push(t);
    }
    return grouped;
  }, [pinnedTeams]);

  const handleToggle = (roster: TeamRoster, team: TeamRosterEntry) => {
    const sport = roster.sport.toLowerCase();
    if (isPinned(sport, team.code)) {
      unpinTeam(sport, team.code);
    } else {
      pinTeam(sport, team.code, extractTeamLabel(team.longLabel));
    }
  };

  if (!mounted) {
    return (
      <div>
        <h2 className="text-lg font-bold mb-4">Pinned Teams</h2>
        <p className="text-sm text-[var(--muted)]">Loading...</p>
      </div>
    );
  }

  return (
    <div>
      <h2 className="text-lg font-bold mb-4">Pinned Teams</h2>

      <div className="grid grid-cols-1 md:grid-cols-[1fr_1fr_280px] gap-4">
        {/* Panel 1 + 2: Search & Results */}
        <div className="md:col-span-2 flex flex-col min-h-0">
          {/* Search input */}
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search teams by name, code, conference, or division..."
            className="w-full px-3 py-2 text-sm border border-[var(--border)] rounded bg-[var(--background)] text-[var(--foreground)] placeholder:text-[var(--muted)] mb-3"
          />

          {/* Results */}
          <div className="overflow-y-auto max-h-[65vh] border border-[var(--border)] rounded">
            {filteredRosters.length === 0 ? (
              <p className="text-sm text-[var(--muted)] p-4">No teams match your search.</p>
            ) : (
              filteredRosters.map(roster => (
                <div key={roster.sport}>
                  {/* Sport header */}
                  <div className="sticky top-0 z-10 px-3 py-2 text-xs font-bold uppercase tracking-wider text-[var(--muted)] bg-[var(--card)] border-b border-[var(--border)]">
                    {roster.sport.toUpperCase()}
                  </div>

                  {/* Team rows */}
                  {roster.teams.map(team => {
                    const pinned = isPinned(roster.sport.toLowerCase(), team.code);
                    return (
                      <button
                        key={`${roster.sport}-${team.code}`}
                        onClick={() => handleToggle(roster, team)}
                        className={`w-full flex items-center gap-3 px-3 py-2 text-sm text-left border-b border-[var(--border)] last:border-b-0 transition-colors hover:bg-[var(--card)] ${
                          pinned ? 'bg-[var(--card)]' : ''
                        }`}
                      >
                        {/* Pin indicator */}
                        <span className={`shrink-0 w-5 h-5 flex items-center justify-center rounded border ${
                          pinned
                            ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
                            : 'border-[var(--border)]'
                        }`}>
                          {pinned && (
                            <svg className="w-3 h-3" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M2 6l3 3 5-5" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                          )}
                        </span>

                        {/* Team info */}
                        <span className="font-mono font-bold w-10">{team.code}</span>
                        <span className="flex-1 truncate">{extractTeamLabel(team.longLabel)}</span>
                        <span className="text-xs text-[var(--muted)] hidden sm:inline">{team.division}</span>
                      </button>
                    );
                  })}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Panel 3: Currently Pinned */}
        <div className="flex flex-col min-h-0">
          <div className="text-xs font-bold uppercase tracking-wider text-[var(--muted)] mb-2">
            Pinned ({pinnedTeams.length})
          </div>
          <div className="overflow-y-auto max-h-[65vh] border border-[var(--border)] rounded bg-[var(--card)]">
            {pinnedTeams.length === 0 ? (
              <p className="text-sm text-[var(--muted)] p-4">
                No pinned teams yet. Select teams from the list to pin them.
              </p>
            ) : (
              Object.entries(pinnedBySport).map(([sport, teams]) => (
                <div key={sport}>
                  <div className="px-3 py-1.5 text-xs font-bold uppercase tracking-wider text-[var(--muted)] bg-[var(--background)] border-b border-[var(--border)]">
                    {sport}
                  </div>
                  {teams.map(t => (
                    <div
                      key={`${t.sport}-${t.teamCode}`}
                      className="flex items-center justify-between px-3 py-2 text-sm border-b border-[var(--border)] last:border-b-0"
                    >
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold">{t.teamCode}</span>
                        <span className="truncate">{t.teamLabel}</span>
                      </div>
                      <button
                        onClick={() => unpinTeam(t.sport, t.teamCode)}
                        className="shrink-0 p-1 text-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
                        aria-label={`Remove ${t.teamLabel}`}
                      >
                        <svg className="w-4 h-4" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M4 4l8 8M12 4l-8 8" strokeLinecap="round" />
                        </svg>
                      </button>
                    </div>
                  ))}
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
