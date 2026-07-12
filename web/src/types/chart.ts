export type VisualizationType = 'SCATTER_PLOT' | 'LINE_CHART' | 'BAR_CHART' | 'BAR_GRAPH' | 'TABLE' | 'MATCHUP' | 'MATCHUP_V2' | 'NBA_MATCHUP' | 'NHL_MATCHUP' | 'MLB_MATCHUP' | 'MLB_TEAM_REPORT_CARD';

export interface QuadrantConfig {
  color: string;
  label: string;
  lightModeColor?: string;
}

export interface ScatterPlotQuadrants {
  topRight?: QuadrantConfig;
  topLeft?: QuadrantConfig;
  bottomLeft?: QuadrantConfig;
  bottomRight?: QuadrantConfig;
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
  avgOffRating: number | null;
  avgDefRating: number | null;
  minOffRating: number | null;
  maxOffRating: number | null;
  minDefRating: number | null;
  maxDefRating: number | null;
}

export interface LeagueCumNetRatingStats {
  minCumNetRating: number | null;
  maxCumNetRating: number | null;
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
  scatterPlotQuadrants?: ScatterPlotQuadrants;
  dataPoints: NBAMatchupDataPoint[];
}

// NHL Matchup types
export interface NHLMonthTrend {
  gamesPlayed: number;
  record: { wins: number; losses: number; rank: number; rankDisplay: string };
  goalsPerGame: { value: number; rank: number; rankDisplay: string };
  goalsAgainstPerGame: { value: number; rank: number; rankDisplay: string };
  goalDiffPerGame: { value: number; rank: number; rankDisplay: string };
  xgfPct: { value: number; rank: number; rankDisplay: string };
}

export interface NHLTeamStats {
  gamesPlayed: number;
  goalsPerGame: number;
  goalsPerGameRank: number;
  goalsPerGameRankDisplay?: string;
  goalsAgainstPerGame: number;
  goalsAgainstPerGameRank: number;
  goalsAgainstPerGameRankDisplay?: string;
  goalDiffPerGame: number;
  goalDiffPerGameRank: number;
  goalDiffPerGameRankDisplay?: string;
  shotsForPerGame: number;
  shotsForPerGameRank?: number;
  shotsForPerGameRankDisplay?: string;
  shotsAgainstPerGame: number;
  shotsAgainstPerGameRank?: number;
  shotsAgainstPerGameRankDisplay?: string;
  powerPlayPct: number;
  powerPlayPctRank?: number;
  powerPlayPctRankDisplay?: string;
  penaltyKillPct: number;
  penaltyKillPctRank?: number;
  penaltyKillPctRankDisplay?: string;
  faceoffWinPct: number;
  faceoffWinPctRank?: number;
  faceoffWinPctRankDisplay?: string;
  pointsPct: number;
  pointsPctRank: number;
  pointsPctRankDisplay?: string;
  xgfPct: number;
  xgfPctRank: number;
  xgfPctRankDisplay?: string;
  cumXgfPctByWeek?: Record<string, number>;
  weeklyXgfPct?: Record<string, number>;
  weeklyPointsPct?: Record<string, number>;
  monthTrend?: NHLMonthTrend;
  [key: string]: number | string | Record<string, unknown> | NHLMonthTrend | undefined;
}

export interface NHLPlayoffProbability {
  playoffProb: number;
  confChampProb: number;
  finalsProb: number;
  champProb: number;
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
  playoffProbability?: NHLPlayoffProbability;
}

export interface NHLPlayerStat {
  value: number;
  rank: number;
  rankDisplay: string;
}

export interface NHLPlayer {
  name: string;
  position: string;
  gamesPlayed: NHLPlayerStat;
  goals: NHLPlayerStat;
  assists: NHLPlayerStat;
  points: NHLPlayerStat;
  plusMinus: NHLPlayerStat;
  pointsPerGame?: NHLPlayerStat;
}

export interface NHLBoxScore {
  goals: number;
  sog: number;
  hits: number;
  pim: number;
  blocks: number;
  powerPlayGoals: number;
  giveaways: number;
  takeaways: number;
  faceoffWinPct: number;
  saves: number;
  savePct: number;
}

export interface NHLMatchupResults {
  finalScore: {
    home: number;
    away: number;
    winner: string;
    margin: number;
    homeWon: boolean;
  };
  teamBoxScore?: {
    home: NHLBoxScore;
    away: NHLBoxScore;
  };
  vsSeasonAvg?: {
    home: Record<string, VsSeasonAvgStat>;
    away: Record<string, VsSeasonAvgStat>;
  };
}

export interface LeagueXgVsPointsStats {
  avgXgPct: number;
  avgPointsPct: number;
  minXgPct: number;
  maxXgPct: number;
  minPointsPct: number;
  maxPointsPct: number;
}

export interface LeagueCumXgStats {
  minCumXgPct: number | null;
  maxCumXgPct: number | null;
}

export interface NHLMatchupDataPoint {
  gameId: string;
  gameDate: string;
  gameName: string;
  gameState: string;
  gameCompleted: boolean;
  homeTeam: NHLTeam;
  awayTeam: NHLTeam;
  homePlayers?: NHLPlayer[];
  awayPlayers?: NHLPlayer[];
  comparisons?: NBAComparisons;
  results?: NHLMatchupResults;
  location?: { stadium: string };
  tenthXgfPctByWeek?: Record<string, number>;
  leagueXgVsPointsStats?: LeagueXgVsPointsStats;
  leagueCumXgStats?: LeagueCumXgStats;
}

export interface NHLMatchupData extends BaseChartData {
  visualizationType: 'NHL_MATCHUP';
  dataPoints: NHLMatchupDataPoint[];
}

