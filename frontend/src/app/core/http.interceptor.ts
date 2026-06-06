import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Adds the X-Requested-With CSRF-mitigation header the backend requires on
 * state-changing calls, sends cookies, and bounces to /login on a 401.
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const cloned = req.clone({
    setHeaders: { 'X-Requested-With': 'XMLHttpRequest' },
    withCredentials: true,
  });

  return next(cloned).pipe(
    catchError((err: HttpErrorResponse) => {
      const onLogin = router.url.startsWith('/login');
      const checkingMe = req.url.endsWith('/api/me');
      if (err.status === 401 && !onLogin && !checkingMe) {
        auth.clear();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
