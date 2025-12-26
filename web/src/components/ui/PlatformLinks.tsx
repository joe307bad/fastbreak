export function PlatformLinks() {
  return (
    <div className="space-y-3 my-6">
      <a
        href="https://testflight.apple.com/join/NnxqNunM"
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-3 p-3 border border-[var(--border)] rounded hover:bg-[var(--border)] transition-colors"
      >
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
          <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
        </svg>
        <span>iOS (Beta - Join TestFlight)</span>
      </a>

      <a
        href="https://groups.google.com/g/fastbreak-beta-testers"
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-3 p-3 border border-[var(--border)] rounded hover:bg-[var(--border)] transition-colors"
      >
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
          <path d="M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85-.29-.15-.65-.06-.83.22l-1.88 3.24c-2.86-1.21-6.08-1.21-8.94 0L5.65 5.67c-.19-.28-.54-.37-.83-.22-.3.16-.42.54-.26.85l1.84 3.18C2.92 10.82 1 13.97 1 17.6h22c0-3.63-1.92-6.78-5.4-8.12M7.43 15.12c-.54 0-.98-.44-.98-.98s.44-.98.98-.98c.55 0 .99.44.99.98s-.44.98-.99.98m9.14 0c-.54 0-.98-.44-.98-.98s.44-.98.98-.98c.55 0 .99.44.99.98s-.44.98-.99.98"/>
        </svg>
        <span>Android (Beta - Join the Google Group)</span>
      </a>

      <a
        href="https://fastbreak.joebad.com"
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-3 p-3 border border-[var(--border)] rounded hover:bg-[var(--border)] transition-colors"
      >
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
        </svg>
        <span>Web - https://fastbreak.joebad.com</span>
      </a>
    </div>
  );
}
