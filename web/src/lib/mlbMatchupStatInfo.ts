export interface StatInfoItem {
  label: string;
  text: string;
}

export interface StatInfoSection {
  title: string;
  items: StatInfoItem[];
}

export const MLB_MATCHUP_STAT_INFO = {
  intro: 'Season stats through today. Lower rank is better unless noted.',
  general: [
    { label: 'Rank badge', text: 'Green = top 10, red = 21+.' },
    { label: 'Edge arrow', text: 'Points to the team with the better rank on that row.' },
    { label: 'RD/G', text: 'Run differential per game over the last month.' },
    { label: 'Month rank', text: 'Win-loss standing over the last ~30 days.' },
  ] satisfies StatInfoItem[],
  sections: [
    {
      title: 'Batting',
      items: [
        { label: 'Runs/Game', text: 'Average runs scored per game.' },
        { label: 'Batting Avg', text: 'Hits per at-bat.' },
        { label: 'On-Base %', text: 'Rate of reaching base safely.' },
        { label: 'Slugging %', text: 'Total bases per at-bat.' },
        { label: 'OPS', text: 'On-base plus slugging.' },
        { label: 'Hits/Game', text: 'Average hits per game.' },
        { label: 'HR/Game', text: 'Average home runs per game.' },
        { label: 'RBI/Game', text: 'Average runs batted in per game.' },
        { label: 'SB/Game', text: 'Average stolen bases per game.' },
      ],
    },
    {
      title: 'Pitching',
      items: [
        { label: 'ERA', text: 'Earned runs per 9 innings. Lower is better.' },
        { label: 'WHIP', text: 'Walks plus hits per inning. Lower is better.' },
        { label: 'K/9', text: 'Strikeouts per 9 innings.' },
        { label: 'BB/9', text: 'Walks per 9 innings. Lower is better.' },
        { label: 'Runs Allowed/Game', text: 'Average runs allowed. Lower is better.' },
        { label: 'Hits Allowed/Game', text: 'Average hits allowed. Lower is better.' },
        { label: 'HR Allowed/Game', text: 'Average homers allowed. Lower is better.' },
      ],
    },
    {
      title: 'Fielding',
      items: [
        { label: 'Fielding %', text: 'Share of chances handled cleanly.' },
        { label: 'Errors/Game', text: 'Average errors. Lower is better.' },
      ],
    },
  ] satisfies StatInfoSection[],
};
