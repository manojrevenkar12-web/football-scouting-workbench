import { HttpErrorResponse } from '@angular/common/http';

// Shapes the Spring Boot service returns (scout.service.ScoutController). Kept in one file so a change shows up in one diff.

export type Position = 'GK' | 'CB' | 'FB' | 'DM' | 'CM' | 'AM' | 'W' | 'ST';
export type Metric = 'PROGRESSION' | 'PROGRESSION_PER90_RAW' | 'XG_PER90' | 'GOALS' | 'DEFENSIVE_PER90' | 'MINUTES';

export interface League { id: number; name: string; country: string; }

export interface PlayerCard {
  playerId: number; name: string; position: Position; age: number; foot: 'left' | 'right';
  teamName: string; leagueName: string; leagueId: number; minutes: number; actions: number; bypassed: number;
  progression: number; progressionPer90Raw: number; xgPer90: number; goals: number; defensivePer90: number;
}

export interface ScoutQuery {
  positions: Position[]; minAge: number | null; maxAge: number | null; foot: 'left' | 'right' | null;
  leagueIds: number[]; minMinutes: number | null; sortBy: Metric | null; limit: number | null;
}

export interface PlayerDetail { player: PlayerCard; percentiles: { progression: number; xgPer90: number; defensivePer90: number }; }
export interface Neighbour { player: PlayerCard; distance: number; }

export interface Moment {
  eventId: number; matchId: number; type: 'PASS' | 'CARRY' | 'SHOT'; second: number;
  x: number; y: number; endX: number; endY: number; bypassed: number; xg: number; goal: boolean;
  opponent: string; kickoff: string; videoUrl: string;
}
export interface MomentClip { moment: Moment; clip: { from: number; to: number; url: string }; }

export interface Constraint { field: string; value: string; quote: string; }
export type AssistantStatus = 'RUN' | 'CLARIFY' | 'NOT_COVERED';
export interface AssistantReply {
  status: AssistantStatus; source: 'claude' | 'rules'; constraints: Constraint[];
  query: ScoutQuery | null; messages: string[]; results: PlayerCard[];
}

export interface Gate { id: string; text: string; pass: boolean; value: number; op: string; limit: number; }
export interface RankingScore { precisionAt20: number; topLeagueShare: number; spearman: number; covered: number; }
export interface StudyResult {
  seeds: number[]; leagues: number; players: number; matches: number; appearances: number; requests: number;
  ranking: Record<string, RankingScore | { topLeagueShare: number; poolSize: number }>;
  assistant: Record<'model_unchecked' | 'assistant', { right: number; wrongShown: number; asked: number }>;
}
export interface PerfResult { events: number; loadSeconds: number; designs: Record<string, { p50Ms: number; p95Ms: number; maxMs: number; plan: string }>; }
export interface LabResults {
  dev: StudyResult; heldout: StudyResult | null; perf: PerfResult | null;
  labels: Record<string, string>; perfLabels: Record<string, string>;
  gates: { dev: Gate[]; heldout: Gate[] };
}

export const POSITIONS: { id: Position; label: string }[] = [
  { id: 'GK', label: 'Goalkeeper' }, { id: 'CB', label: 'Centre-back' }, { id: 'FB', label: 'Full-back' },
  { id: 'DM', label: 'Defensive mid' }, { id: 'CM', label: 'Central mid' }, { id: 'AM', label: 'Attacking mid' },
  { id: 'W', label: 'Winger' }, { id: 'ST', label: 'Striker' },
];

export const METRICS: { id: Metric; label: string; short: string; field: keyof PlayerCard; digits: number }[] = [
  { id: 'PROGRESSION', label: 'Line-breaking, adjusted', short: 'Line-breaking', field: 'progression', digits: 2 },
  { id: 'PROGRESSION_PER90_RAW', label: 'Opponents bypassed per 90, raw', short: 'Bypassed /90', field: 'progressionPer90Raw', digits: 1 },
  { id: 'XG_PER90', label: 'xG per 90', short: 'xG /90', field: 'xgPer90', digits: 2 },
  { id: 'GOALS', label: 'Goals', short: 'Goals', field: 'goals', digits: 0 },
  { id: 'DEFENSIVE_PER90', label: 'Defensive actions per 90', short: 'Defending /90', field: 'defensivePer90', digits: 1 },
  { id: 'MINUTES', label: 'Minutes', short: 'Minutes', field: 'minutes', digits: 0 },
];

export function positionLabel(p: string): string {
  return POSITIONS.find(x => x.id === p)?.label ?? p;
}

/** Turns any failure from the service into one sentence that says what to do next. */
export function problemText(err: unknown): string {
  const e = err instanceof HttpErrorResponse ? err : (err as { cause?: unknown })?.cause;
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'The scouting service is not reachable. Start it on port 8091, then retry.';
    const problems = (e.error as { problems?: string[] } | null)?.problems;
    if (Array.isArray(problems) && problems.length) return problems.map(p => p[0].toUpperCase() + p.slice(1)).join('. ') + '.';
    if (e.status === 404) return 'There is no player with this id. Go back to the search and pick one from the list.';
    return `The service answered ${e.status}. Retry, and check the service log if it repeats.`;
  }
  return 'The page could not load its data. Retry.';
}

export function clock(second: number): string {
  const m = Math.floor(second / 60), s = second % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
