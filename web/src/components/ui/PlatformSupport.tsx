'use client';

import { useState } from 'react';

interface PlatformSupportProps {
  ios?: boolean;
  android?: boolean;
  web?: boolean;
  dateReleased?: string;
}

function getRelativeTime(dateString: string): string {
  const releaseDate = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - releaseDate.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffDays < 1) {
    return 'Released today';
  } else if (diffDays === 1) {
    return 'Released 1 day ago';
  } else if (diffDays < 30) {
    return `Released ${diffDays} days ago`;
  } else if (diffDays < 365) {
    const months = Math.floor(diffDays / 30);
    return months === 1 ? 'Released 1 month ago' : `Released ${months} months ago`;
  } else {
    const years = Math.floor(diffDays / 365);
    return years === 1 ? 'Released 1 year ago' : `Released ${years} years ago`;
  }
}

export function PlatformSupport({ ios = false, android = false, web = false, dateReleased }: PlatformSupportProps) {
  const [activePlatform, setActivePlatform] = useState<string | null>(null);

  const platforms = [
    {
      name: 'iOS',
      supported: ios,
      icon: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
          <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
        </svg>
      ),
    },
    {
      name: 'Android',
      supported: android,
      icon: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
          <path d="M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85a.637.637 0 00-.83.22l-1.88 3.24a11.43 11.43 0 00-8.94 0L5.65 5.67a.643.643 0 00-.87-.2c-.28.18-.37.54-.22.83L6.4 9.48C3.3 11.25 1.28 14.44 1 18h22c-.28-3.56-2.3-6.75-5.4-8.52zM7 15.25a1.25 1.25 0 110-2.5 1.25 1.25 0 010 2.5zm10 0a1.25 1.25 0 110-2.5 1.25 1.25 0 010 2.5z"/>
        </svg>
      ),
    },
    {
      name: 'Web',
      supported: web,
      icon: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
        </svg>
      ),
    },
  ];

  const handleToggleTooltip = (platformName: string) => {
    setActivePlatform(activePlatform === platformName ? null : platformName);
  };

  return (
    <div className="overflow-x-auto my-4">
      <div className="flex gap-3 items-center min-w-max">
        {platforms.map((platform) => (
          <div
            key={platform.name}
            className="relative flex-shrink-0"
            onMouseEnter={() => setActivePlatform(platform.name)}
            onMouseLeave={() => setActivePlatform(null)}
          >
            <button
              onClick={() => handleToggleTooltip(platform.name)}
              className={`flex items-center gap-2 px-3 py-2 border rounded transition-all whitespace-nowrap ${
                platform.supported
                  ? 'border-green-600 text-green-600 bg-green-600/10'
                  : 'border-red-600 text-red-600 bg-red-600/10'
              }`}
              aria-label={`${platform.name} - ${platform.supported ? 'Supported' : 'Not supported'}`}
            >
              {platform.icon}
              <span className="text-xs font-medium">{platform.name}</span>
            </button>
            {activePlatform === platform.name && (
              <div className="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 px-2 py-1 bg-[var(--foreground)] text-[var(--background)] text-xs rounded whitespace-nowrap z-10">
                {platform.supported
                  ? `Supported on ${platform.name}`
                  : `Not supported on ${platform.name}`}
                <div className="absolute top-full left-1/2 transform -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-[var(--foreground)]"></div>
              </div>
            )}
          </div>
        ))}
        {dateReleased && (
          <div className="flex items-center gap-2 px-3 py-2 border rounded bg-blue-600/10 border-blue-600 text-blue-600 flex-shrink-0 whitespace-nowrap">
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/>
            </svg>
            <span className="text-xs font-medium">{getRelativeTime(dateReleased)}</span>
          </div>
        )}
      </div>
    </div>
  );
}
