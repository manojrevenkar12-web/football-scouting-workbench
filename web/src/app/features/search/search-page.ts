import { Component, computed, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { League, METRICS, Metric, POSITIONS, PlayerCard, Position, problemText } from '../../core/api';
import { Empty, ErrorState, Loading } from '../../shared/ui/states';
import { PlayerTable } from '../../shared/ui/player-table';

interface Filters {
  positions: Position[]; minAge: number | null; maxAge: number | null; foot: '' | 'left' | 'right';
  leagueIds: number[]; minMinutes: number | null; sortBy: Metric; limit: number;
}

const START: Filters = { positions: ['CM', 'AM'], minAge: null, maxAge: 23, foot: '', leagueIds: [], minMinutes: 900, sortBy: 'PROGRESSION', limit: 25 };

@Component({
  selector: 'app-search-page',
  imports: [FormsModule, PlayerTable, Loading, ErrorState, Empty],
  template: `
    <header class="page-head">
      <h1>Find players</h1>
      <p>Line-breaking is opponents bypassed per action, adjusted for how hard each opponent was to play through and pulled toward the position average when a player has few actions.</p>
    </header>

    <div class="layout">
      <form class="filters" (ngSubmit)="run()" aria-label="Search filters">
        <fieldset>
          <legend>Position</legend>
          <div class="toggles">
            @for (p of positions; track p.id) {
              <label class="toggle" [class.on]="draft().positions.includes(p.id)">
                <input type="checkbox" [checked]="draft().positions.includes(p.id)" (change)="togglePosition(p.id)" />
                <span>{{ p.label }}</span>
              </label>
            }
          </div>
        </fieldset>

        <fieldset class="pair">
          <legend>Age</legend>
          <label>From <input type="number" min="15" max="40" [ngModel]="draft().minAge" (ngModelChange)="set('minAge', $event)" name="minAge" /></label>
          <label>To <input type="number" min="15" max="40" [ngModel]="draft().maxAge" (ngModelChange)="set('maxAge', $event)" name="maxAge" /></label>
        </fieldset>

        <label class="field">Stronger foot
          <select [ngModel]="draft().foot" (ngModelChange)="set('foot', $event)" name="foot">
            <option value="">Either</option><option value="left">Left</option><option value="right">Right</option>
          </select>
        </label>

        <fieldset>
          <legend>League</legend>
          @if (leagues.hasValue()) {
            <div class="toggles">
              @for (l of leagues.value(); track l.id) {
                <label class="toggle" [class.on]="draft().leagueIds.includes(l.id)">
                  <input type="checkbox" [checked]="draft().leagueIds.includes(l.id)" (change)="toggleLeague(l.id)" />
                  <span>{{ l.name }}</span>
                </label>
              }
            </div>
            <p class="muted">None ticked searches all {{ leagues.value().length }}.</p>
          } @else if (leagues.error()) {
            <p class="muted">Leagues did not load; the search covers all of them.</p>
          } @else {
            <app-loading [rows]="2" label="Loading leagues" />
          }
        </fieldset>

        <label class="field">Minutes played, at least
          <input type="number" min="0" step="90" [ngModel]="draft().minMinutes" (ngModelChange)="set('minMinutes', $event)" name="minMinutes" />
        </label>

        <label class="field">Sort by
          <select [ngModel]="draft().sortBy" (ngModelChange)="set('sortBy', $event)" name="sortBy">
            @for (m of metrics; track m.id) { <option [value]="m.id">{{ m.label }}</option> }
          </select>
        </label>

        <label class="field">Show
          <select [ngModel]="draft().limit" (ngModelChange)="set('limit', +$event)" name="limit">
            <option [ngValue]="10">10 players</option><option [ngValue]="25">25 players</option><option [ngValue]="50">50 players</option><option [ngValue]="100">100 players</option>
          </select>
        </label>

        <div class="actions">
          <button class="btn is-primary" type="submit">Search</button>
          <button class="btn" type="button" (click)="reset()">Reset filters</button>
        </div>
      </form>

      <section class="results" aria-live="polite">
        @if (results.isLoading() && !results.hasValue()) {
          <app-loading [rows]="10" label="Searching" />
        } @else if (results.error()) {
          <app-error [message]="problem()" (retry)="results.reload()" />
        } @else if (results.hasValue() && results.value().length === 0) {
          <app-empty title="No player matches every filter">
            Widen the age range, lower the minutes, or tick more positions or leagues.
          </app-empty>
        } @else if (results.hasValue()) {
          <p class="count muted" [class.stale]="results.isLoading()">{{ results.value().length }} players, best first</p>
          <app-player-table [players]="results.value()" [sortBy]="applied().sortBy" caption="Search results" />
        }
      </section>
    </div>`,
  styles: `
    :host { display: block; }
    .layout { display: grid; grid-template-columns: var(--rail) minmax(0, 1fr); gap: var(--space-5); align-items: start; }
    @media (max-width: 60rem) { .layout { grid-template-columns: 1fr; } }
    .filters { display: grid; gap: var(--space-4); position: sticky; top: var(--space-4); }
    fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: var(--space-2); }
    legend, .field { font-size: var(--text-sm); font-weight: 600; color: var(--ink-2); }
    legend { margin-bottom: var(--space-2); padding: 0; }
    .field { display: grid; gap: var(--space-1); }
    .pair { grid-template-columns: 1fr 1fr; }
    .pair legend { grid-column: 1 / -1; }
    .pair label { display: grid; gap: var(--space-1); font-size: var(--text-sm); color: var(--ink-3); }
    input[type=number], select { inline-size: 100%; padding: var(--space-1) var(--space-2); border: var(--rule); border-radius: var(--radius-s); background: var(--surface); font-weight: 400; }
    .toggles { display: flex; flex-wrap: wrap; gap: var(--space-1); }
    .toggle { position: relative; cursor: pointer; font-size: var(--text-sm); }
    .toggle input { position: absolute; opacity: 0; inset: 0; cursor: pointer; }
    .toggle span { display: inline-block; padding: 2px var(--space-2); border: var(--rule); border-radius: var(--radius-pill); background: var(--surface);
      transition: background var(--dur-quick) var(--ease), color var(--dur-quick) var(--ease), border-color var(--dur-quick) var(--ease); }
    .toggle.on span { background: var(--cobalt); border-color: var(--cobalt); color: var(--on-accent); }
    .toggle input:focus-visible + span { outline: 2px solid var(--focus); outline-offset: 2px; }
    .actions { display: flex; gap: var(--space-2); }
    .count { margin-bottom: var(--space-2); transition: opacity var(--dur-quick); }
    .count.stale { opacity: 0.5; }`,
})
export class SearchPage {
  protected readonly positions = POSITIONS;
  protected readonly metrics = METRICS;
  protected readonly draft = signal<Filters>({ ...START });
  protected readonly applied = signal<Filters>({ ...START });

  protected readonly leagues = httpResource<League[]>(() => '/api/leagues');
  protected readonly results = httpResource<PlayerCard[]>(() => {
    const f = this.applied();
    return {
      url: '/api/search', method: 'POST',
      body: { positions: f.positions, minAge: f.minAge, maxAge: f.maxAge, foot: f.foot || null, leagueIds: f.leagueIds, minMinutes: f.minMinutes, sortBy: f.sortBy, limit: f.limit },
    };
  });
  protected readonly problem = computed(() => problemText(this.results.error()));

  protected set<K extends keyof Filters>(k: K, v: Filters[K]) {
    const empty = v === '' || v === undefined || (typeof v === 'number' && Number.isNaN(v));
    this.draft.update(d => ({ ...d, [k]: empty && k !== 'foot' ? null : v }));
  }
  protected togglePosition(p: Position) {
    this.draft.update(d => ({ ...d, positions: d.positions.includes(p) ? d.positions.filter(x => x !== p) : [...d.positions, p] }));
  }
  protected toggleLeague(id: number) {
    this.draft.update(d => ({ ...d, leagueIds: d.leagueIds.includes(id) ? d.leagueIds.filter(x => x !== id) : [...d.leagueIds, id] }));
  }
  protected run() { this.applied.set({ ...this.draft() }); }
  protected reset() { this.draft.set({ ...START }); this.run(); }
}
