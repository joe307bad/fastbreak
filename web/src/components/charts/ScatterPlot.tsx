'use client';

import { ResponsiveScatterPlot, ScatterPlotLayerProps } from '@nivo/scatterplot';
import { ScatterPlotData, QuadrantConfig } from '@/types/chart';
import { useState, useEffect } from 'react';

interface Props {
  data: ScatterPlotData;
}

interface NodeData {
  x: number;
  y: number;
  label?: string;
  teamCode?: string;
  isOutlier?: boolean;
}

interface Quadrants {
  topRight?: QuadrantConfig;
  topLeft?: QuadrantConfig;
  bottomLeft?: QuadrantConfig;
  bottomRight?: QuadrantConfig;
}

function getQuadrantColor(
  x: number,
  y: number,
  xMid: number,
  yMid: number,
  quadrants: Quadrants
): string {
  const isHighX = x >= xMid;
  const isHighY = y >= yMid;

  if (isHighX && isHighY) return quadrants.topRight?.color || '#888';
  if (!isHighX && isHighY) return quadrants.topLeft?.color || '#888';
  if (!isHighX && !isHighY) return quadrants.bottomLeft?.color || '#888';
  return quadrants.bottomRight?.color || '#888';
}

function createNodesLayer(
  xMid: number,
  yMid: number,
  quadrants: Quadrants,
  isMobile: boolean
) {
  return function NodesLayer({ nodes }: ScatterPlotLayerProps<NodeData>) {
    return (
      <g>
        {nodes.map(node => {
          const color = getQuadrantColor(node.data.x, node.data.y, xMid, yMid, quadrants);
          const isOutlier = node.data.isOutlier || false;

          // Outliers get larger radius and thicker stroke
          const radius = isOutlier ? 8 : 6;
          const strokeWidth = isOutlier ? 2.5 : 1.5;
          // On mobile, non-outliers are transparent; on desktop, all points are fully visible
          const fillOpacity = isMobile ? (isOutlier ? 1 : 0.15) : 1;

          return (
            <circle
              key={node.id}
              cx={node.x}
              cy={node.y}
              r={radius}
              fill={color}
              fillOpacity={fillOpacity}
              stroke="var(--background)"
              strokeWidth={strokeWidth}
              strokeOpacity={fillOpacity}
            />
          );
        })}
      </g>
    );
  };
}

function createLabelsLayer(subject?: string, isMobile?: boolean) {
  return function LabelsLayer({ nodes }: ScatterPlotLayerProps<NodeData>) {
    return (
      <g>
        {nodes.map(node => {
          const d = node.data;
          // On mobile, only show labels for outliers; on desktop, show all labels
          if (isMobile && !d.isOutlier) return null;

          const displayLabel = subject === 'PLAYER' ? (d.label || d.teamCode || '') : (d.teamCode || d.label || '');
          const labelWidth = displayLabel.length * 6 + 6;
          return (
            <g key={node.id}>
              <rect
                x={node.x - labelWidth / 2}
                y={node.y - 20}
                width={labelWidth}
                height={14}
                fill="var(--background)"
                opacity={0.85}
                rx={2}
              />
              <text
                x={node.x}
                y={node.y - 10}
                textAnchor="middle"
                dominantBaseline="middle"
                style={{
                  fontSize: 9,
                  fontFamily: 'var(--font-geist-mono), monospace',
                  fill: 'var(--foreground)',
                  pointerEvents: 'none',
                }}
              >
                {displayLabel}
              </text>
            </g>
          );
        })}
      </g>
    );
  };
}

function createQuadrantLayer(
  xMid: number,
  yMid: number,
  quadrants: Quadrants
) {
  return function QuadrantLayer({ xScale, yScale, innerWidth, innerHeight }: ScatterPlotLayerProps<NodeData>) {
    const xMidPx = xScale(xMid);
    const yMidPx = yScale(yMid);

    const regions = [
      {
        x: xMidPx,
        y: 0,
        width: innerWidth - xMidPx,
        height: yMidPx,
        quadrant: quadrants.topRight,
        labelX: innerWidth - 8,
        labelY: 16,
        anchor: 'end' as const,
      },
      {
        x: 0,
        y: 0,
        width: xMidPx,
        height: yMidPx,
        quadrant: quadrants.topLeft,
        labelX: 8,
        labelY: 16,
        anchor: 'start' as const,
      },
      {
        x: 0,
        y: yMidPx,
        width: xMidPx,
        height: innerHeight - yMidPx,
        quadrant: quadrants.bottomLeft,
        labelX: 8,
        labelY: innerHeight - 8,
        anchor: 'start' as const,
      },
      {
        x: xMidPx,
        y: yMidPx,
        width: innerWidth - xMidPx,
        height: innerHeight - yMidPx,
        quadrant: quadrants.bottomRight,
        labelX: innerWidth - 8,
        labelY: innerHeight - 8,
        anchor: 'end' as const,
      },
    ];

    return (
      <g>
        {regions.map((region, i) => {
          if (!region.quadrant) return null;
          return (
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
                  fontSize: 11,
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
          );
        })}
      </g>
    );
  };
}

