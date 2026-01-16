import { ScreenshotGallery } from './ScreenshotGallery';

const images = [
  '/03-sports-talk-post/01-team-tier.jpg',
  '/03-sports-talk-post/02-line-graph.jpg',
  '/03-sports-talk-post/03-turnover.jpg',
];

export function SportsTalkGallery() {
  return <ScreenshotGallery images={images} />;
}
