import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { ApiService } from './api.service';
import {
  LookupItem, TargetAudienceLookup, ScreenInfoLookup, ComponentInfoLookup,
} from './models';

/**
 * Caches slow-changing reference lookups for the lifetime of the session so
 * forms fetch them once instead of on every mount.
 *
 * Each accessor returns a `shareReplay(1)` Observable keyed by lookup identity:
 * the first subscription triggers the underlying HTTP call, later subscriptions
 * (re-mounts of a form) replay the cached value without a new request. Data is
 * identical to calling the ApiService method directly — only fetching is cached.
 */
@Injectable({ providedIn: 'root' })
export class LookupCacheService {
  private readonly api = inject(ApiService);

  /** Keyed cache of in-flight/completed lookup streams. */
  private readonly cache = new Map<string, Observable<unknown>>();

  private memo<T>(key: string, factory: () => Observable<T>): Observable<T> {
    let stream = this.cache.get(key) as Observable<T> | undefined;
    if (!stream) {
      stream = factory().pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.cache.set(key, stream);
    }
    return stream;
  }

  /** CRUD lookup (e.g. donation-project orgs/cats), keyed by entity + name. */
  crudLookup(entity: string, name: string): Observable<LookupItem[]> {
    return this.memo(`crud:${entity}:${name}`, () => this.api.crudLookup(entity, name));
  }

  lookupTargetAudience(): Observable<TargetAudienceLookup[]> {
    return this.memo('svc:audience', () => this.api.lookupTargetAudience());
  }

  lookupScreenInfo(): Observable<ScreenInfoLookup[]> {
    return this.memo('svc:screen-info', () => this.api.lookupScreenInfo());
  }

  lookupComponents(): Observable<ComponentInfoLookup[]> {
    return this.memo('svc:components', () => this.api.lookupComponents());
  }

  lookupServiceStatuses(): Observable<string[]> {
    return this.memo('svc:statuses', () => this.api.lookupServiceStatuses());
  }

  lookupServiceTypes(): Observable<string[]> {
    return this.memo('svc:types', () => this.api.lookupServiceTypes());
  }
}
