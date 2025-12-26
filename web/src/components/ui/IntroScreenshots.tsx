import { ScreenshotGallery } from './ScreenshotGallery';

const screenshots = [
  '/01-intro-blog-post-screenshots/01-home.png',
  '/01-intro-blog-post-screenshots/02-scatter-plot.png',
  '/01-intro-blog-post-screenshots/03-scatter-plot-1.png',
  '/01-intro-blog-post-screenshots/04-scatter-plot.png',
  '/01-intro-blog-post-screenshots/05-matchup.png',
  '/01-intro-blog-post-screenshots/06-side-menu.png',
];

export function IntroScreenshots() {
  return <ScreenshotGallery images={screenshots} />;
}
