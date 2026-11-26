import { Component, computed } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Gate, LabResults, RankingScore, problemText } from '../../core/api';
import { Empty, ErrorState, Loading } from '../../shared/ui/states';

/** The lab's generated results (web/public/data/results.json, written by the lab's report command). Nothing here is typed in by hand. */
@Component({
  selector: 'app-lab-page',
  imports: [DecimalPipe, PercentPipe, Loading, ErrorState, Empty],
  template: `
    <header class="page-head">
      <h1>How we know the ratings work</h1>
      <p>Every number below comes from simulated leagues where each player's true ability is known. The designs see the same matches, so any gap between them is the design.</p>
    </header>

    @if (res.isLoading()) {
      <app-loading [rows]="8" label="Loading lab results" />
    } @else if (res.error()) {
      <app-error [message]="problem()" (retry)="res.reload()" />
    } @else if (res.hasValue()) {
      @let r = res.value();
      <p class="scope muted">
        Dev seeds {{ r.dev.seeds.join(', ') }}{{ r.heldout ? '; held-out seeds ' + r.heldout.seeds.join(', ') + ', run once' : '; held-out seeds not run yet' }}.
        Per seed: {{ r.dev.leagues }} leagues, {{ r.dev.players | number }} players, {{ r.dev.matches | number }} matches.
      </p>

      <section class="block" aria-labelledby="rank-h">
        <h2 id="rank-h">Finding the 20 best young passers</h2>
        <p class="muted">Share of the true top 20 under-24 line-breakers that each ranking puts in its own top 20. The line is the gate, {{ gateLimit(r.gates.dev, 'find_talent') | percent }}.</p>
        <div class="bars" role="table" aria-label="Precision at 20 by design">
          <div class="bars-head" role="row">
            <span role="columnheader">Design</span><span role="columnheader">Dev seeds</span>
            <span role="columnheader" class="n">Held-out</span><span role="columnheader" class="n">Rank order</span>
          </div>
          @for (d of designs(); track d.id) {
            <div class="bar-row" role="row" [class.ours]="d.id === 'platform'">
              <span role="cell" class="lbl">{{ d.label }}</span>
              <span role="cell" class="track" [title]="d.label + ': ' + (d.dev.precisionAt20 * 100).toFixed(0) + '% on dev seeds'">
                <span class="fill" [style.inline-size.%]="d.dev.precisionAt20 * 100"></span>
                <span class="gate" [style.left.%]="gateLimit(r.gates.dev, 'find_talent') * 100"></span>
                <span class="val num">{{ d.dev.precisionAt20 | percent }}</span>
              </span>
              <span role="cell" class="n num">{{ d.held ? (d.held.precisionAt20 | percent) : '—' }}</span>
              <span role="cell" class="n num">{{ d.dev.spearman | number: '1.2-2' }}</span>
            </div>
          }
        </div>
        <p class="note muted">Rank order is the Spearman correlation with true ability over every player the ranking covers.</p>
      </section>

      <section class="block" aria-labelledby="ast-h">
        <h2 id="ast-h">Typed requests, {{ r.dev.requests }} per seed</h2>
        <p class="muted">The same simulated model reads every request. Alone, its filters run as proposed; in the assistant, code checks them first.</p>
        <div class="legend" aria-hidden="true">
          <span><i class="ok"></i>Right players shown</span><span><i class="warn"></i>Asked a question</span><span><i class="stop"></i>Wrong players shown</span>
        </div>
        @for (a of assistant(); track a.id) {
          <div class="stack-row">
            <span class="lbl">{{ a.label }}</span>
            <span class="stack" [attr.aria-label]="a.label + ': ' + pct(a.v.right) + ' right, ' + pct(a.v.asked) + ' asked, ' + pct(a.v.wrongShown) + ' wrong'" role="img">
              @if (a.v.right > 0) { <span class="seg ok" [style.flex-grow]="a.v.right" [title]="'Right: ' + pct(a.v.right)">{{ a.v.right >= 0.08 ? pct(a.v.right) : '' }}</span> }
              @if (a.v.asked > 0) { <span class="seg warn" [style.flex-grow]="a.v.asked" [title]="'Asked: ' + pct(a.v.asked)">{{ a.v.asked >= 0.08 ? pct(a.v.asked) : '' }}</span> }
              @if (a.v.wrongShown > 0) { <span class="seg stop" [style.flex-grow]="a.v.wrongShown" [title]="'Wrong: ' + pct(a.v.wrongShown)">{{ a.v.wrongShown >= 0.08 ? pct(a.v.wrongShown) : '' }}</span> }
            </span>
          </div>
        }
      </section>

      <section class="block" aria-labelledby="perf-h">
        <h2 id="perf-h">Search latency on PostgreSQL</h2>
        @if (r.perf) {
          <p class="muted">p95 of the same search, three ways, over {{ r.perf.events | number }} events. Log scale: each grid line is ten times the last.</p>
          <div class="log">
            @for (p of perf(); track p.id) {
              <div class="log-row">
                <span class="lbl">{{ p.label }}</span>
                <span class="log-track">
                  @for (t of ticks; track t) { <span class="tick" [style.left.%]="logPos(t)"></span> }
                  <span class="fill" [style.inline-size.%]="logPos(p.p95)"></span>
                  <span class="val num" [style.left.%]="logPos(p.p95)">{{ p.p95 | number: '1.0-2' }} ms</span>
                </span>
              </div>
            }
            <div class="log-axis" aria-hidden="true">
              <span></span>
              <span class="axis">@for (t of ticks; track t) { <span [style.left.%]="logPos(t)">{{ t }} ms</span> }</span>
            </div>
          </div>
        } @else {
          <app-empty title="Not measured yet">Run the perf study against PostgreSQL, then the report command, to fill this in.</app-empty>
        }
      </section>

      <section class="block" aria-labelledby="gates-h">
        <h2 id="gates-h">Gates</h2>
        <table class="gates">
          <thead><tr><th scope="col">Check</th><th scope="col" class="n">Dev</th><th scope="col" class="n">Held-out</th></tr></thead>
          <tbody>
            @for (g of r.gates.dev; track g.id; let i = $index) {
              <tr>
                <td>{{ g.text }} <span class="muted">({{ g.op }} {{ g.limit }})</span></td>
                <td class="n"><span class="chip" [class.is-ok]="g.pass" [class.is-stop]="!g.pass">{{ g.pass ? '✓ ' : '✕ ' }}{{ g.value }}</span></td>
                <td class="n">
                  @if (r.gates.heldout[i]; as h) { <span class="chip" [class.is-ok]="h.pass" [class.is-stop]="!h.pass">{{ h.pass ? '✓ ' : '✕ ' }}{{ h.value }}</span> }
                  @else { <span class="muted">not run</span> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    }`,
  styles: `
    :host { display: block; }
    .scope { margin-bottom: var(--space-6); }
    .block { margin-bottom: var(--space-7); max-inline-size: 60rem; }
    h2 { font-size: var(--text-xl); margin-bottom: var(--space-1); }
    .block > .muted { margin-bottom: var(--space-4); max-inline-size: var(--measure); }
    .bars { display: grid; gap: var(--space-2); }
    .bars-head, .bar-row { display: grid; grid-template-columns: 17rem minmax(10rem, 1fr) 5rem 5rem; gap: var(--space-3); align-items: center; }
    @media (max-width: 48rem) { .bars-head, .bar-row { grid-template-columns: 1fr 5rem 5rem; } .bar-row .lbl, .bars-head span:first-child { grid-column: 1 / -1; } }
    .bars-head { font-size: var(--text-xs); color: var(--ink-3); }
    .lbl { font-size: var(--text-sm); }
    .n { text-align: right; }
    .track { position: relative; block-size: 1.25rem; }
    .fill { position: absolute; inset: 0 auto 0 0; background: var(--line); border-radius: 0 4px 4px 0; }
    .ours .fill { background: var(--cobalt); }
    .ours .val { color: var(--on-accent); }
    .ours .lbl { font-weight: 600; }
    .gate { position: absolute; top: -3px; bottom: -3px; inline-size: 2px; background: var(--ink); }
    .val { position: absolute; left: var(--space-2); top: 50%; transform: translateY(-50%); font-size: var(--text-xs); color: var(--ink); font-weight: 600; }
    .note { margin-top: var(--space-3); }
    .legend { display: flex; gap: var(--space-4); font-size: var(--text-xs); color: var(--ink-2); margin-bottom: var(--space-3); flex-wrap: wrap; }
    .legend i { display: inline-block; inline-size: 10px; block-size: 10px; border-radius: 2px; margin-right: var(--space-1); vertical-align: -1px; }
    .ok { background: var(--ok); } .warn { background: var(--warn); } .stop { background: var(--stop); }
    .stack-row { display: grid; grid-template-columns: 17rem 1fr; gap: var(--space-3); align-items: center; margin-bottom: var(--space-2); }
    .stack { display: flex; gap: 2px; block-size: 1.5rem; }
    .seg { display: flex; align-items: center; padding-left: var(--space-2); color: var(--on-accent); font-size: var(--text-xs); font-weight: 600; flex-basis: 0; min-inline-size: 2px; }
    .seg:first-child { border-radius: 4px 0 0 4px; } .seg:last-child { border-radius: 0 4px 4px 0; }
    .log { display: grid; gap: var(--space-3); }
    .log-row, .log-axis { display: grid; grid-template-columns: 17rem 1fr; gap: var(--space-3); align-items: center; }
    .log-track { position: relative; block-size: 1.25rem; }
    .log-track .fill { background: var(--cobalt); opacity: 1; }
    .log-track .val { left: auto; margin-left: var(--space-2); color: var(--ink); transform: translateY(-50%); }
    .tick { position: absolute; top: -4px; bottom: -4px; inline-size: 1px; background: var(--line); }
    .axis { position: relative; block-size: 1rem; font-size: var(--text-xs); color: var(--ink-3); }
    .axis span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
    .gates { border-collapse: collapse; inline-size: 100%; font-size: var(--text-sm); }
    .gates th { text-align: left; font-weight: 500; color: var(--ink-3); padding: var(--space-2) var(--space-3) var(--space-2) 0; border-bottom: var(--rule); }
    .gates td { padding: var(--space-2) var(--space-3) var(--space-2) 0; border-bottom: 1px solid var(--line-soft); }
    .gates .n { text-align: right; }`,
})
export class LabPage {
  protected readonly res = httpResource<LabResults>(() => 'data/results.json');
  protected readonly problem = computed(() => problemText(this.res.error()));
  protected readonly ticks = [0.1, 1, 10, 100, 1000];

