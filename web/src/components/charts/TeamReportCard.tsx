'use client';

import { Children, isValidElement, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import {
  TeamReportCardData,
  ReportCardCategory,
  ReportCardGameLog,
  ReportCardGameLogGame,
  ReportCardPlayer,
  ReportCardStatValue,
  ReportCardTeam,
} from '@/types/chart';
import { usePinnedTeams } from '@/lib/usePinnedTeams';
import {
  buildReportCardLabelIndex,
  ChartInfoSheet,
  formatReportCardRankingLabel,
  formatReportCardStatLabel,
  isReportCardPlayerRankingKey,
  isReportCardRankingPct,
  PlayoffChancesSheet,
  StatRankingsSheet,
} from '@/components/charts/TeamReportCardSheets';

// Fallback table layout for MLB cards published before the payload carried its
// own keys and flags. Anything newer — including every NFL card — drives the
// layout from the category itself, so no sport needs an entry here.
type CategoryKey = string;

const MLB_CATEGORY_ORDER: CategoryKey[] = [
  'recentTrend',
  'hitters',
  'starters',
  'relievers',
  'fielders',
  'belowReplacement',
  'injuries',
];

const CATEGORY_STAT_KEYS: Record<string, string[]> = {
  recentTrend: ['record', 'runDiffPerGame', 'runsPerGame', 'runsAllowedPerGame', 'hitsPerGame', 'hrsPerGame'],
  hitters: ['wRC_plus', 'AVG', 'OPS_plus', 'Barrel_pct'],
  starters: ['K-BB_pct', 'xFIP', 'SIERA', 'ERA'],
  relievers: ['K-BB_pct', 'FIP', 'SV', 'SIERA', 'ERA'],
  fielders: ['OAA', 'DRS', 'FRP'],
  belowReplacement: ['below_replacement_pa_pct'],
  injuries: ['impact'],
};

const CATEGORY_PLAYER_STAT_KEYS: Record<string, string[]> = {
  belowReplacement: ['PA', 'wRC_plus'],
};

const CATEGORY_COMPOSITE_RANKING_KEYS: Record<string, string> = {
  hitters: 'hittersComposite',
  starters: 'startersComposite',
  relievers: 'relieversComposite',
  fielders: 'fieldersComposite',
  injuries: 'injuriesComposite',
};

const CATEGORY_SHOW_STATUS_COLUMN: Record<string, boolean> = {
  injuries: true,
};

const CATEGORY_SHOW_WAR_COLUMN: Record<string, boolean> = {
  belowReplacement: true,
};

const CATEGORY_SHOW_PLAYER_RANK_AND_COMPOSITE: Record<string, boolean> = {
  recentTrend: false,
  belowReplacement: false,
  injuries: false,
};

const CATEGORY_SHOW_TEAM_COMPOSITE: Record<string, boolean> = {
  belowReplacement: false,
};

interface ResolvedCategoryConfig {
  statKeys: string[];
  playerStatKeys: string[];
  positionColumnLabel: string;
  showPlayerRankAndComposite: boolean;
  showStatusColumn: boolean;
  showWarColumn: boolean;
  showTeamComposite: boolean;
  compositeRankingKey?: string;
}

function statKeysFrom(stats: Record<string, ReportCardStatValue> | undefined): string[] {
  return Object.keys(stats ?? {}).filter(key => key !== PLAYER_COMPOSITE_KEY);
}

function resolveCategoryConfig(
  categoryKey: string,
  category: ReportCardCategory
): ResolvedCategoryConfig {
  return {
    statKeys:
      category.statKeys ?? CATEGORY_STAT_KEYS[categoryKey] ?? statKeysFrom(category.team?.stats),
    playerStatKeys:
      category.playerStatKeys ??
      CATEGORY_PLAYER_STAT_KEYS[categoryKey] ??
      CATEGORY_STAT_KEYS[categoryKey] ??
      statKeysFrom(category.players[0]?.stats),
    positionColumnLabel: category.positionColumnLabel ?? 'Pos',
    showPlayerRankAndComposite:
      category.showPlayerRankAndComposite ??
      CATEGORY_SHOW_PLAYER_RANK_AND_COMPOSITE[categoryKey] ??
      true,
    showStatusColumn: category.showStatusColumn ?? CATEGORY_SHOW_STATUS_COLUMN[categoryKey] ?? false,
    showWarColumn: category.showWarColumn ?? CATEGORY_SHOW_WAR_COLUMN[categoryKey] ?? false,
    showTeamComposite:
      category.showTeamComposite ?? CATEGORY_SHOW_TEAM_COMPOSITE[categoryKey] ?? true,
    compositeRankingKey: category.compositeRankingKey ?? CATEGORY_COMPOSITE_RANKING_KEYS[categoryKey],
  };
}

// Category order comes from the payload; older MLB cards fall back to the fixed
// order the page has always used, then to whatever keys the record carries.
function categoryEntries(
  data: TeamReportCardData,
  team: ReportCardTeam
): [string, ReportCardCategory][] {
  const order =
    team.categoryOrder ??
    data.categoryOrder ??
    (Object.keys(team.categories).some(key => MLB_CATEGORY_ORDER.includes(key))
      ? MLB_CATEGORY_ORDER
      : Object.keys(team.categories));
  return order
    .map(key => [key, team.categories[key]] as [string, ReportCardCategory | undefined])
    .filter((entry): entry is [string, ReportCardCategory] => {
      const category = entry[1];
      if (!category) return false;
      return category.players.length > 0 || Object.keys(category.team?.stats ?? {}).length > 0;
    });
}

function teamGameLog(team: ReportCardTeam): ReportCardGameLog | undefined {
  const log = team.gameLog ?? team.lastTenGames;
  return log && log.games.length > 0 ? log : undefined;
}

const PLAYER_COMPOSITE_KEY = 'aggregate';

const PLAYER_NAME_MIN_WIDTH = 'min-w-[6.75rem] w-[6.75rem]';
const STICKY_PLAYER_CELL = `sticky left-0 z-10 ${PLAYER_NAME_MIN_WIDTH} shrink-0 border-r border-[var(--border)] bg-[var(--card)] group-hover:bg-[var(--foreground)]/5`;
const STAT_COL = 'min-w-[5.5rem] px-2 whitespace-nowrap';

const INVALID_DISPLAY_VALUES = ['null', 'NA', 'undefined', 'nul'];

function formatStatValue(stat: ReportCardStatValue | undefined): string {
  if (!stat) return '-';
  // Filter out invalid displayValue strings like "null", "NA", etc.
  if (stat.displayValue && !INVALID_DISPLAY_VALUES.includes(stat.displayValue)) return stat.displayValue;
  if (stat.value == null) return '-';
  if (stat.label === 'Run Diff/G') {
    const formatted = stat.value.toFixed(2);
    return stat.value >= 0 ? `+${formatted}` : formatted;
  }
  if (stat.label.toLowerCase().includes('%') || stat.label.includes('+')) {
    return stat.value.toFixed(1);
  }
  if (stat.label === 'AVG') {
    return stat.value.toFixed(3);
  }
  if (stat.label === 'FRP' || stat.label === 'SV') {
    return Number.isInteger(stat.value) ? stat.value.toString() : stat.value.toFixed(0);
  }
  if (Number.isInteger(stat.value)) return stat.value.toString();
  return stat.value.toFixed(2);
}

function teamRankBadgeClasses(rank: number | null | undefined): string {
  const base = 'inline-flex items-center justify-center min-w-7 h-4 px-1 rounded text-[10px] font-medium';
  if (rank == null) return `${base} bg-[var(--muted)]/20 text-[var(--muted)]`;
  if (rank <= 10) return `${base} bg-green-500/20 text-green-500`;
  if (rank <= 15) return `${base} bg-lime-500/20 text-lime-600 dark:text-lime-400`;
  if (rank <= 20) return `${base} bg-orange-500/20 text-orange-500`;
  return `${base} bg-red-500/20 text-red-500`;
}

function RankBadge({ rank, display }: { rank?: number | null; display?: string | null }) {
  if (rank == null && !display) return null;
  // Filter out invalid display values like "null", "NA", etc.
  const validDisplay = display && !['null', 'NA', 'undefined', 'nul'].includes(display) ? display : null;
  const value = validDisplay ?? (rank != null ? String(rank) : '');
  if (!value) return null;
  return <span className={teamRankBadgeClasses(rank)}>{value}</span>;
}

function playerRankBadgeClasses(rank: number | null | undefined): string {
  const base = 'inline-flex items-center justify-center min-w-7 h-4 px-1 rounded text-[10px] font-medium';
  if (rank == null) return `${base} bg-[var(--muted)]/20 text-[var(--muted)]`;
  if (rank <= 30) return `${base} bg-green-500/20 text-green-500`;
  if (rank <= 60) return `${base} bg-lime-500/20 text-lime-600 dark:text-lime-400`;
  if (rank <= 100) return `${base} bg-orange-500/20 text-orange-500`;
  return `${base} bg-red-500/20 text-red-500`;
}

function PlayerRankBadge({ rank, display }: { rank?: number | null; display?: string | null }) {
  if (rank == null && !display) return null;
  // Filter out invalid display values like "null", "NA", etc.
  const validDisplay = display && !['null', 'NA', 'undefined', 'nul'].includes(display) ? display : null;
  const value = validDisplay ?? (rank != null ? String(rank) : '');
  if (!value) return null;
  return <span className={playerRankBadgeClasses(rank)}>{value}</span>;
}

function StatCell({
  stat,
  playerRank,
  showRank = true,
  onClick,
}: {
  stat?: ReportCardStatValue;
  playerRank?: boolean;
  showRank?: boolean;
  onClick?: () => void;
}) {
  const Badge = playerRank ? PlayerRankBadge : RankBadge;
  const content = (
    <div className="flex items-center justify-end gap-1.5 whitespace-nowrap">
      <span className="font-mono text-sm">{formatStatValue(stat)}</span>
      {showRank && <Badge rank={stat?.rank} display={stat?.rankDisplay} />}
    </div>
  );

  if (!onClick) return content;

  return (
    <button
      type="button"
      onClick={onClick}
      className="cursor-pointer hover:bg-[var(--foreground)]/5 transition-colors rounded px-0.5 -mx-0.5 text-left"
    >
      {content}
    </button>
  );
}

const tableRowClass =
  'py-1 border-b border-[var(--border)] last:border-b-0 hover:bg-[var(--foreground)]/5 transition-colors';

function reportCardStatRankingKey(categoryKey: CategoryKey, statKey: string): string {
  return `${categoryKey}.${statKey}`;
}

function reportCardPlayerRowKey(player: ReportCardPlayer, index: number): string {
  return `${player.playerId}-${player.position ?? ''}-${index}`;
}

function reportCardPlayerStatRankingKey(categoryKey: CategoryKey, statKey: string): string {
  return `${categoryKey}.player.${statKey}`;
}

function getHashTeamAbbrev(): string {
  if (typeof window === 'undefined') return '';
  return decodeURIComponent(window.location.hash.slice(1)).trim().toUpperCase();
}

function resolveTeamCode(
  teams: ReportCardTeam[],
  pinnedCodes: string[],
  hashAbbrev: string
): string {
  if (hashAbbrev) {
    const fromHash = teams.find(t => t.teamCode.toUpperCase() === hashAbbrev);
    if (fromHash) return fromHash.teamCode;
  }
  for (const code of pinnedCodes) {
    const match = teams.find(t => t.teamCode.toUpperCase() === code.toUpperCase());
    if (match) return match.teamCode;
  }
  return teams[0]?.teamCode ?? '';
}

function setHashTeamAbbrev(teamCode: string) {
  const hash = teamCode.toUpperCase();
  if (window.location.hash.slice(1).toUpperCase() !== hash) {
    window.history.replaceState(null, '', `#${hash}`);
  }
}

function TwoColumnMasonry({
  children,
  className = '',
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const items = useMemo(
    () => Children.toArray(children).filter(isValidElement),
    [children]
  );

  return (
    <div className={`columns-1 md:columns-2 md:gap-3 w-full ${className}`}>
      {items.map((child, index) => (
        <div
          key={child.key ?? index}
          className="break-inside-avoid mb-3 w-full min-w-0 last:mb-0"
        >
          {child}
        </div>
      ))}
    </div>
  );
}

function CategoryPanel({
  categoryKey,
  title,
  category,
  teamStatKeys,
  playerStatKeys,
  positionColumnLabel = 'Pos',
  showPlayerRankAndComposite = true,
  showStatusColumn = false,
  showWarColumn = false,
  showTeamComposite = true,
  compositeRankingKey,
  rankings,
  onRankingClick,
}: {
  categoryKey: CategoryKey;
  title: string;
  category: ReportCardCategory;
  teamStatKeys: string[];
  playerStatKeys: string[];
  positionColumnLabel?: string;
  showPlayerRankAndComposite?: boolean;
  showStatusColumn?: boolean;
  showWarColumn?: boolean;
  showTeamComposite?: boolean;
  compositeRankingKey?: string;
  rankings: TeamReportCardData['rankings'];
  onRankingClick: (key: string) => void;
}) {
  const statLabels = Object.fromEntries(
    [...teamStatKeys, ...playerStatKeys].map(key => [
      key,
      category.players[0]?.stats[key]?.label ??
        category.team?.stats[key]?.label ??
        formatReportCardStatLabel(categoryKey, key),
    ])
  );

  const hasTeam = !!category.team;
  const hasPlayers = category.players.length > 0;
  const composite = showTeamComposite ? category.team?.stats[PLAYER_COMPOSITE_KEY] : undefined;

  if (!hasTeam && !hasPlayers) return null;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] w-full max-w-full min-w-0 box-border">
      <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30 items-center">
        <div />
        <div className="text-center text-xs font-bold">{title}</div>
        <div />
      </div>

      <div className="px-2">
        {hasTeam &&
          teamStatKeys.map(key => {
            const stat = category.team!.stats[key];
            if (!stat) return null;
            const rankingKey = reportCardStatRankingKey(categoryKey, key);
            const hasRankings = (rankings[rankingKey]?.length ?? 0) > 0;
            return (
              <TeamStatRow
                key={key}
                stat={stat}
                onClick={hasRankings ? () => onRankingClick(rankingKey) : undefined}
              />
            );
          })}
        {composite && (
          <TeamStatRow
            stat={composite}
            onClick={
              compositeRankingKey && (rankings[compositeRankingKey]?.length ?? 0) > 0
                ? () => onRankingClick(compositeRankingKey)
                : undefined
            }
          />
        )}
      </div>

      {hasPlayers && (
        <PlayerTable
          players={category.players}
          statKeys={playerStatKeys}
          labels={statLabels}
          categoryKey={categoryKey}
          rankings={rankings}
          onRankingClick={onRankingClick}
          positionColumnLabel={positionColumnLabel}
          showPlayerRankAndComposite={showPlayerRankAndComposite}
          showStatusColumn={showStatusColumn}
          showWarColumn={showWarColumn}
        />
      )}
    </div>
  );
}

