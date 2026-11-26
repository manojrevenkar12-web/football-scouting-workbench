import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { METRICS, Metric, PlayerCard, positionLabel } from '../../core/api';

/** The result list every page uses. The column the search sorted by is marked, so the order is never a mystery. */
@Component({
  selector: 'app-player-table',
  imports: [DecimalPipe, RouterLink],
  template: `
    <div class="wrap">
      <table>
        <caption class="visually-hidden">{{ caption() }}</caption>
        <thead>
          <tr>
            <th scope="col" class="rank">#</th>
            <th scope="col">Player</th>
            <th scope="col">Club</th>
            <th scope="col" class="n">Age</th>
            @for (m of metrics; track m.id) {
              <th scope="col" class="n" [class.sorted]="m.id === sortBy()" [attr.aria-sort]="m.id === sortBy() ? 'descending' : null" [title]="m.label">{{ m.short }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (p of players(); track p.playerId; let i = $index) {
            <tr animate.enter="enter-fade" [style.animation-delay.ms]="i * 12">
              <td class="rank num">{{ i + 1 }}</td>
              <td>
                <a class="name" [routerLink]="['/players', p.playerId]">{{ p.name }}</a>
                <span class="muted">{{ pos(p.position) }} · {{ p.foot }} foot</span>
              </td>
              <td><span>{{ p.teamName }}</span><span class="muted">{{ p.leagueName }}</span></td>
              <td class="n num">{{ p.age }}</td>
              @for (m of metrics; track m.id) {
                <td class="n num" [class.sorted]="m.id === sortBy()">{{ value(p, m.field) | number: fmt(m.digits) }}</td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>`,
  styles: `
    .wrap { overflow-x: auto; border: var(--rule); border-radius: var(--radius-m); background: var(--surface); }
    table { border-collapse: collapse; inline-size: 100%; font-size: var(--text-sm); }
    th { text-align: left; font-weight: 500; color: var(--ink-3); padding: var(--space-3) var(--space-2); border-bottom: var(--rule); vertical-align: bottom; line-height: 1.2; }
    td { padding: var(--space-2); border-bottom: 1px solid var(--line-soft); vertical-align: top; }
    tr:last-child td { border-bottom: 0; }
    td span { display: block; white-space: nowrap; }
    th { white-space: nowrap; }
    .n { text-align: right; }
    .rank { inline-size: 2.5rem; color: var(--ink-3); }
    th.sorted { color: var(--cobalt); }
    td.sorted { background: var(--cobalt-soft); font-weight: 600; }
    .name { white-space: nowrap; font-weight: 600; color: var(--ink); text-decoration: none; }
    .name:hover { color: var(--cobalt); text-decoration: underline; }`,
})
export class PlayerTable {
  readonly players = input.required<PlayerCard[]>();
  readonly sortBy = input<Metric | null>('PROGRESSION');
  readonly caption = input('Players');
  protected readonly metrics = METRICS;
  protected readonly pos = positionLabel;
  protected value(p: PlayerCard, f: keyof PlayerCard): number { return p[f] as number; }
  protected fmt(d: number) { return `1.${d}-${d}`; }
}
