import { ChangeDetectionStrategy, Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ApiService } from '../../core/api.service';
import { ColumnStat } from '../../core/models';

/** Per-column statistics (nulls, uniques, numeric range, time-window counts). */
@Component({
  selector: 'app-insights',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatProgressBarModule, MatIconModule],
  templateUrl: './insights.html',
  styleUrl: './insights.scss',
})
export class InsightsComponent implements OnDestroy {
  private readonly api = inject(ApiService);

  readonly table = input.required<string>();
  readonly loading = signal(false);
  readonly error = signal('');
  readonly partialError = signal('');
  readonly stats = signal<ColumnStat[]>([]);

  private insightsSub?: Subscription;

  constructor() {
    effect(() => {
      const table = this.table();
      this.insightsSub?.unsubscribe();
      this.loading.set(true);
      this.error.set('');
      this.insightsSub = this.api.insights(table).subscribe({
        next: res => {
          if (res.error) this.error.set(res.error);
          this.partialError.set(res.partialError ?? '');
          this.stats.set(res.columnStats ?? []);
          this.loading.set(false);
        },
        error: err => {
          this.error.set(err?.error?.error || 'Failed to analyze data');
          this.loading.set(false);
        },
      });
    });
  }

  ngOnDestroy(): void { this.insightsSub?.unsubscribe(); }

  round(v: number | undefined): number | undefined {
    return v === undefined || v === null ? v : Math.round(v * 100) / 100;
  }
}
