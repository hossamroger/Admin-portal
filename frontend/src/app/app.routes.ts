import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { authGuard, adminGuard, dirtyGuard } from './core/guards';

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
      { path: '', pathMatch: 'full', loadComponent: () => import('./features/welcome/welcome').then(m => m.WelcomeComponent) },
      {
        path: 'table/:name',
        loadComponent: () => import('./features/table-detail/table-detail').then(m => m.TableDetailComponent),
      },
      {
        path: 'source/:type/:name',
        loadComponent: () => import('./features/source-view/source-view').then(m => m.SourceViewComponent),
      },
      {
        path: 'attachments',
        loadComponent: () => import('./features/attachments/attachments').then(m => m.AttachmentsComponent),
      },
      {
        path: 'service-config',
        loadComponent: () => import('./features/service-config/service-config-list').then(m => m.ServiceConfigListComponent),
      },
      {
        path: 'service-config/:code',
        canDeactivate: [dirtyGuard],
        loadComponent: () => import('./features/service-config/service-config-form').then(m => m.ServiceConfigFormComponent),
      },
      {
        path: 'manage/donation-project',
        loadComponent: () => import('./features/donation-hub/donation-hub').then(m => m.DonationHubComponent),
      },
      // Categories and Organizations live inside the Donations hub as tabs
      { path: 'manage/donation-category',     redirectTo: () => inject(Router).parseUrl('/manage/donation-project?tab=categories') },
      { path: 'manage/donation-organization', redirectTo: () => inject(Router).parseUrl('/manage/donation-project?tab=organizations') },
      {
        path: 'manage/:entity',
        loadComponent: () => import('./features/manage/dynamic-list').then(m => m.DynamicListComponent),
      },
      {
        path: 'manage/donation-project/:id',
        canDeactivate: [dirtyGuard],
        loadComponent: () => import('./features/donation-project/donation-project-form')
          .then(m => m.DonationProjectFormComponent),
      },
      {
        path: 'manage/:entity/:id',
        canDeactivate: [dirtyGuard],
        loadComponent: () => import('./features/manage/dynamic-form').then(m => m.DynamicFormComponent),
      },
      // Legacy URLs from before the dynamic CRUD engine
      { path: 'process-status',     redirectTo: 'manage/process-status' },
      { path: 'process-status/:id', redirectTo: 'manage/process-status/:id' },
      { path: 'home-banner',        redirectTo: 'manage/home-banner' },
      { path: 'home-banner/:id',    redirectTo: 'manage/home-banner/:id' },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/user-management/user-management').then(m => m.UserManagementComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
