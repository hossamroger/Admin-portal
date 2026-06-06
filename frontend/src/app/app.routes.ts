import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then(m => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'sql' },
      {
        path: 'sql',
        loadComponent: () => import('./features/sql-editor/sql-editor').then(m => m.SqlEditorComponent),
      },
      {
        path: 'table/:name',
        loadComponent: () => import('./features/table-detail/table-detail').then(m => m.TableDetailComponent),
      },
      {
        path: 'source/:type/:name',
        loadComponent: () => import('./features/source-view/source-view').then(m => m.SourceViewComponent),
      },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/user-management/user-management').then(m => m.UserManagementComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
