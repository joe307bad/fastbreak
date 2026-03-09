import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Pinned Teams',
};

export default function PinnedTeamsPage() {
  return (
    <div>
      <h2 className="text-lg font-bold mb-4">Pinned Teams</h2>
      <p className="text-sm text-[var(--muted)]">
        Select your favorite teams to pin them for quick access.
      </p>
    </div>
  );
}
