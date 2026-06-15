import {
  ChartData,
  MatchupData,
  MatchupV2Data,
  MLBMatchupData,
  MLBTeamReportCardData,
  NBAMatchupData,
  NHLMatchupData,
  VisualizationType,
} from '@/types/chart';
import { selectTopMatchups } from '@/lib/topMatchups';

export type AnyMatchupData =
  | MatchupData
  | MatchupV2Data
  | NBAMatchupData
  | NHLMatchupData
  | MLBMatchupData;

export const WEB_CHART_TYPES: VisualizationType[] = [
  'SCATTER_PLOT',
  'LINE_CHART',
  'BAR_CHART',
  'BAR_GRAPH',
  'TABLE',
  'MLB_TEAM_REPORT_CARD',
];

export const WEB_MATCHUP_TYPES: VisualizationType[] = [
  'MATCHUP',
  'MATCHUP_V2',
  'NBA_MATCHUP',
  'NHL_MATCHUP',
  'MLB_MATCHUP',
];

export const TOP_MATCHUP_TYPES: VisualizationType[] = [
  'NBA_MATCHUP',
  'NHL_MATCHUP',
  'MLB_MATCHUP',
];

const WEB_DISPLAYABLE_TYPES = new Set<VisualizationType>([
  ...WEB_CHART_TYPES,
  ...WEB_MATCHUP_TYPES,
]);

function hasMatchupV2Content(data: MatchupV2Data): boolean {
  const dataPointsObj = data.dataPoints as unknown as Record<string, unknown>;
  return Object.keys(dataPointsObj ?? {}).length > 0;
}

function hasTeamReportCardContent(data: MLBTeamReportCardData): boolean {
  return Object.keys(data.teams ?? {}).length > 0;
}

export function hasDisplayableMatchupContent(data: ChartData): boolean {
  if (TOP_MATCHUP_TYPES.includes(data.visualizationType)) {
    return selectTopMatchups(data as NBAMatchupData | NHLMatchupData | MLBMatchupData).length > 0;
  }

  if (data.visualizationType === 'MATCHUP_V2') {
    return hasMatchupV2Content(data as MatchupV2Data);
  }

  if (data.visualizationType === 'MATCHUP') {
    return (data as MatchupData).dataPoints.length > 0;
  }

  return false;
}

export interface HomeGridMatchupContext {
  topMatchupGameIds: string[];
  topMatchupKey?: string;
  nflMatchupKey?: string;
}

export function isMatchupVisibleOnHomeGrid(
  matchup: { key: string; data: AnyMatchupData },
  ctx: HomeGridMatchupContext
): boolean {
  const { data, key } = matchup;

  if (TOP_MATCHUP_TYPES.includes(data.visualizationType)) {
    return ctx.topMatchupGameIds.length > 0 && ctx.topMatchupKey === key;
  }

  if (data.visualizationType === 'MATCHUP_V2') {
    return !ctx.topMatchupKey && ctx.nflMatchupKey === key && hasMatchupV2Content(data);
  }

  if (data.visualizationType === 'MATCHUP') {
    return data.dataPoints.length > 0;
  }

  return false;
}

export function isDisplayableChart(data: ChartData): boolean {
  if (!WEB_DISPLAYABLE_TYPES.has(data.visualizationType)) {
    return false;
  }

  if (WEB_MATCHUP_TYPES.includes(data.visualizationType)) {
    return hasDisplayableMatchupContent(data);
  }

  if (data.visualizationType === 'MLB_TEAM_REPORT_CARD') {
    return hasTeamReportCardContent(data as MLBTeamReportCardData);
  }

  return true;
}

export function filterChartsForSport(
  allCharts: { key: string; data: ChartData }[],
  sport: string
) {
  const sportCharts = allCharts.filter(
    chart => chart.data.sport?.toLowerCase() === sport.toLowerCase()
  );

  const charts = sportCharts.filter(chart =>
    WEB_CHART_TYPES.includes(chart.data.visualizationType)
  );

  const matchups = sportCharts.filter(
    chart =>
      WEB_MATCHUP_TYPES.includes(chart.data.visualizationType) &&
      hasDisplayableMatchupContent(chart.data)
  );

  return { charts, matchups };
}

export function getTopMatchupMetricLabel(type: VisualizationType): string {
  if (type === 'NHL_MATCHUP') return 'Goal Diff';
  if (type === 'MLB_MATCHUP') return 'Run Diff';
  return 'Net Rating';
}
