import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiService } from './api.service';
import { Me } from './models';

/** Holds the authenticated user as reactive state for the whole app. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly _me = signal<Me | null>(null);

  readonly me = this._me.asReadonly();
  readonly isLoggedIn = computed(() => !!this._me()?.username);
  readonly isAdmin = computed(() => this._me()?.role?.toUpperCase() === 'ADMIN');
  readonly can = computed(() => this._me()?.can);

  /** Fetch the current session user (used by the guard on first load / refresh). */
  load(): Observable<Me> {
    return this.api.me().pipe(tap(me => this._me.set(me?.username ? me : null)));
  }

  login(username: string, password: string): Observable<Me> {
    return this.api.login(username, password).pipe(tap(me => this._me.set(me)));
  }

  logout(): Observable<unknown> {
    return this.api.logout().pipe(tap(() => this._me.set(null)));
  }

  clear(): void {
    this._me.set(null);
  }
}
