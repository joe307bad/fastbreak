'use client';

import { Children, isValidElement, useMemo, useState } from 'react';
import {
  MLBTeamReportCardData,
  ReportCardCategory,
  ReportCardPlayer,
  ReportCardStatValue,
  ReportCardTeam,
} from '@/types/chart';
import { usePinnedTeams } from '@/lib/usePinnedTeams';
import {
  ChartInfoSheet,
  formatReportCardRankingLabel,
  isReportCardPlayerRankingKey,
  isReportCardRankingPct,
  PlayoffChancesSheet,
  StatRankingsSheet,
} from '@/components/charts/MLBReportCardSheets';

type CategoryKey =
  | 'recentTrend'
  | 'hitters'
  | 'starters'
  | 'relievers'
  | 'fielders'
  | 'belowReplacement'
  | 'injuries';

const CATEGORY_KEYS: CategoryKey[] = [
  'recentTrend',
  'hitters',
  'starters',
  'relievers',
  'fielders',
  'belowReplacement',
  'injuries',
];

const CATEGORY_STAT_KEYS: Record<CategoryKey, string[]> = {
  recentTrend: ['record', 'runDiffPerGame', 'runsPerGame', 'runsAllowedPerGame', 'hitsPerGame', 'hrsPerGame'],
  hitters: ['wRC_plus', 'xwOBA', 'xBA', 'Barrel_pct'],
  starters: ['K-BB_pct', 'xFIP', 'SIERA', 'ERA'],
  relievers: ['K-BB_pct', 'FIP', 'SV', 'SIERA', 'ERA'],
  fielders: ['OAA', 'DRS', 'FRP'],
  belowReplacement: ['below_replacement_pa_pct'],
  injuries: ['impact'],
};

const CATEGORY_PLAYER_STAT_KEYS: Partial<Record<CategoryKey, string[]>> = {
  belowReplacement: ['PA', 'wRC_plus'],
};

const CATEGORY_COMPOSITE_RANKING_KEYS: Partial<Record<CategoryKey, string>> = {
  hitters: 'hittersComposite',
  starters: 'startersComposite',
  relievers: 'relieversComposite',
  fielders: 'fieldersComposite',
  belowReplacement: 'belowReplacementComposite',
  injuries: 'injuriesComposite',
};

const CATEGORY_SHOW_STATUS_COLUMN: Partial<Record<CategoryKey, boolean>> = {
  injuries: true,
};

const CATEGORY_SHOW_PLAYER_RANK_AND_COMPOSITE: Partial<Record<CategoryKey, boolean>> = {
  recentTrend: false,
  belowReplacement: false,
  injuries: false,
};

const PLAYER_COMPOSITE_KEY = 'aggregate';

const PLAYER_NAME_MIN_WIDTH = 'min-w-[6.75rem] w-[6.75rem]';
const STICKY_PLAYER_CELL = `sticky left-0 z-10 ${PLAYER_NAME_MIN_WIDTH} shrink-0 border-r border-[var(--border)] bg-[var(--card)] group-hover:bg-[var(--foreground)]/5`;
const STAT_COL = 'min-w-[5.5rem] px-2 whitespace-nowrap';

