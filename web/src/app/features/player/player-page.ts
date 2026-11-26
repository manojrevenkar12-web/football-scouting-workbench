import { Component, computed, input, signal } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MomentClip, Neighbour, PlayerDetail, clock, positionLabel, problemText } from '../../core/api';
import { Empty, ErrorState, Loading } from '../../shared/ui/states';
import { PitchMap } from './pitch-map';

@Component({
  selector: 'app-player-page',
  imports: [DecimalPipe, PercentPipe, RouterLink, PitchMap, Loading, ErrorState, Empty],
  template: `
    <a class="back" routerLink="/">Back to the search</a>

    @if (detail.isLoading() && !detail.hasValue()) {
      <app-loading [rows]="4" label="Loading player" />
    } @else if (detail.error()) {
      <app-error [message]="text(detail.error())" (retry)="detail.reload()" />
    } @else if (detail.hasValue()) {
      @let d = detail.value();
      <header class="head" animate.enter="enter-rise">
        <div>
          <h1>{{ d.player.name }}</h1>
          <p class="sub">{{ pos(d.player.position) }}, {{ d.player.age }}, {{ d.player.foot }} foot · {{ d.player.teamName }}, {{ d.player.leagueName }}</p>
        </div>
        <dl class="facts">
          <div><dt>Minutes</dt><dd class="num">{{ d.player.minutes | number }}</dd></div>
          <div><dt>Actions</dt><dd class="num">{{ d.player.actions | number }}</dd></div>
          <div><dt>Goals</dt><dd class="num">{{ d.player.goals }}</dd></div>
        </dl>
      </header>

      <div class="grid">
        <section class="moments" aria-labelledby="m-h">
          <h2 id="m-h">Moments to watch</h2>
          <p class="muted">His most line-breaking passes and carries and his shots. Each one links to the match video a few seconds before it happens.</p>
          @if (moments.isLoading() && !moments.hasValue()) {
            <app-loading [rows]="6" label="Loading moments" />
          } @else if (moments.error()) {
            <app-error [message]="text(moments.error())" (retry)="moments.reload()" />
          } @else if (moments.hasValue() && moments.value().length === 0) {
            <app-empty title="No passes, carries or shots on record">This player has minutes but no events in the loaded matches.</app-empty>
          } @else if (moments.hasValue()) {
            <app-pitch-map [moments]="moments.value()" [active]="active()" (hover)="active.set($event)" />
            <ol class="clips">
              @for (c of moments.value(); track c.moment.eventId) {
                <li [class.on]="active() === c.moment.eventId" (mouseenter)="active.set(c.moment.eventId)" (mouseleave)="active.set(null)">
                  <a [href]="c.clip.url" target="_blank" rel="noopener" (focus)="active.set(c.moment.eventId)" (blur)="active.set(null)">
                    <span class="what">{{ label(c) }}</span>
                    <span class="muted">vs {{ c.moment.opponent }} · {{ c.moment.kickoff }} · {{ t(c.clip.from) }}–{{ t(c.clip.to) }}</span>
                  </a>
                </li>
              }
            </ol>
          }
        </section>

        <aside class="side">
          <section aria-labelledby="p-h">
            <h2 id="p-h">Against his position</h2>
            <p class="muted">Share of {{ pos(d.player.position).toLowerCase() }}s with 450+ minutes he rates above.</p>
            <ul class="pct">
              @for (r of rows(); track r.key) {
                <li>
                  <span class="lbl">{{ r.label }} <b class="num">{{ r.value | number: r.digits }}</b></span>
                  <span class="track"><span class="fill" [style.inline-size.%]="r.pct * 100"></span><span class="mid"></span></span>
                  <span class="num pv">{{ r.pct | percent }}</span>
                </li>
              }
            </ul>
          </section>

          <section aria-labelledby="s-h">
            <h2 id="s-h">Plays like</h2>
            <p class="muted">Closest {{ pos(d.player.position).toLowerCase() }}s on line-breaking, xG and defensive work, all leagues.</p>
            @if (similar.isLoading() && !similar.hasValue()) {
              <app-loading [rows]="5" label="Loading similar players" />
            } @else if (similar.error()) {
              <app-error [message]="text(similar.error())" (retry)="similar.reload()" />
            } @else if (similar.hasValue() && similar.value().length === 0) {
              <app-empty title="No comparable players">Fewer than two players in this position have enough minutes.</app-empty>
            } @else if (similar.hasValue()) {
              <ol class="similar">
                @for (n of similar.value(); track n.player.playerId) {
                  <li>
                    <a [routerLink]="['/players', n.player.playerId]">{{ n.player.name }}</a>
                    <span class="muted">{{ n.player.age }} · {{ n.player.teamName }}</span>
                    <span class="num dist" title="Distance in standard deviations">{{ n.distance | number: '1.2-2' }}</span>
                  </li>
                }
              </ol>
            }
          </section>
        </aside>
      </div>
    }`,
  styles: `
    :host { display: block; }
    .back { display: inline-block; margin-bottom: var(--space-4); font-size: var(--text-sm); }
    .head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-4); align-items: end; padding-bottom: var(--space-4); border-bottom: var(--rule); margin-bottom: var(--space-5); }
    .head h1 { font-size: var(--text-3xl); }
    .sub { color: var(--ink-2); margin-top: var(--space-1); }
    .facts { display: flex; gap: var(--space-5); margin: 0; }
    .facts dt { font-size: var(--text-xs); color: var(--ink-3); }
    .facts dd { margin: 0; font-size: var(--text-xl); font-weight: 600; }
    .grid { display: grid; grid-template-columns: minmax(0, 1fr) 20rem; gap: var(--space-6); align-items: start; }
    @media (max-width: 64rem) { .grid { grid-template-columns: 1fr; } }
    h2 { font-size: var(--text-lg); margin-bottom: var(--space-1); }
    section > .muted { margin-bottom: var(--space-3); }
    .clips { list-style: none; padding: 0; margin: var(--space-4) 0 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr)); gap: var(--space-1) var(--space-4); }
    .clips li { border-left: 3px solid transparent; padding-left: var(--space-2); transition: border-color var(--dur-quick) var(--ease); }
    .clips li.on { border-color: var(--cobalt); }
    .clips a { display: grid; text-decoration: none; color: inherit; padding: var(--space-1) 0; }
    .clips .what { font-weight: 600; font-size: var(--text-sm); }
    .side { display: grid; gap: var(--space-6); }
    .pct { list-style: none; padding: 0; margin: 0; display: grid; gap: var(--space-3); }
    .pct li { display: grid; grid-template-columns: 1fr auto; gap: var(--space-1) var(--space-2); align-items: center; }
    .lbl { grid-column: 1 / -1; font-size: var(--text-sm); display: flex; justify-content: space-between; }
    .track { position: relative; block-size: 8px; border-radius: 4px; background: var(--line-soft); overflow: hidden; }
    .fill { position: absolute; inset: 0 auto 0 0; background: var(--cobalt); border-radius: 0 4px 4px 0; transition: inline-size var(--dur-base) var(--ease); }
    .mid { position: absolute; inset: 0 auto 0 50%; inline-size: 2px; background: var(--surface); }
    .pv { font-size: var(--text-sm); min-inline-size: 3ch; text-align: right; }
    .similar { list-style: none; padding: 0; margin: 0; display: grid; }
    .similar li { display: grid; grid-template-columns: 1fr auto; padding: var(--space-2) 0; border-bottom: 1px solid var(--line-soft); }
    .similar a { font-weight: 600; color: var(--ink); text-decoration: none; }
    .similar a:hover { color: var(--cobalt); text-decoration: underline; }
    .similar .muted { grid-column: 1; }
    .dist { grid-column: 2; grid-row: 1 / span 2; align-self: center; color: var(--ink-3); }`,
})
export class PlayerPage {
  readonly id = input.required<string>();
  protected readonly active = signal<number | null>(null);
  protected readonly detail = httpResource<PlayerDetail>(() => `/api/players/${this.id()}`);
  protected readonly moments = httpResource<MomentClip[]>(() => `/api/players/${this.id()}/moments?limit=16`);
  protected readonly similar = httpResource<Neighbour[]>(() => `/api/players/${this.id()}/similar`);
  protected readonly pos = positionLabel;
  protected readonly t = clock;
  protected readonly text = problemText;

  protected readonly rows = computed(() => {
    const d = this.detail.value();
    if (!d) return [];
    return [
      { key: 'p', label: 'Line-breaking, adjusted', value: d.player.progression, digits: '1.2-2', pct: d.percentiles.progression },
      { key: 'x', label: 'xG per 90', value: d.player.xgPer90, digits: '1.2-2', pct: d.percentiles.xgPer90 },
      { key: 'd', label: 'Defensive actions per 90', value: d.player.defensivePer90, digits: '1.1-1', pct: d.percentiles.defensivePer90 },
    ];
  });

  protected label(c: MomentClip): string {
    const m = c.moment;
    if (m.type === 'SHOT') return m.goal ? `Goal, xG ${m.xg.toFixed(2)}` : `Shot, xG ${m.xg.toFixed(2)}`;
    return `${m.type === 'PASS' ? 'Pass' : 'Carry'} past ${m.bypassed} opponent${m.bypassed === 1 ? '' : 's'}`;
  }
}