  protected readonly designs = computed(() => {
    const r = this.res.value();
    if (!r) return [];
    return Object.keys(r.labels).map(id => ({
      id, label: r.labels[id],
      dev: r.dev.ranking[id] as RankingScore,
      held: (r.heldout?.ranking[id] as RankingScore | undefined) ?? null,
    }));
  });
  protected readonly assistant = computed(() => {
    const r = this.res.value();
    if (!r) return [];
    return [
      { id: 'model_unchecked', label: 'Model alone', v: r.dev.assistant.model_unchecked },
      { id: 'assistant', label: 'Model, checked by code', v: r.dev.assistant.assistant },
    ];
  });
  protected readonly perf = computed(() => {
    const p = this.res.value()?.perf;
    const labels = this.res.value()?.perfLabels ?? {};
    if (!p) return [];
    return ['event_scan', 'indexed_lookup', 'read_model'].filter(k => p.designs[k]).map(k => ({ id: k, label: labels[k] ?? k, p95: p.designs[k].p95Ms }));
  });

  protected gateLimit(gates: Gate[], id: string): number { return gates.find(g => g.id === id)?.limit ?? 0; }
  protected logPos(ms: number): number { return ((Math.log10(Math.max(ms, 0.1)) + 1) / 4) * 100; }
  protected pct(v: number): string { return `${Math.round(v * 100)}%`; }
}
