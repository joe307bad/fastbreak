import { pageMetadata } from '@/lib/og';
import { fetchAllTeamRosters } from '@/lib/teams';
import { PinnedTeamsManager } from '@/components/ui/PinnedTeamsManager';

export const metadata = pageMetadata({
  title: 'Pinned Teams',
  description: 'Pin your teams to see their charts first',
});

export default async function PinnedTeamsPage() {
  const rosters = await fetchAllTeamRosters();

  return <PinnedTeamsManager rosters={rosters} />;
}
