import { Component, input, output } from '@angular/core';

/** Skeleton rows shaped like the content they stand in for, so the page does not jump when data arrives. */
@Component({
  selector: 'app-loading',
  template: `
    <div class="skeleton" role="status" [attr.aria-label]="label()">
      @for (r of rowList(); track $index) {
        <span class="bar" [style.inline-size.%]="widths[$index % widths.length]"></span>
      }
    </div>`,
  styles: `
    .skeleton { display: grid; gap: var(--space-3); padding: var(--space-4) 0; }
    .bar { display: block; block-size: 0.9rem; border-radius: var(--radius-s); background: var(--line-soft); animation: pulse 1.2s var(--ease) infinite alternate; }
    @keyframes pulse { to { opacity: 0.45; } }
    @media (prefers-reduced-motion: reduce) { .bar { animation: none; } }`,
})
export class Loading {
  readonly rows = input(6);
  readonly label = input('Loading');
  protected readonly widths = [92, 78, 85, 64, 88, 71];
  protected rowList() { return Array.from({ length: this.rows() }); }
}

@Component({
  selector: 'app-error',
  template: `
    <div class="box" role="alert">
      <p class="msg">{{ message() }}</p>
      <button class="btn" type="button" (click)="retry.emit()">Retry</button>
    </div>`,
  styles: `
    .box { display: flex; gap: var(--space-4); align-items: center; justify-content: space-between; padding: var(--space-4);
      border: 1px solid var(--stop); border-left-width: 4px; border-radius: var(--radius-s); background: var(--stop-soft); }
    .msg { color: var(--ink); max-inline-size: var(--measure); }`,
})
export class ErrorState {
  readonly message = input.required<string>();
  readonly retry = output<void>();
}

@Component({
  selector: 'app-empty',
  template: `
    <div class="box">
      <p class="title">{{ title() }}</p>
      <div class="body"><ng-content /></div>
    </div>`,
  styles: `
    .box { padding: var(--space-5) var(--space-4); border: 1px dashed var(--line); border-radius: var(--radius-m); display: grid; gap: var(--space-2); }
    .title { font-family: var(--font-display); font-size: var(--text-lg); font-weight: 600; }
    .body { color: var(--ink-2); max-inline-size: var(--measure); }`,
})
export class Empty {
  readonly title = input.required<string>();
}
