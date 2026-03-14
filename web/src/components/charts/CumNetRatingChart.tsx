'use client';

import { useState } from 'react';
import { ResponsiveLine, LineCustomSvgLayerProps, LineSeries } from '@nivo/line';
import { NBATeamStats, LeagueCumNetRatingStats } from '@/types/chart';

interface Props {
  homeTeamStats: NBATeamStats;
  awayTeamStats: NBATeamStats;
  homeAbbrev: string;
  awayAbbrev: string;
  tenthNetRatingByWeek?: Record<string, number>;
  leagueStats?: LeagueCumNetRatingStats;
}

type CustomLayerProps = LineCustomSvgLayerProps<LineSeries>;

function parseWeekData(cumNetRatingByWeek: Record<string, number> | undefined): { week: number; value: number }[] {
  if (!cumNetRatingByWeek) return [];

  return Object.entries(cumNetRatingByWeek)
    .map(([key, value]) => {
      const weekNum = parseInt(key.replace('week-', ''), 10);
      return { week: weekNum, value };
    })
    .sort((a, b) => a.week - b.week);
}

function createClickableLegendLayer(
  seriesData: Array<{ id: string; color: string }>,
  selectedId: string | null,
  onSelect: (id: string | null) => void
) {
  return function ClickableLegendLayer({ innerWidth }: CustomLayerProps) {
    const legendX = innerWidth + 15;
    const itemHeight = 18;
    const startY = 0;

    return (
      <g>
        {seriesData.map((series, index) => {
          const isSelected = selectedId === series.id;
          const y = startY + index * itemHeight;

          return (
            <g
              key={series.id}
              transform={`translate(${legendX}, ${y})`}
              style={{ cursor: 'pointer' }}
              onClick={() => onSelect(isSelected ? null : series.id)}
            >
              {isSelected && (
                <rect
                  x={-4}
                  y={-2}
                  width={50}
                  height={itemHeight}
                  fill="var(--border)"
                  rx={2}
                />
              )}
              <circle
                cx={5}
                cy={itemHeight / 2 - 2}
                r={5}
                fill={series.color}
              />
              <text
                x={15}
                y={itemHeight / 2 + 1}
                style={{
                  fontSize: 10,
                  fontFamily: 'var(--font-geist-mono), monospace',
                  fill: 'var(--foreground)',
                  fontWeight: isSelected ? 700 : 400,
                }}
              >
                {series.id}
              </text>
            </g>
          );
        })}
      </g>
    );
  };
}

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export function CumNetRatingChart({ homeTeamStats, awayTeamStats, homeAbbrev, awayAbbrev, tenthNetRatingByWeek, leagueStats }: Props) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const homeData = parseWeekData(homeTeamStats.cumNetRatingByWeek);
  const awayData = parseWeekData(awayTeamStats.cumNetRatingByWeek);
  const tenthData = parseWeekData(tenthNetRatingByWeek);

  if (homeData.length === 0 && awayData.length === 0) {
    return (
      <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex items-center justify-center">
        <div className="text-center text-[var(--muted)]">
          <div className="text-sm font-bold mb-1">Cumulative Net Rating</div>
          <div className="text-xs">No data available</div>
        </div>
      </div>
    );
  }

  const seriesColors = [
    { id: awayAbbrev, color: '#ef4444' },
    { id: homeAbbrev, color: '#3b82f6' },
    ...(tenthData.length > 0 ? [{ id: '#10', color: '#22c55e' }] : []),
  ];

  const chartData = seriesColors.map(series => {
    const isSelected = !selectedId || selectedId === series.id;
    const color = isSelected ? series.color : hexToRgba(series.color, 0.15);
    let data: { x: number; y: number }[] = [];

    if (series.id === awayAbbrev) {
      data = awayData.map(d => ({ x: d.week, y: d.value }));
    } else if (series.id === homeAbbrev) {
      data = homeData.map(d => ({ x: d.week, y: d.value }));
    } else if (series.id === '#10') {
      data = tenthData.map(d => ({ x: d.week, y: d.value }));
    }

    return { id: series.id, color, data };
  });

  const ClickableLegendLayer = createClickableLegendLayer(seriesColors, selectedId, setSelectedId);

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex flex-col">
      <div className="text-center text-xs font-bold mb-1">Cumulative Net Rating by Week</div>
      <div className="flex-1 min-h-0">
        <ResponsiveLine
          data={chartData}
          margin={{ top: 10, right: 80, bottom: 40, left: 50 }}
          xScale={{ type: 'linear', min: 'auto', max: 'auto' }}
          yScale={{
            type: 'linear',
            min: leagueStats?.minCumNetRating ?? 'auto',
            max: leagueStats?.maxCumNetRating ?? 'auto',
            stacked: false,
          }}
          axisBottom={{
            tickSize: 0,
            tickPadding: 8,
            tickRotation: 0,
            legend: 'Week',
            legendOffset: 32,
            legendPosition: 'middle',
          }}
          axisLeft={{
            tickSize: 0,
            tickPadding: 8,
            tickRotation: 0,
            legend: 'Net Rtg',
            legendOffset: -40,
            legendPosition: 'middle',
          }}
          theme={{
            text: {
              fontFamily: 'var(--font-geist-mono), monospace',
              fill: 'var(--foreground)',
            },
            axis: {
              ticks: {
                text: {
                  fontSize: 10,
                  fontWeight: 600,
                  fill: 'var(--foreground)',
                },
              },
              legend: {
                text: {
                  fontSize: 11,
                  fontWeight: 700,
                  fill: 'var(--foreground)',
                },
              },
            },
            grid: {
              line: {
                stroke: 'var(--border)',
                strokeOpacity: 0.3,
              },
            },
            crosshair: {
              line: {
                stroke: 'var(--foreground)',
                strokeOpacity: 0.5,
              },
            },
            legends: {
              text: {
                fontSize: 11,
                fontFamily: 'var(--font-geist-mono), monospace',
                fill: 'var(--foreground)',
              },
            },
          }}
          colors={chartData.map(d => d.color)}
          lineWidth={2}
          pointSize={5}
          pointColor={{ theme: 'background' }}
          pointBorderWidth={2}
          pointBorderColor={{ from: 'serieColor' }}
          useMesh={true}
          enableSlices={false}
          layers={[
            'grid',
            'markers',
            'axes',
            'areas',
            'crosshair',
            'lines',
            'points',
            'slices',
            'mesh',
            ClickableLegendLayer,
          ]}
          tooltip={({ point }) => (
            <div className="bg-[var(--card)] border border-[var(--border)] px-2 py-1 rounded shadow-lg text-xs font-mono">
              <div className="flex items-center gap-2">
                <span
                  className="w-2 h-2 rounded-full"
                  style={{ backgroundColor: point.seriesColor }}
                />
                <span className="font-bold">{point.seriesId}</span>
              </div>
              <div className="text-[var(--muted)]">
                Week {String(point.data.x)}: {point.data.y >= 0 ? '+' : ''}{point.data.yFormatted}
              </div>
            </div>
          )}
        />
      </div>
    </div>
  );
}
