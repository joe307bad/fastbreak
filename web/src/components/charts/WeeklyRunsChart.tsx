'use client';

import { useState } from 'react';
import { ResponsiveScatterPlot } from '@nivo/scatterplot';
import { LeagueWeeklyStats, MLBTeamStats } from '@/types/chart';

interface Props {
  homeTeamStats: MLBTeamStats | null;
  awayTeamStats: MLBTeamStats | null;
  homeAbbrev: string;
  awayAbbrev: string;
  leagueStats?: LeagueWeeklyStats;
}

interface WeeklyRuns {
  week: number;
  runsScored: number;
  runsAllowed: number;
}

function parsePerformanceByWeek(stats: MLBTeamStats | null): WeeklyRuns[] {
  if (!stats?.performanceByWeek) return [];

  return Object.entries(stats.performanceByWeek)
    .map(([key, value]) => ({
      week: parseInt(key.replace('week-', ''), 10),
      runsScored: value.runsScored,
      runsAllowed: value.runsAllowed,
    }))
    .sort((a, b) => a.week - b.week);
}

export function WeeklyRunsChart({
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
          <div className="text-sm font-bold mb-1">Weekly Runs Scored vs Allowed</div>
          <div className="text-xs">No data available</div>
        </div>
      </div>
    );
  }

  const avgScored = leagueStats?.avgRunsScored ?? 4.5;
  const avgAllowed = leagueStats?.avgRunsAllowed ?? 4.5;

  const series = [
    { id: awayAbbrev, color: '#ef4444', data: awayWeeks },
    { id: homeAbbrev, color: '#3b82f6', data: homeWeeks },
  ];

  const chartData = series.map(s => ({
    id: s.id,
    data: s.data.map(w => ({
      x: w.runsScored,
      y: w.runsAllowed,
      week: w.week,
    })),
  }));

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex flex-col">
      <div className="text-center text-xs font-bold mb-1">Weekly Runs Scored vs Allowed</div>
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
          xScale={{ type: 'linear', min: leagueStats?.minRunsScored ?? 'auto', max: leagueStats?.maxRunsScored ?? 'auto' }}
          yScale={{
            type: 'linear',
            min: leagueStats?.minRunsAllowed ?? 'auto',
            max: leagueStats?.maxRunsAllowed ?? 'auto',
            reverse: true,
          }}
          axisBottom={{
            tickSize: 0,
            tickPadding: 8,
            legend: 'Runs Scored/G',
            legendOffset: 36,
            legendPosition: 'middle',
          }}
          axisLeft={{
            tickSize: 0,
            tickPadding: 8,
            legend: 'Runs Allowed/G',
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
          colors={series.map(s => s.color)}
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
              <div className="font-bold">{node.serieId} · Week {(node.data as { week: number }).week}</div>
              <div className="text-[var(--muted)]">
                {Number(node.data.x).toFixed(2)} RS / {Number(node.data.y).toFixed(2)} RA
              </div>
            </div>
          )}
        />
      </div>
    </div>
  );
}
