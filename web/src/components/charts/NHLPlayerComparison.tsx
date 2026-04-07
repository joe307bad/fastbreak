'use client';

import { NHLPlayer } from '@/types/chart';

function getRankBadgeClasses(rank: number): string {
  const base = 'inline-flex items-center justify-center w-7 h-4 rounded text-[10px] font-medium';
  if (rank <= 10) return `${base} bg-green-500/20 text-green-500`;
  if (rank >= 21) return `${base} bg-red-500/20 text-red-500`;
  return `${base} bg-[var(--muted)]/20 text-[var(--muted)]`;
}

function formatPosition(pos: string): string {
  switch (pos) {
    case 'L': return 'LW';
    case 'R': return 'RW';
    case 'C': return 'C';
    case 'D': return 'D';
    default: return pos;
  }
}

function PlayerTable({ players, teamAbbrev }: { players: NHLPlayer[]; teamAbbrev: string }) {
  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)]">
      <div className="px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30">
        <span className="text-xs font-bold">{teamAbbrev}</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-[var(--border)] text-[var(--muted)]">
              <th className="px-1.5 py-1 text-left font-medium">Pos</th>
              <th className="px-1.5 py-1 text-left font-medium">Player</th>
              <th className="px-1.5 py-1 text-right font-medium">G</th>
              <th className="px-1.5 py-1 text-right font-medium">A</th>
              <th className="px-1.5 py-1 text-right font-medium">Pts</th>
              <th className="px-1.5 py-1 text-right font-medium">+/-</th>
            </tr>
          </thead>
          <tbody>
            {players.map((player, i) => (
              <tr key={i} className="border-b border-[var(--border)] last:border-b-0">
                <td className="px-1.5 py-1 text-[var(--muted)]">{formatPosition(player.position)}</td>
                <td className="px-1.5 py-1 font-medium whitespace-nowrap">{player.name}</td>
                <td className="px-1.5 py-1 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <span className="font-mono">{player.goals.value}</span>
                    <span className={getRankBadgeClasses(player.goals.rank)}>{player.goals.rankDisplay}</span>
                  </div>
                </td>
                <td className="px-1.5 py-1 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <span className="font-mono">{player.assists.value}</span>
                    <span className={getRankBadgeClasses(player.assists.rank)}>{player.assists.rankDisplay}</span>
                  </div>
                </td>
                <td className="px-1.5 py-1 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <span className="font-mono font-bold">{player.points.value}</span>
                    <span className={getRankBadgeClasses(player.points.rank)}>{player.points.rankDisplay}</span>
                  </div>
                </td>
                <td className="px-1.5 py-1 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <span className={`font-mono ${player.plusMinus.value >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                      {player.plusMinus.value >= 0 ? '+' : ''}{player.plusMinus.value}
                    </span>
                    <span className={getRankBadgeClasses(player.plusMinus.rank)}>{player.plusMinus.rankDisplay}</span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

interface NHLPlayerComparisonProps {
  homePlayers: NHLPlayer[];
  awayPlayers: NHLPlayer[];
  homeAbbrev: string;
  awayAbbrev: string;
}

export function NHLPlayerComparison({ homePlayers, awayPlayers, homeAbbrev, awayAbbrev }: NHLPlayerComparisonProps) {
  if (homePlayers.length === 0 && awayPlayers.length === 0) {
    return null;
  }

  return (
    <div>
      <div className="text-xs font-bold text-center mb-2">Key Players</div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        <PlayerTable players={awayPlayers} teamAbbrev={awayAbbrev} />
        <PlayerTable players={homePlayers} teamAbbrev={homeAbbrev} />
      </div>
    </div>
  );
}
