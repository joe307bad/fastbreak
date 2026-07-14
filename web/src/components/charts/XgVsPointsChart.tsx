'use client';

import { useState } from 'react';
import { ResponsiveScatterPlot, ScatterPlotLayerProps } from '@nivo/scatterplot';
import { NHLTeamStats, LeagueXgVsPointsStats } from '@/types/chart';

interface Props {
  homeTeamStats: NHLTeamStats;
  awayTeamStats: NHLTeamStats;
  homeAbbrev: string;
  awayAbbrev: string;
  leagueStats?: LeagueXgVsPointsStats;
}

interface WeeklyData {
  week: number;
  xgPct: number;
  pointsPct: number;
}

interface NodeData {
  x: number;
  y: number;
  week: number;
}

type WeekFilter = 'all' | 'last5' | 'prior5';
type QuadrantKey = 'topRight' | 'topLeft' | 'bottomLeft' | 'bottomRight';

interface QuadrantConfig {
  color: string;
  label: string;
}

const QUADRANTS: Record<QuadrantKey, QuadrantConfig> = {
  topRight: { color: '#22c55e', label: 'Elite' },
  topLeft: { color: '#f59e0b', label: 'Lucky' },
  bottomRight: { color: '#3b82f6', label: 'Unlucky' },
  bottomLeft: { color: '#ef4444', label: 'Back of the Pack' },
};

function getQuadrantForPoint(
  xgPct: number,
  pointsPct: number,
  avgXg: number,
  avgPts: number
): QuadrantKey {
  const isHighXg = xgPct >= avgXg;
  const isHighPts = pointsPct >= avgPts;

  if (isHighXg && isHighPts) return 'topRight';
  if (!isHighXg && isHighPts) return 'topLeft';
  if (!isHighXg && !isHighPts) return 'bottomLeft';
  return 'bottomRight';
}

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
          data-active-filter={filter === opt.value ? opt.label : undefined}
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