function formatStatValue(stat: ReportCardStatValue | undefined): string {
  if (!stat) return '-';
  if (stat.displayValue) return stat.displayValue;
  if (stat.value == null) return '-';
  if (stat.label === 'Run Diff/G') {
    const formatted = stat.value.toFixed(2);
    return stat.value >= 0 ? `+${formatted}` : formatted;
  }
  if (stat.label.toLowerCase().includes('%') || stat.label.includes('+')) {
    return stat.value.toFixed(1);
  }
  if (stat.label === 'xwOBA' || stat.label === 'xBA') {
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
  const value = display ?? String(rank);
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
  const value = display ?? String(rank);
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

function reportCardPlayerStatRankingKey(categoryKey: CategoryKey, statKey: string): string {
  return `${categoryKey}.player.${statKey}`;
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
  rankings: MLBTeamReportCardData['rankings'];
  onRankingClick: (key: string) => void;
}) {
  const statLabels = Object.fromEntries(
    [...teamStatKeys, ...playerStatKeys].map(key => [
      key,
      category.players[0]?.stats[key]?.label ?? category.team?.stats[key]?.label ?? key,
    ])
  );

  const hasTeam = !!category.team;
  const hasPlayers = category.players.length > 0;
  const composite = category.team?.stats[PLAYER_COMPOSITE_KEY];
  const compositeRankingKey = CATEGORY_COMPOSITE_RANKING_KEYS[categoryKey];

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
}: {
  players: ReportCardPlayer[];
  statKeys: string[];
  labels: Record<string, string>;
  categoryKey: CategoryKey;
  rankings: MLBTeamReportCardData['rankings'];
  onRankingClick: (key: string) => void;
  positionColumnLabel?: string;
  showPlayerRankAndComposite?: boolean;
  showStatusColumn?: boolean;
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
          {players.map(player => (
            <tr
              key={player.playerId}
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
  playoffChances: MLBTeamReportCardData['playoffChances'],
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
  data: MLBTeamReportCardData;
}

export function MLBTeamReportCard({ data }: Props) {
  const { getPinnedForSport, mounted } = usePinnedTeams();

  const pinnedCodes = useMemo(
    () => (mounted ? getPinnedForSport('mlb') : []).map(t => t.teamCode),
    [getPinnedForSport, mounted]
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

  const defaultTeamCode = useMemo(() => {
    for (const code of pinnedCodes) {
      const match = teams.find(t => t.teamCode.toUpperCase() === code.toUpperCase());
      if (match) return match.teamCode;
    }
    return teams[0]?.teamCode ?? '';
  }, [teams, pinnedCodes]);

  const [selectedTeamCode, setSelectedTeamCode] = useState('');

  const activeTeamCode = selectedTeamCode || defaultTeamCode;
  const team = mounted ? data.teams[activeTeamCode] : undefined;
  const playoffRank = team ? getPlayoffProbRank(data.playoffChances, team.teamCode) : null;

  const [rankingSheetKey, setRankingSheetKey] = useState<string | null>(null);
  const [showPlayoffSheet, setShowPlayoffSheet] = useState(false);
  const [showInfoSheet, setShowInfoSheet] = useState(false);

  const hasDescription = Boolean(data.description?.trim());

  const seasonLabel = useMemo(() => {
    const nextYear = (data.season + 1) % 100;
    return `${data.season}-${nextYear.toString().padStart(2, '0')}`;
  }, [data.season]);

  const hasOverallRankings = (data.rankings.overallComposite?.length ?? 0) > 0;
  const hasPlayoffChances = data.playoffChances.length > 0;

  if (!mounted || !team) {
    if (mounted && !team) {
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
            onChange={e => setSelectedTeamCode(e.target.value)}
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
            <span className="text-xs text-[var(--muted)]">{team.wins}-{team.losses}</span>
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
          {CATEGORY_KEYS.flatMap(key => {
            const category =
              key === 'recentTrend'
                ? team.categories.recentTrend
                : key === 'belowReplacement'
                  ? team.categories.belowReplacement
                  : team.categories[key];
            if (!category) return [];
            return [
              <CategoryPanel
                key={key}
                categoryKey={key}
                title={category.label}
                category={category}
                teamStatKeys={CATEGORY_STAT_KEYS[key]}
                playerStatKeys={CATEGORY_PLAYER_STAT_KEYS[key] ?? CATEGORY_STAT_KEYS[key]}
                showPlayerRankAndComposite={CATEGORY_SHOW_PLAYER_RANK_AND_COMPOSITE[key] ?? true}
                showStatusColumn={CATEGORY_SHOW_STATUS_COLUMN[key] ?? false}
                rankings={data.rankings}
                onRankingClick={setRankingSheetKey}
              />,
            ];
          })}
        </TwoColumnMasonry>
      </div>
      </div>

      {rankingSheetKey && (
        <StatRankingsSheet
          open={!!rankingSheetKey}
          onClose={() => setRankingSheetKey(null)}
          title={formatReportCardRankingLabel(seasonLabel, rankingSheetKey)}
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

export function MLBTeamReportCardSummary({ data }: Props) {
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
