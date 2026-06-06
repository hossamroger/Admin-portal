import { ChangeDetectionStrategy, Component, effect, inject, signal, viewChild } from '@angular/core';
import { input } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { SchemaStateService } from '../../core/schema-state.service';
import { TableDetail } from '../../core/models';
import { DataGridComponent } from './data-grid';
import { InsightsComponent } from './insights';

/** Container for a table/view: Structure, Data and Insights tabs. */
@Component({
  selector: 'app-table-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTabsModule, MatIconModule, MatProgressBarModule, DataGridComponent, InsightsComponent],
  templateUrl: './table-detail.html',
  styleUrl: './table-detail.scss',
})
export class TableDetailComponent {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly schemaState = inject(SchemaStateService);

  readonly name = input.required<string>();

  readonly loading = signal(false);
  readonly detail = signal<TableDetail | null>(null);

  /** Tabs visited at least once — used to lazy-instantiate Data / Insights. */
  readonly visited = signal<Set<number>>(new Set([0]));

  private readonly grid = viewChild(DataGridComponent);

  constructor() {
    effect(() => {
      this.name();
      this.visited.set(new Set([0]));
      this.loadStructure();
    });

    // Refresh structure + data when the user syncs.
    this.schemaState.synced$.subscribe(() => {
      this.loadStructure();
      this.grid()?.reload();
    });
  }

  onTabChange(index: number): void {
    this.visited.update(set => new Set(set).add(index));
  }

  constraintType(t: string): string {
    return { P: 'PRIMARY KEY', R: 'FOREIGN KEY', U: 'UNIQUE', C: 'CHECK' }[t] ?? t;
  }

  typeWithSize(c: { dataType: string; dataLength?: number; dataPrecision?: number; dataScale?: number }): string {
    const t = c.dataType || '';
    if (/CHAR/.test(t) && c.dataLength) return `${t}(${c.dataLength})`;
    if (/NUMBER/.test(t) && c.dataPrecision) {
      return `${t}(${c.dataPrecision}${c.dataScale ? ',' + c.dataScale : ''})`;
    }
    return t;
  }

  private loadStructure(): void {
    this.loading.set(true);
    this.api.table(this.name()).subscribe({
      next: d => { this.detail.set(d); this.loading.set(false); },
      error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load table'); },
    });
  }
}
