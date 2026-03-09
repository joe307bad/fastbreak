import { CaptionedGallery } from './CaptionedGallery';

const images = [
  { src: '/04-casual-fan/05-xg-week-pit-v-phi.png', caption: 'Both teams have a rather stable xG% trend over the season. We can again see that dip in opportunity creation by the Pens.' },
  { src: '/04-casual-fan/07-scoring-leaders.png', caption: 'Crosby is still performing at a high level for the Pens at 38 years old, but he is injured for this matchup.' },
];

export function PensVFlyersBreakdown() {
  return <CaptionedGallery images={images} />;
}
