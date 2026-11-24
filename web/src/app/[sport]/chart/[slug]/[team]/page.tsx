import { notFound } from 'next/navigation';
import { fetchAllCharts, fetchChartData, slugToKey, keyToSlug } from '@/lib/api';
import { pageMetadata } from '@/lib/og';
import { REPORT_CARD_TYPES } from '@/lib/charts';
import type { ReportCardTeam, TeamReportCardData } from '@/types/chart';
import { ReportCardHandoff } from '@/components/charts/ReportCardHandoff';

interface Props {
  params: Promise<{ sport: string; slug: string; team: string }>;
}

function isReportCard(data: { visualizationType: string }): data is TeamReportCardData {
  return REPORT_CARD_TYPES.includes(data.visualizationType as TeamReportCardData['visualizationType']);
}

function findTeam(data: TeamReportCardData, team: string): ReportCardTeam | undefined {
  return Object.values(data.teams).find(t => t.teamCode.toLowerCase() === team.toLowerCase());
}

// The chart subtitle reads "Each team's ..."; make it about this team.
function teamSubtitle(team: ReportCardTeam, subtitle?: string): string {
  const possessive = team.teamName.endsWith('s') ? `${team.teamName}'` : `${team.teamName}'s`;
  if (subtitle && /^each team'?s\s/i.test(subtitle)) {
    return subtitle.replace(/^each team'?s\s/i, `${possessive} `);
  }
  return subtitle ? `${team.teamName}: ${subtitle}` : `${team.teamName} report card`;
}

/** One page per team for every report card chart, so each team unfurls with its own OG card. */
export async function generateStaticParams() {
  const charts = await fetchAllCharts();
  return charts.flatMap(({ key, data }) =>
    isReportCard(data)
      ? Object.values(data.teams).map(team => ({
          sport: data.sport.toLowerCase(),
          slug: keyToSlug(key),
          team: team.teamCode.toLowerCase(),
        }))
      : []
  );
}

export async function generateMetadata({ params }: Props) {
  const { slug, team: teamParam } = await params;
  const data = await fetchChartData(slugToKey(slug));
  const team = isReportCard(data) ? findTeam(data, teamParam) : undefined;
  if (!team) {
    return pageMetadata({ title: data.title, description: data.description || data.subtitle, subtitle: data.subtitle });
  }
  return pageMetadata({
    title: data.title,
    description: `${team.teamName}: ${data.description || data.subtitle}`,
    subtitle: teamSubtitle(team, data.subtitle),
  });
}

export default async function ChartTeamPage({ params }: Props) {
  const { sport, slug, team: teamParam } = await params;
  const key = slugToKey(slug);
  const data = await fetchChartData(key);
  const team = isReportCard(data) ? findTeam(data, teamParam) : undefined;
  if (!team) notFound();

  // Only the team's code and name cross into the page; `data` was read for the
  // metadata above and stays on the build machine. See ReportCardHandoff for
  // why this route no longer renders the card itself.
  return (
    <ReportCardHandoff
      href={`/${sport}/chart/${slug}/#${encodeURIComponent(team.teamCode.toUpperCase())}`}
      teamName={team.teamName}
    />
  );
}
