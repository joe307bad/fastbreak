import { CaptionedGallery } from './CaptionedGallery';

const images = [
  { src: '/04-casual-fan/04-pit-playoff-chances.png', caption: 'The Pens are 7th in their conference and have a good playoff outlook. But their chances of playoff success are low enough that the final month of the season is going to be important.' },
  { src: '/04-casual-fan/06-bos-v-pit.png', caption: 'Their next matchup worksheet shows the Pens are playing the Bruins, who would be the last team in the East to make the playoffs, right behind the Pens; an important game for two teams trying to finish the season strong.' },
];

export function HowWillMyTeamDo() {
  return <CaptionedGallery images={images} />;
}
