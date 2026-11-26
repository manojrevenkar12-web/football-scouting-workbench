import { Component, computed, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AssistantReply, METRICS, ScoutQuery, positionLabel, problemText } from '../../core/api';
import { Empty, ErrorState, Loading } from '../../shared/ui/states';
import { PlayerTable } from '../../shared/ui/player-table';

const FIELD_LABELS: Record<string, string> = {
  position: 'Position', minAge: 'Age from', maxAge: 'Age up to', age: 'Age', foot: 'Stronger foot', league: 'League',
  minMinutes: 'Minutes, at least', sortBy: 'Sort by', limit: 'How many',
};

@Component({
  selector: 'app-assistant-page',
  imports: [FormsModule, PlayerTable, Loading, ErrorState, Empty],
  template: `
    <header class="page-head">
      <h1>Ask in your own words</h1>
      <p>The model turns the request into filters and must quote the words each filter came from. The service then checks every quote is in your request,
        works the numbers out again itself, and runs the search only when nothing is unclear or outside the data we hold.</p>
    </header>

    <form class="ask" (ngSubmit)="ask()">
      <label for="req" class="visually-hidden">Scouting request</label>
      <textarea id="req" name="req" rows="3" maxlength="500" [(ngModel)]="text" placeholder="Left-footed centre-backs under 23 in the Eredivisie, best at breaking lines"></textarea>
      <div class="row">
        <div class="examples">
          <span class="muted">Try</span>
          @for (e of examples; track e) {
            <button type="button" class="ex" (click)="text = e; ask()">{{ e }}</button>
          }
        </div>
        <button class="btn is-primary" type="submit" [disabled]="!text.trim()">Ask</button>
      </div>
    </form>

    <section aria-live="polite" class="answer">
      @if (!sent()) {
        <app-empty title="Nothing asked yet">Describe the player you need: position, age, foot, league, minutes, what to rank by and how many.</app-empty>
      } @else if (reply.isLoading()) {
        <app-loading [rows]="5" label="Reading the request" />
      } @else if (reply.error()) {
        <app-error [message]="problem()" (retry)="reply.reload()" />
      } @else if (reply.hasValue()) {
        @let r = reply.value();
        <div class="verdict" animate.enter="enter-rise">
          <span class="chip" [class.is-ok]="r.status === 'RUN'" [class.is-warn]="r.status === 'CLARIFY'" [class.is-stop]="r.status === 'NOT_COVERED'">
            <span aria-hidden="true">{{ icon(r.status) }}</span>{{ statusText(r.status) }}
          </span>
          <span class="muted">Read by {{ r.source === 'claude' ? 'Claude' : 'the offline rules model' }}</span>
        </div>

        @if (r.messages.length) {
          <ul class="messages">
            @for (m of r.messages; track m) { <li>{{ m }}</li> }
          </ul>
        }

        @if (r.constraints.length) {
          <table class="constraints">
            <caption>What the model proposed</caption>
            <thead><tr><th scope="col">Filter</th><th scope="col">Model's value</th><th scope="col">From your words</th></tr></thead>
            <tbody>
              @for (c of r.constraints; track $index) {
                <tr><td>{{ field(c.field) }}</td><td class="num">{{ c.value || '—' }}</td><td><q>{{ c.quote }}</q></td></tr>
              }
            </tbody>
          </table>
        }

        @if (r.query) {
          <p class="built"><span class="muted">The search the service built</span> {{ summary(r.query) }}</p>
        }

        @if (r.status === 'RUN') {
          @if (r.results.length === 0) {
            <app-empty title="The request is clear, but no player matches it">Loosen one filter, for example the age or the minutes, and ask again.</app-empty>
          } @else {
            <app-player-table [players]="r.results" [sortBy]="r.query?.sortBy ?? 'PROGRESSION'" caption="Assistant results" />
          }
        }
      }
    </section>`,
  styles: `
    :host { display: block; }
    .ask { display: grid; gap: var(--space-3); max-inline-size: 56rem; margin-bottom: var(--space-6); }
    textarea { inline-size: 100%; padding: var(--space-3); border: var(--rule); border-radius: var(--radius-m); background: var(--surface); font-size: var(--text-lg); resize: vertical; }
    .row { display: flex; gap: var(--space-4); justify-content: space-between; align-items: start; }
    .examples { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: baseline; }
    .ex { border: 0; background: none; padding: 0; color: var(--cobalt); text-decoration: underline; text-underline-offset: 3px; cursor: pointer; font-size: var(--text-sm); text-align: left; }
    .btn[disabled] { opacity: 0.5; cursor: not-allowed; }
    .answer { display: grid; gap: var(--space-4); }
    .verdict { display: flex; gap: var(--space-3); align-items: center; }
    .verdict .chip { font-size: var(--text-sm); padding: var(--space-1) var(--space-3); }
    .messages { margin: 0; padding-left: var(--space-5); color: var(--ink); max-inline-size: var(--measure); display: grid; gap: var(--space-1); }
    .constraints { border-collapse: collapse; font-size: var(--text-sm); max-inline-size: 48rem; }
    .constraints caption { text-align: left; font-weight: 600; color: var(--ink-2); padding-bottom: var(--space-2); }
    .constraints th { text-align: left; font-weight: 500; color: var(--ink-3); padding: var(--space-1) var(--space-4) var(--space-1) 0; border-bottom: var(--rule); }
    .constraints td { padding: var(--space-2) var(--space-4) var(--space-2) 0; border-bottom: 1px solid var(--line-soft); }
    q { color: var(--ink-2); }
    .built { display: grid; gap: var(--space-1); max-inline-size: var(--measure); }`,
})
export class AssistantPage {
  protected text = '';
  protected readonly examples = [
    'Left-footed centre-backs under 23 in the Eredivisie, best at breaking lines',
    'Top 10 strikers aged 20 to 24 with at least 1200 minutes, by xG',
    'A young winger from Serie A',
    'An experienced full-back',
  ];
  protected readonly sent = signal<{ request: string } | null>(null);
  protected readonly reply = httpResource<AssistantReply>(() => {
    const s = this.sent();
    return s ? { url: '/api/assistant', method: 'POST', body: s } : undefined;
  });
  protected readonly problem = computed(() => problemText(this.reply.error()));

