/** Formatting for scheduler-o11y timestamps, shared by /diagnostics and the report card info sheet. */

import { useSyncExternalStore } from 'react';

// "Now", ticking every 30s. The server snapshot is null so prerendered HTML
// carries only the UTC time and the relative/local parts render on the client.
const TICK_MS = 30000;
function subscribeToClock(onChange: () => void) {
  const timer = setInterval(onChange, TICK_MS);
  return () => clearInterval(timer);
}
const getClientNow = () => Math.floor(Date.now() / TICK_MS) * TICK_MS;
const getServerNow = () => null;

export function useClockNow(): number | null {
  return useSyncExternalStore(subscribeToClock, getClientNow, getServerNow);
}

export function describeRelative(iso: string, now: number): string {
  const ms = new Date(iso).getTime() - now;
  if (Number.isNaN(ms)) return '';
  if (ms <= 0) return 'due now';
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `in ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `in ${hours}h ${(minutes % 60).toString().padStart(2, '0')}m`;
  const days = Math.floor(hours / 24);
  return `in ${days}d ${hours % 24}h`;
}

/** Like describeRelative but for a time in the past: "3h ago". */
export function describeElapsed(iso: string, now: number): string {
  const ms = now - new Date(iso).getTime();
  if (Number.isNaN(ms)) return '';
  if (ms < 60000) return 'just now';
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export function formatUtc(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toISOString().replace('T', ' ').replace(/:\d\d\.\d+Z$/, 'Z');
}
