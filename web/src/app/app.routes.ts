import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', title: 'Find players · Scouting workbench', loadComponent: () => import('./features/search/search-page').then(m => m.SearchPage) },
  { path: 'players/:id', title: 'Player · Scouting workbench', loadComponent: () => import('./features/player/player-page').then(m => m.PlayerPage) },
  { path: 'assistant', title: 'Ask · Scouting workbench', loadComponent: () => import('./features/assistant/assistant-page').then(m => m.AssistantPage) },
  { path: 'lab', title: 'Lab results · Scouting workbench', loadComponent: () => import('./features/lab/lab-page').then(m => m.LabPage) },
  { path: '**', redirectTo: '' },
];
