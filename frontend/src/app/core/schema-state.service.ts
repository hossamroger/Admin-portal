import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Subject, Subscription, interval, switchMap } from 'rxjs';
import { ApiService } from './api.service';
import { SchemaOverview } from './models';

/**
 * Shared schema state: the current overview (name / read-only / object counts)
 * plus a background fingerprint poll that flags when the DB schema changes so
 * the toolbar can light up its Sync button.
 */
@Injectable({ providedIn: 'root' })
export class SchemaStateService implements OnDestroy {
  private readonly api = inject(ApiService);

  readonly overview = signal<SchemaOverview | null>(null);
  readonly changed = signal(false);
  readonly lastSync = signal<Date | null>(null);

  /** Emits whenever a sync completes, so open views can refresh themselves. */
  readonly synced$ = new Subject<void>();

  private lastFingerprint: string | null = null;
  private poll?: Subscription;
  private keepAliveSub?: Subscription;

  /** Load the overview and (re)start the background change poll. */
  refresh(): void {
    this.api.overview().subscribe(ov => {
      this.overview.set(ov);
      this.lastFingerprint = ov.fingerprint?.fingerprint ?? null;
      this.changed.set(false);
      this.lastSync.set(new Date());
      this.startPolling();
    });
  }

  /** User-initiated sync: reload overview and notify subscribers to refresh. */
  sync(): void {
    this.api.overview().subscribe(ov => {
      this.overview.set(ov);
      this.lastFingerprint = ov.fingerprint?.fingerprint ?? this.lastFingerprint;
      this.changed.set(false);
      this.lastSync.set(new Date());
      this.synced$.next();
    });
  }

  private startPolling(): void {
    this.poll?.unsubscribe();
    this.poll = interval(20_000)
      .pipe(switchMap(() => this.api.fingerprint()))
      .subscribe({
        next: fp => {
          if (this.lastFingerprint && fp.fingerprint && fp.fingerprint !== this.lastFingerprint) {
            this.changed.set(true);
          }
        },
        error: () => { /* transient poll errors are ignored */ },
      });

    // Keep the servlet session alive by pinging /api/me every 5 minutes.
    this.keepAliveSub?.unsubscribe();
    this.keepAliveSub = interval(5 * 60_000)
      .pipe(switchMap(() => this.api.me()))
      .subscribe({ error: () => { /* keep-alive errors are ignored */ } });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
    this.keepAliveSub?.unsubscribe();
  }
}
