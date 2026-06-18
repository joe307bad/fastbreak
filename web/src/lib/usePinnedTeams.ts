'use client';

import { useState, useEffect, useCallback } from 'react';
import type { PinnedTeam } from '@/types/pinnedTeams';
import {
  readPinnedTeamsFromStorage,
  syncPinnedTeams,
} from '@/lib/pinnedTeamsStorage';

export function usePinnedTeams() {
  const [mounted, setMounted] = useState(false);
  const [pinnedTeams, setPinnedTeams] = useState<PinnedTeam[]>([]);

  useEffect(() => {
    const teams = readPinnedTeamsFromStorage();
    setPinnedTeams(teams);
    syncPinnedTeams(teams);
    setMounted(true);

    const onStorage = (e: StorageEvent) => {
      if (e.key === 'pinnedTeams') {
        const nextTeams = readPinnedTeamsFromStorage();
        setPinnedTeams(nextTeams);
        syncPinnedTeams(nextTeams);
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const pinTeam = useCallback((sport: string, teamCode: string, teamLabel: string) => {
    setPinnedTeams(prev => {
      if (prev.some(t => t.sport === sport && t.teamCode === teamCode)) return prev;
      const next = [...prev, { sport, teamCode, teamLabel, pinnedAt: new Date().toISOString() }];
      syncPinnedTeams(next);
      return next;
    });
  }, []);

  const unpinTeam = useCallback((sport: string, teamCode: string) => {
    setPinnedTeams(prev => {
      const next = prev.filter(t => !(t.sport === sport && t.teamCode === teamCode));
      syncPinnedTeams(next);
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
