import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <header class="bar">
      <a class="brand" routerLink="/">
        <svg viewBox="0 0 28 18" aria-hidden="true"><rect x="0.75" y="0.75" width="26.5" height="16.5" rx="1.5" /><line x1="14" y1="0.75" x2="14" y2="17.25" /><circle cx="14" cy="9" r="3" /><path d="M4 13 L11 5 L19 8 L25 3" class="run" /></svg>
        Scouting workbench
      </a>
      <nav aria-label="Main">
        <a routerLink="/" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">Find players</a>
        <a routerLink="/assistant" routerLinkActive="on">Ask</a>
        <a routerLink="/lab" routerLinkActive="on">Lab results</a>
      </nav>
    </header>
    <main id="main"><router-outlet /></main>
    <footer class="foot muted">Simulated leagues and players. No real person or club is rated here.</footer>`,
  styles: `
    :host { display: grid; grid-template-rows: auto 1fr auto; min-block-size: 100vh; }
    .skip { position: absolute; left: -999px; }
    .skip:focus { left: var(--space-4); top: var(--space-2); background: var(--surface); padding: var(--space-2); z-index: 2; }
    .bar { display: flex; align-items: center; justify-content: space-between; gap: var(--space-5); padding: var(--space-3) var(--space-6); background: var(--ink); color: var(--paper); flex-wrap: wrap; }
    @media (prefers-color-scheme: dark) { .bar { background: var(--surface); color: var(--ink); border-bottom: var(--rule); } }
    .brand { display: inline-flex; align-items: center; gap: var(--space-3); font-family: var(--font-display); font-weight: 700; font-size: var(--text-lg); color: inherit; text-decoration: none; letter-spacing: 0.01em; }
    .brand svg { inline-size: 34px; fill: none; stroke: currentColor; stroke-width: 1.2; opacity: 0.9; }
    .brand .run { stroke: var(--pitch-2); stroke-width: 1.6; }
    nav { display: flex; gap: var(--space-1); }
    nav a { color: inherit; text-decoration: none; padding: var(--space-1) var(--space-3); border-radius: var(--radius-s); opacity: 0.75; font-size: var(--text-sm); }
    nav a:hover { opacity: 1; }
    nav a.on { opacity: 1; background: color-mix(in srgb, currentColor 14%, transparent); }
    main { padding: var(--space-6); max-inline-size: var(--content-max); inline-size: 100%; margin-inline: auto; }
    @media (max-width: 40rem) { main, .bar { padding-inline: var(--space-4); } }
    .foot { padding: var(--space-4) var(--space-6); border-top: var(--rule); text-align: center; }`,
})
export class App {}
