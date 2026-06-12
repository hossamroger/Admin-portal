import { inject } from '@angular/core';
import { CanActivateFn, CanDeactivateFn, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { ConfirmDialogComponent } from '../shared/confirm-dialog';

/** Require an authenticated session; resolves it from the server on first load. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) return true;

  return auth.load().pipe(
    map(me => (me?.username ? true : router.createUrlTree(['/login']))),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};

/** Require the ADMIN role (assumes authGuard already ran). */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAdmin() ? true : router.createUrlTree(['/']);
};

/** Components that can hold unsaved edits implement this. */
export interface Dirtyable {
  isDirty(): boolean;
}

/** Confirm before navigating away from a form with unsaved changes. */
export const dirtyGuard: CanDeactivateFn<Dirtyable> = (component) => {
  if (!component?.isDirty?.()) return true;
  return inject(MatDialog).open(ConfirmDialogComponent, {
    data: { message: 'You have unsaved changes. Leave this page and discard them?', confirmLabel: 'Discard' },
    width: '480px',
  }).afterClosed().pipe(map(confirmed => !!confirmed));
};
