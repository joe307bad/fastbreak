import { SettingsNav } from '@/components/ui/SettingsNav';

export default function SettingsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="max-w-[1920px] mx-auto px-4 md:px-8 py-8">
      <div className="grid grid-cols-1 md:grid-cols-[240px_1fr] gap-8">
        <aside className="md:sticky md:top-20 md:self-start">
          <SettingsNav />
        </aside>
        <main className="max-w-none">
          {children}
        </main>
      </div>
    </div>
  );
}
