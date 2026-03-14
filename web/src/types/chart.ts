export type VisualizationType = 'SCATTER_PLOT' | 'LINE_CHART' | 'BAR_CHART' | 'BAR_GRAPH' | 'TABLE' | 'MATCHUP' | 'MATCHUP_V2' | 'NBA_MATCHUP' | 'NHL_MATCHUP';

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

export interface ReferenceLine {
  value: number;
  label: string;
  color: string;
}

export interface BarChartData extends BaseChartData {
  visualizationType: 'BAR_CHART' | 'BAR_GRAPH';
  xAxisLabel?: string;
  yAxisLabel?: string;
  topReferenceLine?: ReferenceLine;
  bottomReferenceLine?: ReferenceLine;
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

// NBA Matchup types
export interface NBATeamStats {
  gamesPlayed: number;
  pointsPerGame: number;
  pointsPerGameRank: number;
  fieldGoalPct: number;
  threePtPct: number;
  reboundsPerGame: number;
  assistsPerGame: number;
  stealsPerGame: number;
  blocksPerGame: number;
  turnoversPerGame: number;
  offensiveRating: number;
  offensiveRatingRank: number;
  defensiveRating: number;
  defensiveRatingRank: number;
  netRating: number;
  netRatingRank: number;
  pace: number;
  cumNetRatingByWeek?: Record<string, number>;
  efficiencyByWeek?: Record<string, { offRating: number; defRating: number }>;
  [key: string]: number | string | Record<string, unknown> | undefined;
}

export interface NBATeam {
  id: string;
  name: string;
  abbreviation: string;
  logo?: string;
  wins: number;
  losses: number;
  conferenceRank: number;
  conference: string;
  stats: NBATeamStats;
}

export interface StatComparison {
  label: string;
  home: { value: number; rank: number };
  away: { value: number; rank: number };
}

export interface OffDefStatEntry {
  statKey: string;
  offLabel: string;
  defLabel: string;
  offense: { team: string; value: number; rank: number; rankDisplay?: string };
  defense: { team: string; value: number; rank: number; rankDisplay?: string };
  advantage: number;
}

export interface OffDefComparison {
  [key: string]: OffDefStatEntry | undefined;
}

export interface NBAComparisons {
  sideBySide?: {
    offense?: Record<string, StatComparison>;
    defense?: Record<string, StatComparison>;
    misc?: Record<string, StatComparison>;
  };
  homeOffVsAwayDef?: OffDefComparison;
  awayOffVsHomeDef?: OffDefComparison;
}

export interface TeamBoxScore {
  pts: number;
  fgm: number;
  fga: number;
  fgPct?: number;
  fg3m?: number;
  fg3a?: number;
  fg3Pct?: number;
  ftm?: number;
  fta?: number;
  ftPct?: number;
  reb?: number;
  ast?: number;
  stl?: number;
  blk?: number;
  tov?: number;
  [key: string]: number | undefined;
}

export interface VsSeasonAvgStat {
  gameValue: number;
  seasonAvg: number;
  difference: number;
  percentDiff?: number;
  aboveAverage?: boolean;
  label?: string;
}

export interface VsSeasonAvg {
  points?: VsSeasonAvgStat;
  fieldGoalPct?: VsSeasonAvgStat;
  threePtPct?: VsSeasonAvgStat;
  rebounds?: VsSeasonAvgStat;
  assists?: VsSeasonAvgStat;
  [key: string]: VsSeasonAvgStat | undefined;
}

export interface NBAMatchupResults {
  finalScore: {
    home: number;
    away: number;
  };
  winner: string;
  margin: number;
  homeWon: boolean;
  teamBoxScore?: {
    home: TeamBoxScore;
    away: TeamBoxScore;
  };
  vsSeasonAvg?: {
    home: VsSeasonAvg;
    away: VsSeasonAvg;
  };
}

export interface LeagueEfficiencyStats {
  avgOffRating: number;
  avgDefRating: number;
  minOffRating: number;
  maxOffRating: number;
  minDefRating: number;
  maxDefRating: number;
}

export interface LeagueCumNetRatingStats {
  minCumNetRating: number;
  maxCumNetRating: number;
}

export interface NBAMatchupDataPoint {
  gameId: string;
  gameDate: string;
  gameName: string;
  gameStatus: string;
  gameCompleted: boolean;
  homeTeam: NBATeam;
  awayTeam: NBATeam;
  odds?: {
    spread?: string;
    overUnder?: string;
    homeMoneyline?: string;
    awayMoneyline?: string;
  };
  comparisons?: NBAComparisons;
  results?: NBAMatchupResults;
  tenthNetRatingByWeek?: Record<string, number>;
  leagueEfficiencyStats?: LeagueEfficiencyStats;
  leagueCumNetRatingStats?: LeagueCumNetRatingStats;
}

export interface NBAMatchupData extends BaseChartData {
  visualizationType: 'NBA_MATCHUP';
  dataPoints: NBAMatchupDataPoint[];
}

// NHL Matchup types
export interface NHLTeamStats {
  gamesPlayed: number;
  goalsPerGame: number;
  goalsPerGameRank: number;
  goalsAgainstPerGame: number;
  goalsAgainstPerGameRank: number;
  goalDiffPerGame: number;
  goalDiffPerGameRank: number;
  shotsForPerGame: number;
  shotsAgainstPerGame: number;
  powerPlayPct: number;
  penaltyKillPct: number;
  faceoffWinPct: number;
  pointsPct: number;
  pointsPctRank: number;
  xgfPct: number;
  xgfPctRank: number;
  [key: string]: number | string | Record<string, unknown>;
}

export interface NHLTeam {
  id: string;
  name: string;
  abbreviation: string;
  logo?: string;
  wins: number;
  losses: number;
  otLosses: number;
  points: number;
  conferenceRank: number;
  divisionRank: number;
  division: string;
  conference: string;
  streak?: string;
  last10?: string;
  stats: NHLTeamStats;
}

export interface NHLMatchupDataPoint {
  gameId: string;
  gameDate: string;
  gameName: string;
  gameState: string;
  gameCompleted: boolean;
  homeTeam: NHLTeam;
  awayTeam: NHLTeam;
}

export interface NHLMatchupData extends BaseChartData {
  visualizationType: 'NHL_MATCHUP';
  dataPoints: NHLMatchupDataPoint[];
}

// NFL/MATCHUP_V2 types
export interface MatchupV2Odds {
  home_spread: string;
  home_moneyline: string;
  away_spread: string;
  away_moneyline: string;
  over_under: string;
}

export interface MatchupV2Team {
  name: string;
  abbreviation: string;
  record?: string;
  conferenceRank?: number;
  conference?: string;
  stats?: Record<string, { value: number; rank: number; rankDisplay: string }>;
}

export interface MatchupV2DataPoint {
  game_datetime: string;
  odds?: MatchupV2Odds;
  homeTeam?: MatchupV2Team;
  awayTeam?: MatchupV2Team;
  comparisons?: {
    sideBySide?: Record<string, Record<string, { label: string; home: { value: number; rank: number }; away: { value: number; rank: number } }>>;
  };
}

export type ChartData = ScatterPlotData | LineChartData | BarChartData | TableData | MatchupData | MatchupV2Data | NBAMatchupData | NHLMatchupData;

export interface RegistryEntry {
  interval: string;
  updatedAt: string;
  title: string;
  type?: string;
}

export type Registry = Record<string, RegistryEntry>;

export function isChartEntry(entry: RegistryEntry): boolean {
  return entry.type !== 'topics';
}
