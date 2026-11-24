'use client';

import { useEffect } from 'react';

interface Props {
  href: string;
  teamName: string;
}

/**
 * What a per-team report card URL serves instead of the card itself.
 *
 * Those URLs exist so a shared link unfurls with the team's own OG card -- that
 * is the whole reason the route was added, and an unfurl needs a URL and meta
 * tags, which the page still renders server-side. It does not need the card.
 *
 * Rendering the card here meant handing the client component the entire report
 * card dataset -- all 32 teams, because the picker, the label index, the playoff
 * sheet and the rankings table genuinely read all of them -- once per team page.
 * Thirty-two near-identical copies of the same 1.85 MB, about 700 MB of the
 * export, to vary which team happened to open first.
 *
 * So the team goes in the fragment and the canonical page reads it from there,
 * which is machinery that already existed for `#TEAM` deep links
 * (`getHashTeamAbbrev` in TeamReportCard). One copy of the data, one page, and
 * the visitor lands on the same team they would have.
 *
 * A full document load rather than a router push: the visitor is arriving from
 * somewhere else -- that is what the OG card is for -- so there is no client
 * state worth keeping, and a plain navigation is the version that cannot get
 * the fragment timing wrong. The anchor below is not decoration either; it is
 * what a reader with no JavaScript follows.
 */
export function ReportCardHandoff({ href, teamName }: Props) {
  useEffect(() => {
    window.location.replace(href);
  }, [href]);

  return (
    <main className="max-w-[2000px] mx-auto px-4 py-8">
      <p className="text-sm text-[var(--muted)]">
        Opening the {teamName} report card&hellip;
      </p>
      <a className="text-sm underline" href={href}>
        Continue to the {teamName} report card
      </a>
    </main>
  );
}
