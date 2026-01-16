import { CaptionedGallery } from './CaptionedGallery';

const examples = [
  {
    src: '/03-sports-talk-post/05-joker.jpg',
    caption: "Joker's true shooting percentage compared to his usage is off the charts."
  },
  {
    src: '/03-sports-talk-post/06-worksheet.jpg',
    caption: "The Seattle game is a lock right? I can't see the San Fran defense stifling this offense; Seattle is the second rated defense as well."
  },
  {
    src: '/03-sports-talk-post/07-crosby.jpg',
    caption: "It's awesome to see Crosby and the Penguins still contending."
  }
];

export function SportsTalkExamplesGallery() {
  return <CaptionedGallery images={examples} />;
}