function TeamStatRow({ stat, onClick }: { stat?: ReportCardStatValue; onClick?: () => void }) {
  const Wrapper = onClick ? 'button' : 'div';
  return (
    <Wrapper
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={`grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 text-sm items-center w-full ${tableRowClass} ${
        onClick ? 'cursor-pointer hover:bg-[var(--foreground)]/5 transition-colors text-left' : ''
      }`}
    >
      <div className="flex items-center justify-end gap-1 pr-1">
        <StatCell stat={stat} />
      </div>
      <div className="text-center text-xs text-[var(--muted)] truncate px-1">
        {stat?.label ?? '-'}
      </div>
      <div />
    </Wrapper>
  );
}

function formatGameDifferential(diff: number | null | undefined): string {
  if (diff == null) return '-';
  return diff > 0 ? `+${diff}` : String(diff);
}

function gameDifferentialClass(game: ReportCardGameLogGame): string {
  if (game.played === false) return 'text-[var(--muted)]';
  if (game.won === true) return 'text-green-500';
  if (game.won === false) return 'text-red-500';
  return '';
}

function GameLogPanel({ gameLog }: { gameLog: ReportCardGameLog }) {
  const games = gameLog.games ?? [];
  if (games.length === 0) return null;

  const record = gameLog.record;
  const recordDisplay = record?.display ?? `${record?.wins ?? 0}-${record?.losses ?? 0}`;
  // Older payloads predate totalDifferential; sum the shown games instead.
  const totalDiff =
    gameLog.totalDifferential ?? games.reduce((sum, game) => sum + (game.differential ?? 0), 0);
  // A full NFL schedule carries week numbers; the MLB last-10 ledger does not,
  // and gains nothing from an empty column.
  const showWeek = games.some(game => game.week != null);
  const playedCount = games.filter(game => game.played !== false).length;

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] w-full max-w-full min-w-0 box-border">
      <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30 items-center">
        <div />
        <div className="text-center text-xs font-bold whitespace-nowrap">{gameLog.label}</div>
        <div />
      </div>

      <div className="min-w-0 max-w-full overflow-x-auto overscroll-x-contain">
        <table className="w-full min-w-full text-sm border-collapse">
          <thead>
            <tr className="border-b border-[var(--border)] bg-[var(--card)] text-xs font-bold">
              {showWeek && <th className="py-1 pl-2 pr-1 text-left whitespace-nowrap">Wk</th>}
              <th className="py-1 pl-2 pr-1 text-left whitespace-nowrap">Opponent</th>
              <th className="py-1 px-2 text-right whitespace-nowrap">Opp</th>
              <th className="py-1 px-2 text-right whitespace-nowrap">Team</th>
              <th className="py-1 pl-2 pr-2 text-right whitespace-nowrap">Diff</th>
            </tr>
          </thead>
          <tbody>
            {games.map((game, index) => (
              <tr
                key={`${game.date ?? ''}-${game.opponent}-${index}`}
                className="border-b border-[var(--border)] last:border-b-0 hover:bg-[var(--foreground)]/5"
              >
                {showWeek && (
                  <td className="py-1 pl-2 pr-1 text-xs text-[var(--muted)] whitespace-nowrap">
                    {game.week ?? '-'}
                  </td>
                )}
                <td className="py-1 pl-2 pr-1 font-medium whitespace-nowrap">
                  <span className="text-[var(--muted)]">{game.location}</span> {game.opponent}
                </td>
                <td className="py-1 px-2 text-right font-mono whitespace-nowrap">
                  {game.opponentScore ?? '-'}
                </td>
                <td className="py-1 px-2 text-right font-mono whitespace-nowrap">
                  {game.teamScore ?? '-'}
                </td>
                <td
                  className={`py-1 pl-2 pr-2 text-right font-mono whitespace-nowrap ${gameDifferentialClass(game)}`}
                >
                  {formatGameDifferential(game.differential)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="grid grid-cols-[1fr_auto] gap-2 px-2 py-1 border-t border-[var(--border)] items-center text-xs">
        <span className="text-[var(--muted)] whitespace-nowrap">Record ({playedCount} games)</span>
        <span className="font-mono font-medium">{recordDisplay}</span>
        <span className="text-[var(--muted)] whitespace-nowrap">Total Diff</span>
        <span
          className={`font-mono font-medium ${
            totalDiff > 0 ? 'text-green-500' : totalDiff < 0 ? 'text-red-500' : ''
          }`}
        >
          {formatGameDifferential(totalDiff)}
        </span>
      </div>
    </div>
  );
}

function PlayerTable({
  players,
  statKeys,
  labels,
  categoryKey,
  rankings,
  onRankingClick,
  positionColumnLabel = 'Pos',
  showPlayerRankAndComposite = true,
  showStatusColumn = false,
  showWarColumn = false,
}: {
  players: ReportCardPlayer[];
  statKeys: string[];
  labels: Record<string, string>;
  categoryKey: CategoryKey;
  rankings: TeamReportCardData['rankings'];
  onRankingClick: (key: string) => void;
  positionColumnLabel?: string;
  showPlayerRankAndComposite?: boolean;
  showStatusColumn?: boolean;
  showWarColumn?: boolean;
}) {
  return (
    <div className="min-w-0 max-w-full overflow-x-auto overscroll-x-contain">
      <table className="w-max min-w-full text-sm border-collapse">
        <thead>
          <tr className="border-b border-[var(--border)] bg-[var(--card)] text-xs font-bold">
            <th className={`${STICKY_PLAYER_CELL} z-20 py-1 pl-2 pr-1 text-left`}>
              Player
            </th>
            <th className="py-1 px-2 text-center whitespace-nowrap w-9">{positionColumnLabel}</th>
            {showStatusColumn && (
              <th className="py-1 px-2 text-left whitespace-nowrap min-w-[4.5rem]">Status</th>
            )}
            {showWarColumn && (
              <th className="py-1 px-2 text-right whitespace-nowrap w-9">WAR</th>
            )}
            {statKeys.map(key => (
              <th key={key} className={`py-1 text-right ${STAT_COL}`}>
                {labels[key] ?? key}
              </th>
            ))}
            {showPlayerRankAndComposite && (
              <th className={`py-1 text-right ${STAT_COL}`}>Comp</th>
            )}
          </tr>
        </thead>
        <tbody>
          {players.map((player, index) => (
            <tr
              key={reportCardPlayerRowKey(player, index)}
              className="group border-b border-[var(--border)] last:border-b-0 hover:bg-[var(--foreground)]/5"
            >
              <td className={`${STICKY_PLAYER_CELL} py-1 pl-2 pr-1 font-medium truncate`}>
                {player.name}
              </td>
              <td className="py-1 px-2 text-xs text-[var(--muted)] text-center whitespace-nowrap w-9">
                {player.position ?? '-'}
              </td>
              {showStatusColumn && (
                <td className="py-1 px-2 text-xs text-[var(--muted)] text-left whitespace-nowrap min-w-[4.5rem]">
                  {player.status ?? '-'}
                </td>
              )}
              {showWarColumn && (
                <td className="py-1 px-2 font-mono text-sm text-right whitespace-nowrap w-9">
                  {player.war != null ? player.war.toFixed(1) : '-'}
                </td>
              )}
              {statKeys.map(key => {
                const rankingKey = reportCardPlayerStatRankingKey(categoryKey, key);
                const hasRankings = (rankings[rankingKey]?.length ?? 0) > 0;
                return (
                  <td key={key} className={`py-1 text-right ${STAT_COL}`}>
                    <StatCell
                      stat={player.stats[key]}
                      playerRank={showPlayerRankAndComposite}
                      showRank={showPlayerRankAndComposite}
                      onClick={hasRankings ? () => onRankingClick(rankingKey) : undefined}
                    />
                  </td>
                );
              })}
              {showPlayerRankAndComposite && (
                <td className={`py-1 text-right ${STAT_COL}`}>
                  <StatCell
                    stat={player.stats[PLAYER_COMPOSITE_KEY]}
                    playerRank
                    showRank
                  />
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function getPlayoffProbRank(
  playoffChances: TeamReportCardData['playoffChances'],
  teamCode: string
): { rank: number; rankDisplay: string } | null {
  const idx = playoffChances.findIndex(
    entry => (entry.teamCode ?? entry.team)?.toUpperCase() === teamCode.toUpperCase()
  );
  if (idx === -1) return null;
  return { rank: idx + 1, rankDisplay: String(idx + 1) };
}

function TeamSummaryStat({
  label,
  value,
  rank,
  rankDisplay,
  onClick,
  interactive,
}: {
  label: string;
  value: string;
  rank?: number | null;
  rankDisplay?: string | null;
  onClick?: () => void;
  interactive?: boolean;
}) {
  const Wrapper = onClick ? 'button' : 'div';
  return (
    <Wrapper
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={`flex items-center gap-1.5 text-xs text-[var(--muted)] ${
        interactive ? 'cursor-pointer hover:text-[var(--foreground)] transition-colors' : ''
      }`}
    >
      <span>{label}</span>
      <span className="font-mono text-[var(--foreground)]">{value}</span>
      <RankBadge rank={rank} display={rankDisplay} />
    </Wrapper>
  );
}

interface Props {
  data: TeamReportCardData;
}

export function TeamReportCard({ data }: Props) {
  const { getPinnedForSport, mounted } = usePinnedTeams();

  const sportKey = (data.sport ?? 'mlb').toLowerCase();
  const pinnedCodes = useMemo(
    () => (mounted ? getPinnedForSport(sportKey) : []).map(t => t.teamCode),
    [getPinnedForSport, mounted, sportKey]
  );

  const teams = useMemo(() => {
    const allTeams = Object.values(data.teams);
    const pinnedSet = new Set(pinnedCodes.map(code => code.toUpperCase()));
    const pinnedTeamsList = pinnedCodes
      .map(code => allTeams.find(t => t.teamCode.toUpperCase() === code.toUpperCase()))
      .filter((t): t is ReportCardTeam => t != null);
    const remainingTeams = allTeams
      .filter(t => !pinnedSet.has(t.teamCode.toUpperCase()))
      .sort((a, b) => a.teamName.localeCompare(b.teamName));
    return [...pinnedTeamsList, ...remainingTeams];
  }, [data.teams, pinnedCodes]);

  const [selectedTeamCode, setSelectedTeamCode] = useState<string | null>(null);

  useLayoutEffect(() => {
    const hashAbbrev = getHashTeamAbbrev();
    if (hashAbbrev) {
      const fromHash = teams.find(t => t.teamCode.toUpperCase() === hashAbbrev);
      if (fromHash) {
        setSelectedTeamCode(fromHash.teamCode);
        return;
      }
    }

    if (!mounted) return;
    setSelectedTeamCode(resolveTeamCode(teams, pinnedCodes, ''));
  }, [mounted, teams, pinnedCodes]);

  useEffect(() => {
    const handleHashChange = () => {
      const hashAbbrev = getHashTeamAbbrev();
      if (!hashAbbrev) return;
      const match = teams.find(t => t.teamCode.toUpperCase() === hashAbbrev);
      if (match) setSelectedTeamCode(match.teamCode);
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, [teams]);

  const activeTeamCode = selectedTeamCode ?? '';
  const team = selectedTeamCode ? data.teams[activeTeamCode] : undefined;
  const gameLog = team ? teamGameLog(team) : undefined;
  const playoffRank = team ? getPlayoffProbRank(data.playoffChances, team.teamCode) : null;

  const [rankingSheetKey, setRankingSheetKey] = useState<string | null>(null);
  const [showPlayoffSheet, setShowPlayoffSheet] = useState(false);
  const [showInfoSheet, setShowInfoSheet] = useState(false);

  const hasDescription = Boolean(data.description?.trim());

  // The pipeline ships a formatted season label; older payloads did not, so
  // fall back to the MLB-style "2025-26" the card has always shown.
  const seasonLabel = useMemo(() => {
    if (data.seasonLabel?.trim()) return data.seasonLabel;
    const nextYear = (data.season + 1) % 100;
    return `${data.season}-${nextYear.toString().padStart(2, '0')}`;
  }, [data.season, data.seasonLabel]);

  const labelIndex = useMemo(() => buildReportCardLabelIndex(data.teams), [data.teams]);

  const hasOverallRankings = (data.rankings.overallComposite?.length ?? 0) > 0;
  const hasPlayoffChances = data.playoffChances.length > 0;

  const handleTeamChange = (teamCode: string) => {
    setSelectedTeamCode(teamCode);
    setHashTeamAbbrev(teamCode);
  };

  if (!selectedTeamCode || !team) {
    if (mounted && selectedTeamCode && !team) {
      return (
        <div className="h-full flex items-center justify-center text-[var(--muted)] text-sm">
          No team report card data available
        </div>
      );
    }
    return null;
  }

  return (
    <div className="h-full min-h-0 flex flex-col">
      <div className="mx-auto w-full max-w-full flex-1 min-h-0 flex flex-col gap-3">
        <div className="max-w-3xl w-full mx-auto flex flex-wrap items-center gap-3 shrink-0">
        <div className="flex items-center gap-1">
          <select
            value={activeTeamCode}
            onChange={e => handleTeamChange(e.target.value)}
            className="text-sm rounded px-2 py-1 bg-[var(--muted)]/10"
          >
            {teams.map(t => (
              <option key={t.teamCode} value={t.teamCode}>
                {t.teamCode} · {t.teamName}
              </option>
            ))}
          </select>
          {hasDescription && (
            <button
              type="button"
              onClick={() => setShowInfoSheet(true)}
              className="p-1 rounded hover:bg-[var(--border)] text-[var(--muted)] hover:text-[var(--foreground)] transition-colors shrink-0"
              aria-label="Chart info"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {team.wins != null && team.losses != null && (
            <span className="text-xs text-[var(--muted)]">
              {team.wins}-{team.losses}
              {team.ties != null && team.ties > 0 ? `-${team.ties}` : ''}
            </span>
          )}
          {team.overallComposite != null && (
            <TeamSummaryStat
              label="Composite"
              value={team.overallComposite.toFixed(1)}
              rank={team.overallCompositeRank}
              rankDisplay={team.overallCompositeRankDisplay}
              interactive={hasOverallRankings}
              onClick={
                hasOverallRankings ? () => setRankingSheetKey('overallComposite') : undefined
              }
            />
          )}
          {team.playoffProb != null && (
            <TeamSummaryStat
              label="Playoffs"
              value={`${team.playoffProb.toFixed(0)}%`}
              rank={playoffRank?.rank}
              rankDisplay={playoffRank?.rankDisplay}
              interactive={hasPlayoffChances}
              onClick={hasPlayoffChances ? () => setShowPlayoffSheet(true) : undefined}
            />
          )}
        </div>
      </div>

      <div className="flex-1 min-h-0 w-full max-w-full overflow-y-auto overflow-x-hidden">
        <TwoColumnMasonry className="pb-8">
          {[
            ...categoryEntries(data, team).map(([key, category]) => {
              const config = resolveCategoryConfig(key, category);
              return (
                <CategoryPanel
                  key={key}
                  categoryKey={key}
                  title={category.label}
                  category={category}
                  teamStatKeys={config.statKeys}
                  playerStatKeys={config.playerStatKeys}
                  positionColumnLabel={config.positionColumnLabel}
                  showPlayerRankAndComposite={config.showPlayerRankAndComposite}
                  showStatusColumn={config.showStatusColumn}
                  showWarColumn={config.showWarColumn}
                  showTeamComposite={config.showTeamComposite}
                  compositeRankingKey={config.compositeRankingKey}
                  rankings={data.rankings}
                  onRankingClick={setRankingSheetKey}
                />
              );
            }),
            ...(gameLog ? [<GameLogPanel key="gameLog" gameLog={gameLog} />] : []),
          ]}
        </TwoColumnMasonry>
      </div>
      </div>

      {rankingSheetKey && (
        <StatRankingsSheet
          open={!!rankingSheetKey}
          onClose={() => setRankingSheetKey(null)}
          title={formatReportCardRankingLabel(seasonLabel, rankingSheetKey, labelIndex)}
          entries={data.rankings[rankingSheetKey] ?? []}
          highlightedTeam={activeTeamCode}
          isPct={isReportCardRankingPct(rankingSheetKey)}
          subtitle={
            isReportCardPlayerRankingKey(rankingSheetKey) ? 'Player Rankings' : 'Season Rankings'
          }
          source={data.source}
        />
      )}

      <PlayoffChancesSheet
        open={showPlayoffSheet}
        onClose={() => setShowPlayoffSheet(false)}
        title={`${seasonLabel} / Playoff Chances`}
        entries={data.playoffChances}
        teams={data.teams}
        highlightedTeam={activeTeamCode}
      />

      {hasDescription && (
        <ChartInfoSheet
          open={showInfoSheet}
          onClose={() => setShowInfoSheet(false)}
          title={data.title}
          description={data.description!}
          source={data.source}
        />
      )}
    </div>
  );
}

export function TeamReportCardSummary({ data }: Props) {
  const teams = useMemo(
    () => Object.values(data.teams).sort((a, b) => (a.overallCompositeRank ?? 99) - (b.overallCompositeRank ?? 99)),
    [data.teams]
  );

  return (
    <div className="border border-[var(--border)] rounded bg-[var(--card)] h-full min-h-0 flex flex-col">
      <div className="grid grid-cols-[1fr_minmax(60px,1fr)_1fr] gap-0 px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30 items-center shrink-0">
        <div />
        <div className="text-center text-xs font-bold">Team Rankings</div>
        <div />
      </div>
      <div
        className="grid gap-x-3 px-2 py-1 border-b border-[var(--border)] bg-[var(--border)]/30 text-xs font-bold items-center shrink-0"
        style={{ gridTemplateColumns: '3rem minmax(0, 1fr) 3rem 3.5rem 2.5rem' }}
      >
        <div>Team</div>
        <div>Name</div>
        <div className="text-right">W-L</div>
        <div className="text-right">Comp</div>
        <div className="text-right">Rank</div>
      </div>
      <div className="px-2 overflow-y-auto flex-1 min-h-0">
        {teams.slice(0, 10).map(team => (
          <TeamSummaryRow key={team.teamCode} team={team} />
        ))}
      </div>
    </div>
  );
}

function TeamSummaryRow({ team }: { team: ReportCardTeam }) {
  return (
    <div
      className={`grid gap-x-3 text-xs items-center whitespace-nowrap ${tableRowClass}`}
      style={{ gridTemplateColumns: '3rem minmax(0, 1fr) 3rem 3.5rem 2.5rem' }}
    >
      <span className="font-bold">{team.teamCode}</span>
      <span className="text-[var(--muted)] truncate">{team.teamName}</span>
      <span className="text-[var(--muted)] text-right">
        {team.wins != null && team.losses != null ? `${team.wins}-${team.losses}` : '-'}
      </span>
      <span className="font-mono text-right">{team.overallComposite?.toFixed(1) ?? '-'}</span>
      <div className="flex justify-end">
        <RankBadge rank={team.overallCompositeRank} display={team.overallCompositeRankDisplay} />
      </div>
    </div>
  );
}
