import { CaptionedGallery } from './CaptionedGallery';

const images = [
  { src: '/04-casual-fan/01-penguins-v-sabres-post-game.png', caption: 'Penguins had recently played (and lost) to the Buffalo Sabres. The low shots on goal, high penalty minutes, and reviewing the Sabres efficiency metrics suggests a tough loss against a good team.' },
  { src: '/04-casual-fan/02-xg-points-penguins-bruins.png', caption: 'Over the past 10 weeks, the Pens have been creating opportunity and taking advantage. The most recent week, however, they\'ve been struggling.' },
  { src: '/04-casual-fan/08-team-efficiency.png', caption: 'Even with a recent skid, the Pens are still one of the best teams in the league as they are among the leaders in goals per game and goals allowed per game.' }
  // ,
];

export function HowIsMyTeamGallery() {
  return <CaptionedGallery images={images} />;
}