function QuadrantLegend({
  selectedQuadrant,
  onSelectQuadrant,
}: {
  selectedQuadrant: QuadrantKey | null;
  onSelectQuadrant: (q: QuadrantKey | null) => void;
}) {
  const items: { key: QuadrantKey; config: QuadrantConfig }[] = [
    { key: 'topRight', config: QUADRANTS.topRight },
    { key: 'topLeft', config: QUADRANTS.topLeft },
    { key: 'bottomRight', config: QUADRANTS.bottomRight },
    { key: 'bottomLeft', config: QUADRANTS.bottomLeft },
  ];

  return (
    <div className="flex gap-1 justify-center mb-1 flex-wrap">
      {items.map(({ key, config }) => {
        const isSelected = selectedQuadrant === key;
        return (
          <button
            key={key}
            onClick={() => onSelectQuadrant(isSelected ? null : key)}
            className="px-2 py-0.5 text-[10px] rounded-full border-2 transition-colors flex items-center gap-1 font-semibold"
            style={{
              borderColor: isSelected ? config.color : 'var(--border)',
              backgroundColor: isSelected ? `${config.color}20` : 'transparent',
              color: config.color,
            }}
          >
            <span
              className="w-2 h-2 rounded-full"
              style={{ backgroundColor: config.color }}
            />
            {config.label}
          </button>
        );
      })}
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
  maxWeek: number,
  selectedQuadrant: QuadrantKey | null,
  avgXg: number,
  avgPts: number
) {
  return function NodesLayer({ nodes }: ScatterPlotLayerProps<NodeData>) {
    return (
      <g>
        {nodes.map(node => {
          const seriesColor = seriesColors.find(s => s.id === node.serieId)?.color || '#888';
          const nodeData = node.data as NodeData;
          const weekNum = nodeData.week;

          const isWeekSelected = weekFilter === 'all' ||
            (weekFilter === 'last5' && weekNum > maxWeek - 5) ||
            (weekFilter === 'prior5' && weekNum <= maxWeek - 5 && weekNum > maxWeek - 10);

          const isSeriesSelected = !selectedId || selectedId === node.serieId;

          const pointQuadrant = getQuadrantForPoint(nodeData.x, nodeData.y, avgXg, avgPts);
          const isQuadrantSelected = !selectedQuadrant || selectedQuadrant === pointQuadrant;

          const isHighlighted = isWeekSelected && isSeriesSelected && isQuadrantSelected;
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

function createQuadrantLayer(avgXg: number, avgPts: number) {
  return function QuadrantLayer({ xScale, yScale, innerWidth, innerHeight }: ScatterPlotLayerProps<NodeData>) {
    const xMidPx = xScale(avgXg);
    const yMidPx = yScale(avgPts);

    const regions = [
      {
        x: xMidPx, y: 0,
        width: innerWidth - xMidPx, height: yMidPx,
        quadrant: QUADRANTS.topRight,
        labelX: innerWidth - 4, labelY: 10,
        anchor: 'end' as const,
      },
      {
        x: 0, y: 0,
        width: xMidPx, height: yMidPx,
        quadrant: QUADRANTS.topLeft,
        labelX: 4, labelY: 10,
        anchor: 'start' as const,
      },
      {
        x: 0, y: yMidPx,
        width: xMidPx, height: innerHeight - yMidPx,
        quadrant: QUADRANTS.bottomLeft,
        labelX: 4, labelY: innerHeight - 4,
        anchor: 'start' as const,
      },
      {
        x: xMidPx, y: yMidPx,
        width: innerWidth - xMidPx, height: innerHeight - yMidPx,
        quadrant: QUADRANTS.bottomRight,
        labelX: innerWidth - 4, labelY: innerHeight - 4,
        anchor: 'end' as const,
      },
    ];

    return (
      <g>
        {regions.map((region, i) => (
          <g key={i}>
            <rect
              x={region.x}
              y={region.y}
              width={region.width}
              height={region.height}
              fill={region.quadrant.color}
              fillOpacity={0.08}
            />
            <text
              x={region.labelX}
              y={region.labelY}
              textAnchor={region.anchor}
              style={{
                fontSize: 9,
                fontFamily: 'var(--font-geist-mono), monospace',
                fill: region.quadrant.color,
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              {region.quadrant.label}
            </text>
          </g>
        ))}
      </g>
    );
  };
}

function parseWeeklyData(weeklyXgfPct: Record<string, number> | undefined, weeklyPointsPct: Record<string, number> | undefined): WeeklyData[] {
  if (!weeklyXgfPct || !weeklyPointsPct) return [];

  return Object.entries(weeklyXgfPct)
    .filter(([key]) => weeklyPointsPct[key] !== undefined)
    .map(([key, xgPct]) => {
      const weekNum = parseInt(key.replace('week-', ''), 10);
      return {
        week: weekNum,
        xgPct,
        pointsPct: weeklyPointsPct[key],
      };
    })
    .sort((a, b) => a.week - b.week);
}

export function XgVsPointsChart({ homeTeamStats, awayTeamStats, homeAbbrev, awayAbbrev, leagueStats }: Props) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [weekFilter, setWeekFilter] = useState<WeekFilter>('all');
  const [selectedQuadrant, setSelectedQuadrant] = useState<QuadrantKey | null>(null);
  const homeData = parseWeeklyData(homeTeamStats.weeklyXgfPct, homeTeamStats.weeklyPointsPct);
  const awayData = parseWeeklyData(awayTeamStats.weeklyXgfPct, awayTeamStats.weeklyPointsPct);

  if (homeData.length === 0 && awayData.length === 0) {
    return (
      <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex items-center justify-center">
        <div className="text-center text-[var(--muted)]">
          <div className="text-sm font-bold mb-1">xG% vs Points%</div>
          <div className="text-xs">No data available</div>
        </div>
      </div>
    );
  }

  const allWeeks = [...homeData, ...awayData].map(d => d.week);
  const maxWeek = Math.max(...allWeeks);

  const seriesColors = [
    { id: awayAbbrev, color: '#ef4444' },
    { id: homeAbbrev, color: '#3b82f6' },
  ];

  const chartData = [
    {
      id: awayAbbrev,
      data: awayData.map(d => ({ x: d.xgPct, y: d.pointsPct, week: d.week })),
    },
    {
      id: homeAbbrev,
      data: homeData.map(d => ({ x: d.xgPct, y: d.pointsPct, week: d.week })),
    },
  ];

  const allPoints = [...homeData, ...awayData];
  const avgXg = leagueStats?.avgXgPct ?? (allPoints.reduce((sum, p) => sum + p.xgPct, 0) / allPoints.length);
  const avgPts = leagueStats?.avgPointsPct ?? (allPoints.reduce((sum, p) => sum + p.pointsPct, 0) / allPoints.length);

  const ClickableLegendLayer = createClickableLegendLayer(seriesColors, selectedId, setSelectedId);
  const NodesLayer = createNodesLayer(seriesColors, selectedId, weekFilter, maxWeek, selectedQuadrant, avgXg, avgPts);
  const QuadrantLayer = createQuadrantLayer(avgXg, avgPts);

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] p-2 h-full flex flex-col">
      <div className="text-center text-xs font-bold mb-1">Weekly xG% vs Points%</div>
      <WeekFilterNav filter={weekFilter} onFilterChange={setWeekFilter} />
      <QuadrantLegend
        selectedQuadrant={selectedQuadrant}
        onSelectQuadrant={setSelectedQuadrant}
      />
      <div className="flex-1 min-h-0">
        <ResponsiveScatterPlot
          data={chartData}
          margin={{ top: 10, right: 80, bottom: 40, left: 50 }}
          xScale={{
            type: 'linear',
            min: leagueStats?.minXgPct ?? 'auto',
            max: leagueStats?.maxXgPct ?? 'auto',
          }}
          yScale={{
            type: 'linear',
            min: leagueStats?.minPointsPct ?? 'auto',
            max: leagueStats?.maxPointsPct ?? 'auto',
          }}
          axisBottom={{
            tickSize: 0,
            tickPadding: 8,
            tickRotation: 0,
            legend: 'xG%',
            legendPosition: 'middle',
            legendOffset: 32,
          }}
          axisLeft={{
            tickSize: 0,
            tickPadding: 8,
            tickRotation: 0,
            legend: 'Points%',
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
          layers={[QuadrantLayer, 'grid', 'axes', NodesLayer, 'markers', 'mesh', 'annotations', ClickableLegendLayer]}
          markers={[
            {
              axis: 'x',
              value: avgXg,
              lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
            },
            {
              axis: 'y',
              value: avgPts,
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
                <div className="text-[var(--muted)]">xG%: {d.x.toFixed(1)} · Pts%: {d.y.toFixed(1)}</div>
              </div>
            );
          }}
        />
      </div>
    </div>
  );
}
