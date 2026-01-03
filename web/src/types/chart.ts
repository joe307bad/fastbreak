export type VisualizationType = 'SCATTER_PLOT' | 'LINE_CHART' | 'BAR_CHART' | 'BAR_GRAPH' | 'TABLE' | 'MATCHUP' | 'MATCHUP_V2';

export interface QuadrantConfig {
  color: string;
  label: string;
}

export interface ScatterDataPoint {
  label: string;
  x: number;
  y: number;
  sum?: number;
  teamCode?: string;
  division?: string;
  conference?: string;
}

export interface LineDataPoint {
  x: number | string;
  y: number;
}

export interface LineSeries {
  label: string;
  color: string;
  division?: string;
  conference?: string;
  dataPoints: LineDataPoint[];
}

export interface MatchupComparison {
  title: string;
  homeTeamValue: number | string;
  awayTeamValue: number | string;
  inverted?: boolean;
}

export interface MatchupDataPoint {
  homeTeam: string;
  awayTeam: string;
  gameTime: string;
  homeTeamDivision: string;
  awayTeamDivision: string;
  homeTeamConference: string;
  awayTeamConference: string;
  comparisons: MatchupComparison[];
}

export interface BaseChartData {
  sport: string;
  visualizationType: VisualizationType;
  title: string;
  subtitle?: string;
  description?: string;
  lastUpdated: string;
  source: string;
}

export interface ScatterPlotData extends BaseChartData {
  visualizationType: 'SCATTER_PLOT';
  xAxisLabel: string;
  yAxisLabel: string;
  xColumnLabel?: string;
  yColumnLabel?: string;
  invertYAxis?: boolean;
  quadrantTopRight?: QuadrantConfig;
  quadrantTopLeft?: QuadrantConfig;
  quadrantBottomLeft?: QuadrantConfig;
  quadrantBottomRight?: QuadrantConfig;
  subject?: string;
  dataPoints: ScatterDataPoint[];
}

export interface LineChartData extends BaseChartData {
  visualizationType: 'LINE_CHART';
  xAxisLabel?: string;
  yAxisLabel?: string;
  series: LineSeries[];
}

export interface BarChartData extends BaseChartData {
  visualizationType: 'BAR_CHART' | 'BAR_GRAPH';
  xAxisLabel?: string;
  yAxisLabel?: string;
  dataPoints: Array<{ label: string; value: number; color?: string; division?: string; conference?: string }>;
}

export interface TableColumn {
  label: string;
  value: string | number;
}

export interface TableRow {
  label: string;
  columns: TableColumn[];
}

export interface TableData extends BaseChartData {
  visualizationType: 'TABLE';
  dataPoints: TableRow[];
}

export interface MatchupData extends BaseChartData {
  visualizationType: 'MATCHUP';
  week?: number;
  dataPoints: MatchupDataPoint[];
}

export interface MatchupV2Data extends BaseChartData {
  visualizationType: 'MATCHUP_V2';
  week?: number;
  dataPoints: MatchupDataPoint[];
}

export type ChartData = ScatterPlotData | LineChartData | BarChartData | TableData | MatchupData | MatchupV2Data;

export interface RegistryEntry {
  interval: string;
  updatedAt: string;
  title: string;
}

export type Registry = Record<string, RegistryEntry>;
