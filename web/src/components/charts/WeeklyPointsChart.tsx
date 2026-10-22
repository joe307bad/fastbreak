'use client';

import { useState } from 'react';
import { ResponsiveScatterPlot } from '@nivo/scatterplot';
import { LeagueWeeklyPointStats, NFLMatchupTeamStats } from '@/types/chart';

interface Props {
  homeTeamStats?: NFLMatchupTeamStats | null;
  awayTeamStats?: NFLMatchupTeamStats | null;
  homeAbbrev: string;
  awayAbbrev: string;
  leagueStats?: LeagueWeeklyPointStats;
}

interface WeeklyPoints {
  week: number;
  pointsScored: number;
  pointsAllowed: number;
}

function parsePerformanceByWeek(stats?: NFLMatchupTeamStats | null): WeeklyPoints[] {
  const byWeek = stats?.performanceByWeek;
  if (!byWeek) return [];

  return Object.entries(byWeek)
    .map(([key, value]) => ({
      week: parseInt(key.replace('week-', ''), 10),
      pointsScored: value.pointsScored,
      pointsAllowed: value.pointsAllowed,
    }))
    .sort((a, b) => a.week - b.week);
}

export function WeeklyPointsChart({
  homeTeamStats,
  awayTeamStats,
  homeAbbrev,
  awayAbbrev,
  leagueStats,
}: Props) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const homeWeeks = parsePerformanceByWeek(homeTeamStats);
  const awayWeeks = parsePerformanceByWeek(awayTeamStats);

  if (homeWeeks.length === 0 && awayWeeks.length === 0) {
    return (
      <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex items-center justify-center">
        <div className="text-center text-[var(--muted)]">
          <div className="text-sm font-bold mb-1">Weekly Points Scored vs Allowed</div>
          <div className="text-xs">No data available</div>
        </div>
      </div>
    );
  }

  // League averages anchor the quadrants: right of the vertical line is a
  // better-than-average offense, above the horizontal one a defense that gave
  // up more than the league does (the y axis is reversed, so up is worse).
  const avgScored = leagueStats?.avgPointsScored ?? 22;
  const avgAllowed = leagueStats?.avgPointsAllowed ?? 22;

  const series = [
    { id: awayAbbrev, color: '#ef4444', data: awayWeeks },
    { id: homeAbbrev, color: '#3b82f6', data: homeWeeks },
  ];

  const chartData = series.map(s => ({
    id: s.id,
    data: s.data.map(w => ({ x: w.pointsScored, y: w.pointsAllowed, week: w.week })),
  }));

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex flex-col">
      <div className="text-center text-xs font-bold mb-1">Weekly Points Scored vs Allowed</div>
      <div className="flex gap-2 justify-center mb-1">
        {series.map(s => (
          <button
            key={s.id}
            onClick={() => setSelectedId(selectedId === s.id ? null : s.id)}
            className={`flex items-center gap-1 px-2 py-0.5 text-[10px] rounded-full border transition-colors ${
              !selectedId || selectedId === s.id
                ? 'border-[var(--border)]'
                : 'opacity-40 border-transparent'
            }`}
          >
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: s.color }} />
            {s.id}
          </button>
        ))}
      </div>
      <div className="flex-1 min-h-0">
        <ResponsiveScatterPlot
          data={chartData.filter(s => !selectedId || s.id === selectedId)}
          margin={{ top: 10, right: 20, bottom: 50, left: 55 }}
          xScale={{
            type: 'linear',
            min: leagueStats?.minPointsScored ?? 'auto',
            max: leagueStats?.maxPointsScored ?? 'auto',
          }}
          yScale={{
            type: 'linear',
            min: leagueStats?.minPointsAllowed ?? 'auto',
            max: leagueStats?.maxPointsAllowed ?? 'auto',
            reverse: true,
          }}
          axisBottom={{
            tickSize: 0,
            tickPadding: 8,
            legend: 'Points Scored',
            legendOffset: 36,
            legendPosition: 'middle',
          }}
          axisLeft={{
            tickSize: 0,
            tickPadding: 8,
            legend: 'Points Allowed',
            legendOffset: -45,
            legendPosition: 'middle',
          }}
          theme={{
            text: { fontFamily: 'var(--font-geist-mono), monospace', fill: 'var(--foreground)' },
            axis: {
              ticks: { text: { fontSize: 10, fontWeight: 600, fill: 'var(--foreground)' } },
              legend: { text: { fontSize: 11, fontWeight: 700, fill: 'var(--foreground)' } },
            },
            grid: { line: { stroke: 'var(--border)', strokeOpacity: 0.3 } },
          }}
          colors={series.filter(s => !selectedId || s.id === selectedId).map(s => s.color)}
          nodeSize={8}
          markers={[
            {
              axis: 'x',
              value: avgScored,
              lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
            },
            {
              axis: 'y',
              value: avgAllowed,
              lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
            },
          ]}
          tooltip={({ node }) => (
            <div className="bg-[var(--card)] border border-[var(--border)] px-2 py-1 rounded shadow-lg text-xs font-mono">
              <div className="font-bold">
                {node.serieId} · Week {(node.data as { week: number }).week}
              </div>
              <div className="text-[var(--muted)]">
                {Number(node.data.x)} scored / {Number(node.data.y)} allowed
              </div>
            </div>
          )}
        />
      </div>
    </div>
  );
}
