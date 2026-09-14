import Link from 'next/link';
import { pageMetadata } from '@/lib/og';
import { getDiagnostics } from '@/lib/data';
import { DiagnosticsTable } from '@/components/ui/DiagnosticsTable';

export const metadata = pageMetadata({
  title: 'Diagnostics - fastbreak',
  description: 'Status, timing, and errors for every scheduled chart pipeline script',
});

export default function DiagnosticsPage() {
  const { runs, fetchedAt, fetchError } = getDiagnostics();
  const failed = runs.filter((run) => run.status === 'failed').length;
  const succeeded = runs.length - failed;

  return (
    <main className="max-w-6xl mx-auto px-4 md:px-8 py-8">
      <Link
        href="/"
        className="inline-block mb-6 text-sm text-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
      >
        ← Back to Home
      </Link>

      <header className="mb-6">
        <h1 className="text-3xl md:text-4xl font-bold mb-2">Pipeline Diagnostics</h1>
        <p className="text-sm text-[var(--muted)]">
          Last recorded run of every scheduled script. Each script writes one row when it finishes; a
          failed row keeps the tail of its output.
        </p>
      </header>

      <div className="flex flex-wrap gap-3 mb-6 text-sm">
        <span className="px-3 py-1.5 border border-[var(--border)] rounded bg-[var(--card)]">
          {runs.length} scripts
        </span>
        <span className="px-3 py-1.5 border border-[var(--border)] rounded bg-[var(--card)] text-emerald-600 dark:text-emerald-400">
          {succeeded} succeeded
        </span>
        <span
          className={`px-3 py-1.5 border border-[var(--border)] rounded bg-[var(--card)] ${
            failed > 0 ? 'text-red-600 dark:text-red-400' : 'text-[var(--muted)]'
          }`}
        >
          {failed} failed
        </span>
        {fetchedAt && (
          <span className="px-3 py-1.5 text-xs text-[var(--muted)] self-center">
            snapshot {new Date(fetchedAt).toUTCString()}
          </span>
        )}
      </div>

      {fetchError && (
        <p className="mb-6 px-3 py-2 text-sm border border-red-300 dark:border-red-800 rounded text-red-600 dark:text-red-400">
          Diagnostics could not be fetched when this site was built: {fetchError}
        </p>
      )}

      <DiagnosticsTable runs={runs} />
    </main>
  );
}
