import { Component, computed, input, output } from '@angular/core';
import { MomentClip, clock } from '../../core/api';

/**
 * The player's moments drawn where they happened, attacking left to right on a 105 x 68 m pitch.
 * Passes and carries are arrows coloured by how many opponents they took out; shots are rings sized by xG, filled when scored.
 */
@Component({
  selector: 'app-pitch-map',
  template: `
    <figure class="pitch">
      <svg viewBox="-3 -3 111 74" role="img" [attr.aria-label]="'Pitch map of ' + moments().length + ' moments'">
        <defs>
          @for (b of buckets; track b.id) {
            <marker [attr.id]="'head-' + b.id" viewBox="0 0 6 6" refX="5" refY="3" markerWidth="4" markerHeight="4" orient="auto-start-reverse">
              <path d="M0,0 L6,3 L0,6 z" [attr.fill]="b.color" />
            </marker>
          }
        </defs>
        <g class="lines">
          <rect x="0" y="0" width="105" height="68" />
          <line x1="52.5" y1="0" x2="52.5" y2="68" />
          <circle cx="52.5" cy="34" r="9.15" />
          <rect x="0" y="13.84" width="16.5" height="40.32" />
          <rect x="88.5" y="13.84" width="16.5" height="40.32" />
          <rect x="0" y="24.84" width="5.5" height="18.32" />
          <rect x="99.5" y="24.84" width="5.5" height="18.32" />
          <circle cx="11" cy="34" r="0.4" class="spot" /><circle cx="94" cy="34" r="0.4" class="spot" />
        </g>
        @for (c of drawn(); track c.moment.eventId) {
          <a class="mark" [class.dim]="active() !== null && active() !== c.moment.eventId" [attr.href]="c.clip.url" target="_blank" rel="noopener"
             (mouseenter)="hover.emit(c.moment.eventId)" (mouseleave)="hover.emit(null)" (focus)="hover.emit(c.moment.eventId)" (blur)="hover.emit(null)">
            <title>{{ describe(c) }}</title>
            @if (c.moment.type === 'SHOT') {
              <circle class="hit" [attr.cx]="c.moment.x" [attr.cy]="c.moment.y" r="3" />
              <circle class="shot" [class.goal]="c.moment.goal" [attr.cx]="c.moment.x" [attr.cy]="c.moment.y" [attr.r]="1 + c.moment.xg * 4" />
            } @else {
              <line class="hit" [attr.x1]="c.moment.x" [attr.y1]="c.moment.y" [attr.x2]="c.moment.endX" [attr.y2]="c.moment.endY" />
              <line class="arrow" [attr.x1]="c.moment.x" [attr.y1]="c.moment.y" [attr.x2]="c.moment.endX" [attr.y2]="c.moment.endY"
                    [attr.stroke]="bucket(c.moment.bypassed).color" [attr.marker-end]="'url(#head-' + bucket(c.moment.bypassed).id + ')'"
                    [class.carry]="c.moment.type === 'CARRY'" />
            }
          </a>
        }
      </svg>
      <figcaption>
        <span class="key">Opponents taken out</span>
        @for (b of buckets; track b.id) { <span class="swatch"><i [style.background]="b.color"></i>{{ b.label }}</span> }
        <span class="swatch"><i class="ring"></i>Shot, size is xG</span>
        <span class="swatch"><i class="ring filled"></i>Goal</span>
        <span class="swatch"><i class="dash"></i>Carry</span>
      </figcaption>
    </figure>`,
  styles: `
    .pitch { margin: 0; display: grid; }
    svg { display: block; inline-size: 100%; background: var(--pitch); border-radius: var(--radius-m) var(--radius-m) 0 0; }
    .lines rect, .lines line, .lines circle { fill: none; stroke: var(--pitch-line); stroke-width: 0.3; opacity: 0.6; }
    .lines .spot { fill: var(--pitch-line); }
    .arrow { stroke-width: 0.7; stroke-linecap: round; transition: opacity var(--dur-quick) var(--ease); }
    .arrow.carry { stroke-dasharray: 1.6 1; }
    .hit { stroke: transparent; stroke-width: 3; fill: transparent; }
    .shot { fill: none; stroke: var(--shot); stroke-width: 0.5; }
    .shot.goal { fill: var(--shot); fill-opacity: 0.85; }
    .mark { cursor: pointer; outline: none; }
    .mark.dim { opacity: 0.18; }
    .mark:focus-visible .arrow, .mark:focus-visible .shot { stroke-width: 1.2; }
    figcaption { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-3); font-size: var(--text-xs); color: var(--on-pitch); align-items: center;
      background: var(--pitch); padding: var(--space-2) var(--space-3); border-top: 1px solid color-mix(in srgb, var(--pitch-line) 40%, transparent); border-radius: 0 0 var(--radius-m) var(--radius-m); }
    .key { font-weight: 600; }
    .swatch { display: inline-flex; align-items: center; gap: var(--space-1); }
    .swatch i { display: inline-block; inline-size: 14px; block-size: 4px; border-radius: 2px; }
    .swatch i.ring { inline-size: 10px; block-size: 10px; border-radius: 50%; border: 1.5px solid var(--shot); background: transparent; }
    .swatch i.ring.filled { background: var(--shot); }
    .swatch i.dash { background: repeating-linear-gradient(90deg, var(--pitch-3) 0 4px, transparent 4px 6px); }`,
})
export class PitchMap {
  readonly moments = input.required<MomentClip[]>();
  readonly active = input<number | null>(null);
  readonly hover = output<number | null>();

  protected readonly buckets = [
    { id: 'b1', label: '0–1', max: 1, color: 'var(--pitch-1)' },
    { id: 'b2', label: '2–3', max: 3, color: 'var(--pitch-2)' },
    { id: 'b3', label: '4–5', max: 5, color: 'var(--pitch-3)' },
    { id: 'b4', label: '6+', max: Infinity, color: 'var(--pitch-4)' },
  ];
  // Shots last so their rings sit on top of the arrows.
  protected readonly drawn = computed(() => [...this.moments()].sort((a, b) => Number(a.moment.type === 'SHOT') - Number(b.moment.type === 'SHOT')));

  protected bucket(n: number) { return this.buckets.find(b => n <= b.max)!; }
  protected describe(c: MomentClip): string {
    const m = c.moment;
    const what = m.type === 'SHOT' ? `Shot, xG ${m.xg.toFixed(2)}${m.goal ? ', goal' : ''}` : `${m.type === 'PASS' ? 'Pass' : 'Carry'} past ${m.bypassed} opponents`;
    return `${what} · vs ${m.opponent}, ${m.kickoff}, ${clock(m.second)}. Opens the clip.`;
  }
}