// MLB Matchup types
export interface MLBStatValue {
  value: number | null;
  rank: number | null;
  rankDisplay?: string | null;
}

export interface MLBMonthTrend {
  gamesPlayed?: number;
  record?: { wins: number; losses: number; rank: number; rankDisplay?: string };
  runsPerGame?: MLBStatValue;
  runsAllowedPerGame?: MLBStatValue;
  runDiffPerGame?: MLBStatValue;
  hitsPerGame?: MLBStatValue;
  hrsPerGame?: MLBStatValue;
}

export interface MLBTeamStats {
  gamesPlayed?: number;
  runsPerGame?: MLBStatValue;
  runsAllowedPerGame?: MLBStatValue;
  battingAvg?: MLBStatValue;
  onBasePct?: MLBStatValue;
  sluggingPct?: MLBStatValue;
  ops?: MLBStatValue;
  hitsPerGame?: MLBStatValue;
  hrPerGame?: MLBStatValue;
  era?: MLBStatValue;
  whip?: MLBStatValue;
  kPer9?: MLBStatValue;
  bbPer9?: MLBStatValue;
  cumRunDiffByWeek?: Record<string, number>;
  performanceByWeek?: Record<string, { runsScored: number; runsAllowed: number }>;
  monthTrend?: MLBMonthTrend;
  [key: string]: MLBStatValue | number | Record<string, unknown> | MLBMonthTrend | undefined;
}

export interface MLBTeamInfo {
  id: string;
  name: string;
  abbreviation: string;
  logo?: string;
  record?: string;
  division?: string;
  league?: string;
  stats: MLBTeamStats | null;
}

export interface MLBMatchupOdds {
  provider?: string;
  spread?: number;
  overUnder?: number;
  homeMoneyline?: number;
  awayMoneyline?: number;
  details?: string;
}

export interface MLBGameResults {
  homeScore?: number;
  awayScore?: number;
  winner?: string;
  margin?: number;
  homeWon?: boolean;
}

export interface LeagueCumRunDiffStats {
  minCumRunDiff: number | null;
  maxCumRunDiff: number | null;
  top10ByWeek?: Record<string, number>;
}

export interface LeagueWeeklyStats {
  avgRunsScored: number | null;
  avgRunsAllowed: number | null;
  minRunsScored: number | null;
  maxRunsScored: number | null;
  minRunsAllowed: number | null;
  maxRunsAllowed: number | null;
}

export interface MLBMatchupDataPoint {
  gameId: string;
  gameDate: string;
  gameName: string;
  gameStatus?: string;
  gameCompleted: boolean;
  homeTeam: MLBTeamInfo;
  awayTeam: MLBTeamInfo;
  location?: { stadium?: string };
  odds?: MLBMatchupOdds;
  comparisons?: NBAComparisons;
  results?: MLBGameResults;
}

export interface MLBMatchupData extends BaseChartData {
  visualizationType: 'MLB_MATCHUP';
  leagueCumRunDiffStats?: LeagueCumRunDiffStats;
  leagueWeeklyStats?: LeagueWeeklyStats;
  dataPoints: MLBMatchupDataPoint[];
}

// MLB Team Report Card types
export interface ReportCardStatValue {
  label: string;
  value?: number | null;
  rank?: number | null;
  rankDisplay?: string | null;
  displayValue?: string | null;
}

export interface ReportCardTeamSummary {
  stats: Record<string, ReportCardStatValue>;
}

export interface ReportCardPlayer {
  playerId: string;
  name: string;
  position?: string;
  status?: string;
  war?: number | null;
  stats: Record<string, ReportCardStatValue>;
}

export interface ReportCardCategory {
  label: string;
  description?: string;
  team?: ReportCardTeamSummary;
  players: ReportCardPlayer[];
}

export interface ReportCardCategories {
  recentTrend?: ReportCardCategory;
  hitters: ReportCardCategory;
  starters: ReportCardCategory;
  relievers: ReportCardCategory;
  fielders: ReportCardCategory;
  belowReplacement?: ReportCardCategory;
  injuries?: ReportCardCategory;
}

export interface ReportCardTeam {
  teamCode: string;
  teamName: string;
  division?: string;
  league?: string;
  wins?: number;
  losses?: number;
  recordRank?: number;
  recordRankDisplay?: string;
  divisionRank?: number;
  divisionRankDisplay?: string;
  overallComposite?: number;
  overallCompositeRank?: number;
  overallCompositeRankDisplay?: string;
  playoffProb?: number;
  categories: ReportCardCategories;
}

export interface RankingEntry {
  team?: string;
  teamCode?: string;
  teamName?: string;
  player?: string;
  value: number;
  rank: number;
  rankDisplay?: string;
}

export interface PlayoffChanceEntry {
  team?: string;
  teamCode?: string;
  teamName?: string;
  playoffProb?: number | null;
  champProb?: number | null;
  conference?: string | null;
  winPct?: number | null;
  wins?: number | null;
  losses?: number | null;
  division?: string | null;
  divisionRank?: number | null;
  gamesBackFromPlayoff?: number | null;
  standingsSection?: string | null;
}

export interface MLBTeamReportCardData extends BaseChartData {
  visualizationType: 'MLB_TEAM_REPORT_CARD';
  season: number;
  topN: number;
  rankings: Record<string, RankingEntry[]>;
  playoffChances: PlayoffChanceEntry[];
  teams: Record<string, ReportCardTeam>;
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

export type ChartData = ScatterPlotData | LineChartData | BarChartData | TableData | MatchupData | MatchupV2Data | NBAMatchupData | NHLMatchupData | MLBMatchupData | MLBTeamReportCardData;

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
