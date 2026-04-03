'use client';

const brackets = [
  {
    name: "Yahoo app",
    url: "https://res.cloudinary.com/joebad-com/video/upload/v1775186156/kigzcum0dtboxbtgjp0u.mp4",
    pros: [
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit",
      "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
      "Ut enim ad minim veniam, quis nostrud exercitation",
    ],
    cons: [
      "Duis aute irure dolor in reprehenderit in voluptate velit",
      "Excepteur sint occaecat cupidatat non proident",
      "Sunt in culpa qui officia deserunt mollit anim id est laborum",
    ],
  },
  {
    name: "CBS Sports app",
    url: "https://res.cloudinary.com/joebad-com/video/upload/v1775186153/gptnmsucjzfqquskgcb3.mp4",
    pros: [
      "Pellentesque habitant morbi tristique senectus et netus",
      "Maecenas sed diam eget risus varius blandit sit amet non magna",
      "Cras mattis consectetur purus sit amet fermentum",
    ],
    cons: [
      "Nullam quis risus eget urna mollis ornare vel eu leo",
      "Vestibulum id ligula porta felis euismod semper",
      "Aenean lacinia bibendum nulla sed consectetur",
    ],
  },
  {
    name: "Google Search results",
    url: "https://res.cloudinary.com/joebad-com/video/upload/v1775186152/x8tnmzibfmht3yq3fqkf.mp4",
    pros: [
      "Fusce dapibus tellus ac cursus commodo tortor mauris",
      "Donec ullamcorper nulla non metus auctor fringilla",
      "Praesent commodo cursus magna vel scelerisque nisl",
    ],
    cons: [
      "Morbi leo risus porta ac consectetur ac vestibulum",
      "Etiam porta sem malesuada magna mollis euismod",
      "Integer posuere erat a ante venenatis dapibus posuere",
    ],
  },
  {
    name: "Real Bracket",
    url: "https://res.cloudinary.com/joebad-com/video/upload/v1775186152/hqq4wjmbdbopswe1txy0.mp4",
    pros: [
      "Vivamus sagittis lacus vel augue laoreet rutrum faucibus",
      "Curabitur blandit tempus porttitor lorem ipsum dolor",
      "Nulla vitae elit libero a pharetra augue donec sed odio",
    ],
    cons: [
      "Cum sociis natoque penatibus et magnis dis parturient",
      "Maecenas faucibus mollis interdum nulla facilisi",
      "Donec id elit non mi porta gravida at eget metus",
    ],
  },
  {
    name: "Fastbreak",
    url: "https://res.cloudinary.com/joebad-com/video/upload/v1775186151/mr8nz4cbtrzub4vhfow8.mp4",
    pros: [
      "Suspendisse potenti nullam ac tortor vitae purus faucibus",
      "Aliquam erat volutpat nam libero tempore soluta nobis",
      "Temporibus autem quibusdam et aut officiis debitis",
    ],
    cons: [
      "Nam quam nunc blandit vel luctus pulvinar hendrerit",
      "Phasellus viverra nulla ut metus varius laoreet",
      "Quisque rutrum aenean imperdiet etiam ultricies nisi",
    ],
  },
  {
    name: "NCAA official app",
    url: "https://res.cloudinary.com/joebad-com/video/upload/v1775186151/ukh7u7qrld62hncavbes.mp4",
    pros: [
      "Praesent egestas neque eu enim in hac habitasse platea",
      "Dictumst quisque sagittis purus sit amet volutpat consequat",
      "Mauris in aliquam sem fringilla ut morbi tincidunt augue",
    ],
    cons: [
      "Egestas integer eget aliquet nibh praesent tristique magna",
      "Sit amet purus gravida quis blandit turpis cursus in hac",
      "Habitasse platea dictumst quisque sagittis purus sit amet",
    ],
  },
];

export function BracketComparison() {
  return (
    <div className="space-y-12 my-8">
      {brackets.map((bracket) => (
        <div key={bracket.name} className="overflow-hidden">
          <h3 className="text-lg font-bold mb-2">{bracket.name}</h3>
          <div className="grid grid-cols-1 md:grid-cols-2">
            <video
              src={bracket.url}
              controls
              playsInline
              preload="metadata"
              className="w-full h-full object-cover"
            />
            <div className="p-4 space-y-4 flex flex-col justify-start">
              <div>
                <h4 className="font-bold text-sm mb-2 text-green-500">Pros</h4>
                <ul className="list-disc ml-4 space-y-1 text-sm text-[var(--muted)]">
                  {bracket.pros.map((pro) => (
                    <li key={pro}>{pro}</li>
                  ))}
                </ul>
              </div>
              <div>
                <h4 className="font-bold text-sm mb-2 text-red-500">Cons</h4>
                <ul className="list-disc ml-4 space-y-1 text-sm text-[var(--muted)]">
                  {bracket.cons.map((con) => (
                    <li key={con}>{con}</li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
