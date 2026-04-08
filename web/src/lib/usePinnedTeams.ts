'use client';

import { useState, useEffect, useCallback } from 'react';
import type { PinnedTeam } from '@/types/pinnedTeams';

const STORAGE_KEY = 'pinnedTeams';

function readPinnedTeams(): PinnedTeam[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function writePinnedTeams(teams: PinnedTeam[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(teams));
}

export function usePinnedTeams() {
  const [mounted, setMounted] = useState(false);
  const [pinnedTeams, setPinnedTeams] = useState<PinnedTeam[]>([]);

  useEffect(() => {
    setPinnedTeams(readPinnedTeams());
    setMounted(true);

    const onStorage = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY) {
        setPinnedTeams(readPinnedTeams());
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const pinTeam = useCallback((sport: string, teamCode: string, teamLabel: string) => {
    setPinnedTeams(prev => {
      if (prev.some(t => t.sport === sport && t.teamCode === teamCode)) return prev;
      const next = [...prev, { sport, teamCode, teamLabel, pinnedAt: new Date().toISOString() }];
      writePinnedTeams(next);
      return next;
    });
  }, []);

  const unpinTeam = useCallback((sport: string, teamCode: string) => {
    setPinnedTeams(prev => {
      const next = prev.filter(t => !(t.sport === sport && t.teamCode === teamCode));
      writePinnedTeams(next);
      return next;
    });
  }, []);

  const isPinned = useCallback((sport: string, teamCode: string) => {
    return pinnedTeams.some(t => t.sport === sport && t.teamCode === teamCode);
  }, [pinnedTeams]);

  const getPinnedForSport = useCallback((sport: string) => {
    return pinnedTeams.filter(t => t.sport.toLowerCase() === sport.toLowerCase());
  }, [pinnedTeams]);

  return {
    pinnedTeams: mounted ? pinnedTeams : [],
    mounted,
    pinTeam,
    unpinTeam,
    isPinned,
    getPinnedForSport,
  };
}
