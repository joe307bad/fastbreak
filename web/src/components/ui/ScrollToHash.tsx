'use client';

import { useEffect } from 'react';

export function ScrollToHash() {
  useEffect(() => {
    const hash = window.location.hash;
    if (hash) {
      // Remove the # from the hash
      const id = hash.substring(1);

      // Wait for the page to fully render
      setTimeout(() => {
        const element = document.getElementById(id);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 100);
    }
  }, []);

  return null;
}
