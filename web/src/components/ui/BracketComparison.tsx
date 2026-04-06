'use client';

export function BracketComparison({
  name,
  url,
  children,
}: {
  name: string;
  url: string;
  children: React.ReactNode;
}) {
  return (
    <div className="overflow-hidden my-12">
      <h3 className="text-lg font-bold mb-2">{name}</h3>
      <div className="grid grid-cols-1 md:grid-cols-2">
        <video
          src={url}
          controls
          playsInline
          preload="metadata"
          className="w-full h-full object-cover"
        />
        <div className="p-4 space-y-4 flex flex-col justify-start">
          {children}
        </div>
      </div>
    </div>
  );
}

export function Pros({ children }: { children: React.ReactNode }) {
  return (
    <div>
      <h4 className="font-bold text-sm mb-2 text-green-500">Pros</h4>
      <ul className="list-disc ml-4 space-y-1 text-sm text-[var(--muted)]">
        {children}
      </ul>
    </div>
  );
}

export function Cons({ children }: { children: React.ReactNode }) {
  return (
    <div>
      <h4 className="font-bold text-sm mb-2 text-red-500">Cons</h4>
      <ul className="list-disc ml-4 space-y-1 text-sm text-[var(--muted)]">
        {children}
      </ul>
    </div>
  );
}