  protected ask() {
    const request = this.text.trim();
    if (request) this.sent.set({ request });
  }
  protected summary(q: ScoutQuery): string {
    const parts: string[] = [];
    parts.push(q.positions.length ? q.positions.map(positionLabel).join(' or ') : 'Any position');
    if (q.minAge != null && q.maxAge != null) parts.push(`aged ${q.minAge} to ${q.maxAge}`);
    else if (q.maxAge != null) parts.push(`aged ${q.maxAge} or younger`);
    else if (q.minAge != null) parts.push(`aged ${q.minAge} or older`);
    if (q.foot) parts.push(`${q.foot}-footed`);
    if (q.leagueIds.length) parts.push(`${q.leagueIds.length} league${q.leagueIds.length > 1 ? 's' : ''}`);
    if (q.minMinutes != null) parts.push(`${q.minMinutes}+ minutes`);
    const m = METRICS.find(x => x.id === q.sortBy);
    parts.push(`top ${q.limit ?? 25} by ${(m?.label ?? 'line-breaking').toLowerCase()}`);
    return parts.join(', ') + '.';
  }
  protected field(f: string) { return FIELD_LABELS[f] ?? f; }
  protected statusText(s: AssistantReply['status']) {
    return s === 'RUN' ? 'Ran the search' : s === 'CLARIFY' ? 'Needs one answer from you' : 'Outside the data we hold';
  }
  protected icon(s: AssistantReply['status']) { return s === 'RUN' ? '✓' : s === 'CLARIFY' ? '?' : '✕'; }
}
