'use client';

import { MatchupData } from '@/types/chart';

interface Props {
  data: MatchupData;
}

export function Matchup({ data }: Props) {
  return (
    <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {data.dataPoints.map((matchup, i) => (
        <div key={i} className="py-4">
          <div className="flex justify-between items-center mb-2">
            <span className="text-lg font-bold">{matchup.awayTeam}</span>
            <span className="text-[var(--muted)] text-sm">@</span>
            <span className="text-lg font-bold">{matchup.homeTeam}</span>
          </div>
          <div className="text-xs text-[var(--muted)] mb-3">
            {new Date(matchup.gameTime).toLocaleString(undefined, {
              weekday: 'short',
              month: 'short',
              day: 'numeric',
              hour: 'numeric',
              minute: '2-digit',
            })}
          </div>
          <div className="space-y-1">
            {matchup.comparisons.slice(0, 4).map((comp, j) => (
              <div key={j} className="flex justify-between text-xs">
                <span>{comp.awayTeamValue}</span>
                <span className="text-[var(--muted)]">{comp.title}</span>
                <span>{comp.homeTeamValue}</span>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
