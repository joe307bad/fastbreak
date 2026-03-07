import { ScreenshotGallery } from './ScreenshotGallery';

const images = [
  '/04-casual-fan/01-penguins-v-bruins-post-game.png',
  '/04-casual-fan/02-xg-points-penguins-bruins.png',
  '/04-casual-fan/03-flyers-v-penguins.png',
  '/04-casual-fan/04-pit-playoff-chances.png',
  '/04-casual-fan/05-xg-week-pit-v-phi.png',
  '/04-casual-fan/06-bos-v-pit.png',
  '/04-casual-fan/07-scoring-leaders.png',
  '/04-casual-fan/08-team-efficiency.png',
];

export function HowIsMyTeamGallery() {
  return <ScreenshotGallery images={images} />;
}