export function ScatterPlot({ data }: Props) {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Calculate means for both axes
  const xValues = data.dataPoints.map(p => p.x);
  const yValues = data.dataPoints.map(p => p.y);
  const xMean = xValues.reduce((sum, val) => sum + val, 0) / xValues.length;
  const yMean = yValues.reduce((sum, val) => sum + val, 0) / yValues.length;

  // Identify outliers: top 3 points furthest from center in each quadrant
  const outlierSet = new Set<string>();

  if (data.dataPoints.length > 15) {
    const quadrants = {
      topRight: [] as Array<{ point: typeof data.dataPoints[0]; distance: number }>,
      topLeft: [] as Array<{ point: typeof data.dataPoints[0]; distance: number }>,
      bottomLeft: [] as Array<{ point: typeof data.dataPoints[0]; distance: number }>,
      bottomRight: [] as Array<{ point: typeof data.dataPoints[0]; distance: number }>,
    };

    // Categorize points by quadrant and calculate distance from mean
    data.dataPoints.forEach(point => {
      const xDist = point.x - xMean;
      const yDist = point.y - yMean;
      const distance = Math.sqrt(xDist * xDist + yDist * yDist);

      // For invertYAxis charts, flip the y comparison for quadrant categorization
      const isHighX = point.x >= xMean;
      const isHighY = data.invertYAxis ? point.y <= yMean : point.y >= yMean;

      if (isHighX && isHighY) {
        quadrants.topRight.push({ point, distance });
      } else if (!isHighX && isHighY) {
        quadrants.topLeft.push({ point, distance });
      } else if (!isHighX && !isHighY) {
        quadrants.bottomLeft.push({ point, distance });
      } else {
        quadrants.bottomRight.push({ point, distance });
      }
    });

    // Get top 3 from each quadrant
    Object.values(quadrants).forEach(quadrant => {
      quadrant
        .sort((a, b) => b.distance - a.distance)
        .slice(0, 3)
        .forEach(({ point }) => {
          const id = `${point.x}-${point.y}`;
          outlierSet.add(id);
        });
    });
  }

  // Prepare chart data with outlier flags
  const chartData = [{
    id: data.subject || 'data',
    data: data.dataPoints.map(p => {
      const transformedY = data.invertYAxis ? p.y * -1 : p.y;
      const pointId = `${p.x}-${p.y}`;
      const isOutlier = outlierSet.has(pointId);

      return {
        x: p.x,
        y: transformedY,
        label: p.label,
        teamCode: p.teamCode,
        isOutlier,
      };
    }),
  }];

  // Calculate midpoints for quadrant display (using transformed Y for visual consistency)
  const transformedYValues = data.dataPoints.map(p => data.invertYAxis ? p.y * -1 : p.y);
  const xMid = xMean;
  const yMid = transformedYValues.reduce((sum, val) => sum + val, 0) / transformedYValues.length;

  const quadrants: Quadrants = {
    topRight: data.quadrantTopRight,
    topLeft: data.quadrantTopLeft,
    bottomLeft: data.quadrantBottomLeft,
    bottomRight: data.quadrantBottomRight,
  };

  const QuadrantLayer = createQuadrantLayer(xMid, yMid, quadrants);
  const NodesLayer = createNodesLayer(xMid, yMid, quadrants, isMobile);
  const LabelsLayer = createLabelsLayer(data.subject, isMobile);

  return (
    <div className="w-full h-full">
      <ResponsiveScatterPlot
        data={chartData}
        margin={{ top: 20, right: 20, bottom: 60, left: 70 }}
        xScale={{ type: 'linear', min: 'auto', max: 'auto' }}
        yScale={{ type: 'linear', min: 'auto', max: 'auto' }}
        axisBottom={{
          tickSize: 0,
          tickPadding: 8,
          tickRotation: 0,
          legend: data.xAxisLabel,
          legendPosition: 'middle',
          legendOffset: 45,
        }}
        axisLeft={{
          tickSize: 0,
          tickPadding: 8,
          tickRotation: 0,
          legend: data.yAxisLabel,
          legendPosition: 'middle',
          legendOffset: -55,
        }}
        theme={{
          text: {
            fontFamily: 'var(--font-geist-mono), monospace',
            fill: 'var(--foreground)',
          },
          axis: {
            ticks: {
              text: {
                fontSize: 11,
                fontWeight: 600,
                fill: 'var(--foreground)',
              },
            },
            legend: {
              text: {
                fontSize: 12,
                fontWeight: 700,
                fill: 'var(--foreground)',
              },
            },
          },
          grid: {
            line: {
              stroke: 'var(--border)',
              strokeOpacity: 0.4,
            },
          },
        }}
        colors={{ scheme: 'category10' }}
        nodeSize={12}
        useMesh={true}
        layers={[QuadrantLayer, 'grid', 'axes', NodesLayer, LabelsLayer, 'markers', 'mesh', 'legends', 'annotations']}
        tooltip={({ node }) => {
          const d = node.data as NodeData;
          return (
            <div className="bg-[var(--card)] border border-[var(--border)] px-3 py-2 rounded shadow-lg text-xs font-mono">
              <strong>{d.label || d.teamCode}</strong>
              <div className="text-[var(--muted)]">
                {data.xColumnLabel || 'X'}: {node.xValue}
              </div>
              <div className="text-[var(--muted)]">
                {data.yColumnLabel || 'Y'}: {node.yValue}
              </div>
            </div>
          );
        }}
        markers={[
          {
            axis: 'x',
            value: xMid,
            lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
          },
          {
            axis: 'y',
            value: yMid,
            lineStyle: { stroke: 'var(--muted)', strokeWidth: 1, strokeDasharray: '4 4' },
          },
        ]}
      />
    </div>
  );
}
