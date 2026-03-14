'use client';

import { useState } from 'react';
import { ResponsiveScatterPlot, ScatterPlotLayerProps } from '@nivo/scatterplot';
import { NBATeamStats, LeagueEfficiencyStats } from '@/types/chart';

interface Props {
  homeTeamStats: NBATeamStats;
  awayTeamStats: NBATeamStats;
  homeAbbrev: string;
  awayAbbrev: string;
  leagueStats?: LeagueEfficiencyStats;
}

interface WeeklyEfficiency {
  week: number;
  offRating: number;
  defRating: number;
}

interface NodeData {
  x: number;
  y: number;
  week: number;
}

type WeekFilter = 'all' | 'last5' | 'prior5';

function WeekFilterNav({ filter, onFilterChange }: { filter: WeekFilter; onFilterChange: (f: WeekFilter) => void }) {
  const options: { value: WeekFilter; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'last5', label: 'Last 5' },
    { value: 'prior5', label: 'Prior 5' },
  ];

  return (
    <div className="flex gap-1 justify-center mb-1">
      {options.map(opt => (
        <button
          key={opt.value}
          onClick={() => onFilterChange(opt.value)}
          className={`px-2 py-0.5 text-[10px] rounded-full border transition-colors ${
            filter === opt.value
              ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
              : 'bg-transparent text-[var(--muted)] border-[var(--border)] hover:border-[var(--foreground)] hover:text-[var(--foreground)]'
          }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

function createClickableLegendLayer(
  seriesData: Array<{ id: string; color: string }>,
  selectedId: string | null,
  onSelect: (id: string | null) => void
) {
  return function ClickableLegendLayer({ innerWidth }: ScatterPlotLayerProps<NodeData>) {
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

function createNodesLayer(
  seriesColors: Array<{ id: string; color: string }>,
  selectedId: string | null,
  weekFilter: WeekFilter,
  maxWeek: number
) {
  return function NodesLayer({ nodes }: ScatterPlotLayerProps<NodeData>) {
    return (
      <g>
        {nodes.map(node => {
          const seriesColor = seriesColors.find(s => s.id === node.serieId)?.color || '#888';
          const weekNum = (node.data as NodeData).week;

          // Check if week matches the filter
          const isWeekSelected = weekFilter === 'all' ||
            (weekFilter === 'last5' && weekNum > maxWeek - 5) ||
            (weekFilter === 'prior5' && weekNum <= maxWeek - 5 && weekNum > maxWeek - 10);

          // Check if series matches the legend filter
          const isSeriesSelected = !selectedId || selectedId === node.serieId;

          // Point is highlighted if both filters pass (or their respective filter is "all"/null)
          const isHighlighted = isWeekSelected && isSeriesSelected;
          const fillOpacity = isHighlighted ? 1 : 0.15;

          return (
            <g key={node.id}>
              <circle
                cx={node.x}
                cy={node.y}
                r={5}
                fill={seriesColor}
                fillOpacity={fillOpacity}
                stroke="var(--background)"
                strokeWidth={1}
                strokeOpacity={fillOpacity}
              />
              {isHighlighted && (
                <text
                  x={node.x}
                  y={node.y - 8}
                  textAnchor="middle"
                  style={{
                    fontSize: 8,
                    fontFamily: 'var(--font-geist-mono), monospace',
                    fill: seriesColor,
                    fontWeight: 600,
                  }}
                >
                  W{weekNum}
                </text>
              )}
            </g>
          );
        })}
      </g>
    );
  };
}

function parseEfficiencyData(stats: NBATeamStats): WeeklyEfficiency[] {
  const efficiencyByWeek = stats.efficiencyByWeek;
  if (!efficiencyByWeek) return [];

  return Object.entries(efficiencyByWeek)
    .map(([key, value]) => {
      const weekNum = parseInt(key.replace('week-', ''), 10);
      return {
        week: weekNum,
        offRating: value.offRating,
        defRating: value.defRating,
      };
    })
    .sort((a, b) => a.week - b.week);
}

export function WeeklyEfficiencyChart({ homeTeamStats, awayTeamStats, homeAbbrev, awayAbbrev, leagueStats }: Props) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [weekFilter, setWeekFilter] = useState<WeekFilter>('all');
  const homeData = parseEfficiencyData(homeTeamStats);
  const awayData = parseEfficiencyData(awayTeamStats);

  if (homeData.length === 0 && awayData.length === 0) {
    return (
      <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex items-center justify-center">
        <div className="text-center text-[var(--muted)]">
          <div className="text-sm font-bold mb-1">Weekly Efficiency</div>
          <div className="text-xs">No data available</div>
        </div>
      </div>
    );
  }

  // Find max week for filtering
  const allWeeks = [...homeData, ...awayData].map(d => d.week);
  const maxWeek = Math.max(...allWeeks);

  const seriesColors = [
    { id: awayAbbrev, color: '#ef4444' },
    { id: homeAbbrev, color: '#3b82f6' },
  ];

  const chartData = [
    {
      id: awayAbbrev,
      data: awayData.map(d => ({
        x: d.offRating,
        y: d.defRating,
        week: d.week,
      })),
    },
    {
      id: homeAbbrev,
      data: homeData.map(d => ({
        x: d.offRating,
        y: d.defRating,
        week: d.week,
      })),
    },
  ];

  // Use league averages for reference lines, fallback to local averages
  const allPoints = [...homeData, ...awayData];
  const avgOff = leagueStats?.avgOffRating ?? (allPoints.reduce((sum, p) => sum + p.offRating, 0) / allPoints.length);
  const avgDef = leagueStats?.avgDefRating ?? (allPoints.reduce((sum, p) => sum + p.defRating, 0) / allPoints.length);

  const ClickableLegendLayer = createClickableLegendLayer(seriesColors, selectedId, setSelectedId);
  const NodesLayer = createNodesLayer(seriesColors, selectedId, weekFilter, maxWeek);

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex flex-col">
      <div className="text-center text-xs font-bold mb-1">Weekly Off/Def Rating</div>
      <WeekFilterNav filter={weekFilter} onFilterChange={setWeekFilter} />
      <div className="flex-1 min-h-0">
        <ResponsiveScatterPlot
          data={chartData}
          margin={{ top: 10, right: 80, bottom: 40, left: 50 }}
          xScale={{
            type: 'linear',
            min: leagueStats?.minOffRating ?? 'auto',
            max: leagueStats?.maxOffRating ?? 'auto',
          }}
          yScale={{
            type: 'linear',
            min: leagueStats?.minDefRating ?? 'auto',
            max: leagueStats?.maxDefRating ?? 'auto',
            reverse: true,
          }}
          axisBottom={{
            tickSize: 0,
            tickPadding: 8,
            tickRotation: 0,
            legend: 'Off Rtg',
            legendPosition: 'middle',
            legendOffset: 32,
          }}
          axisLeft={{
            tickSize: 0,
            tickPadding: 8,
            tickRotation: 0,
            legend: 'Def Rtg',
            legendPosition: 'middle',
            legendOffset: -40,
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
            legends: {
              text: {
                fontSize: 11,
                fontFamily: 'var(--font-geist-mono), monospace',
                fill: 'var(--foreground)',
              },
            },
          }}
          colors={['#ef4444', '#3b82f6']}
          nodeSize={5}
          useMesh={true}
          layers={['grid', 'axes', NodesLayer, 'markers', 'mesh', 'annotations', ClickableLegendLayer]}
          markers={[
            {
              axis: 'x',
              value: avgOff,
              lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
            },
            {
              axis: 'y',
              value: avgDef,
              lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
            },
          ]}
          tooltip={({ node }) => {
            const d = node.data as { x: number; y: number; week: number };
            return (
              <div className="bg-[var(--card)] border border-[var(--border)] px-2 py-1 rounded shadow-lg text-xs font-mono">
                <div className="flex items-center gap-2">
                  <span
                    className="w-2 h-2 rounded-full"
                    style={{ backgroundColor: node.color }}
                  />
                  <span className="font-bold">{node.serieId}</span>
                </div>
                <div className="text-[var(--muted)]">Week {d.week}</div>
                <div className="text-[var(--muted)]">Off: {d.x} · Def: {d.y}</div>
              </div>
            );
          }}
        />
      </div>
    </div>
  );
}
