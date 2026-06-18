import type { PinnedTeam } from '@/types/pinnedTeams';

export const PINNED_TEAMS_STORAGE_KEY = 'pinnedTeams';
export const PINNED_TEAMS_COOKIE_KEY = 'pinnedTeams';
const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export function readPinnedTeamsFromStorage(): PinnedTeam[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(PINNED_TEAMS_STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function writePinnedTeamsToStorage(teams: PinnedTeam[]) {
  if (typeof window === 'undefined') return;
  localStorage.setItem(PINNED_TEAMS_STORAGE_KEY, JSON.stringify(teams));
}

export function writePinnedTeamsCookie(teams: PinnedTeam[]) {
  if (typeof document === 'undefined') return;
  const value = encodeURIComponent(JSON.stringify(teams));
  document.cookie = `${PINNED_TEAMS_COOKIE_KEY}=${value}; path=/; max-age=${COOKIE_MAX_AGE_SECONDS}; SameSite=Lax`;
}

export function syncPinnedTeams(teams: PinnedTeam[]) {
  writePinnedTeamsToStorage(teams);
  writePinnedTeamsCookie(teams);
}

export function parsePinnedTeamsCookie(raw: string | undefined): PinnedTeam[] {
  if (!raw) return [];
  try {
    return JSON.parse(decodeURIComponent(raw));
  } catch {
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }
}

export function getPinnedTeamsFromCookieValue(raw: string | undefined): PinnedTeam[] {
  return parsePinnedTeamsCookie(raw);
}

export function getPinnedTeamCodesForSport(teams: PinnedTeam[], sport: string): string[] {
  return teams
    .filter(team => team.sport.toLowerCase() === sport.toLowerCase())
    .map(team => team.teamCode);
}
