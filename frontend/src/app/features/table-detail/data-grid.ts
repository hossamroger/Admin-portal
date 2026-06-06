import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';

const PAGE_SIZE = 50;

/** Paginated, sortable, inline-editable grid for a single table's data. */
@Component({
  selector: 'app-data-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  templateUrl: './data-grid.html',
  styleUrl: './data-grid.scss',
})
export class DataGridComponent {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  readonly table = input.required<string>();

  readonly loading = signal(false);
  readonly error = signal('');
  readonly columns = signal<string[]>([]);
  readonly pk = signal<string[]>([]);
  readonly can = signal({ insert: false, update: false, delete: false });
  readonly total = signal(0);
  readonly page = signal(0);
  readonly sort = signal<string | null>(null);
  readonly dir = signal<'ASC' | 'DESC'>('ASC');

  /** Pristine rows from the server (used for diffing on save). */
  private original: any[][] = [];
  /** Editable working copy bound to the inputs. */
  readonly working = signal<any[][]>([]);
  readonly newRows = signal<Record<string, string>[]>([]);
  readonly deleted = signal<Set<number>>(new Set());
  readonly dirty = signal<Set<number>>(new Set());

  readonly pageSize = PAGE_SIZE;
  readonly canUpdate = computed(() => this.can().update && this.pk().length > 0);
  readonly canDelete = computed(() => this.can().delete && this.pk().length > 0);
  readonly canInsert = computed(() => this.can().insert);
  readonly showActions = computed(() => this.canDelete() || this.canInsert());
  readonly fromRow = computed(() => (this.working().length ? this.page() * PAGE_SIZE + 1 : 0));
  readonly toRow = computed(() => this.page() * PAGE_SIZE + this.working().length);
  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => (this.page() + 1) * PAGE_SIZE < this.total());
  readonly hasChanges = computed(() => this.newRows().length > 0 || this.dirty().size > 0 || this.deleted().size > 0);

  constructor() {
    // Reload from page 0 whenever the bound table changes.
    effect(() => {
      this.table();
      untracked(() => {
        this.sort.set(null);
        this.dir.set('ASC');
        this.page.set(0);
        this.fetch();
      });
    });
  }

  /** Public hook so the parent can refresh after a Sync. */
  reload(): void {
    this.fetch();
  }

  isPk(col: string): boolean {
    return this.pk().includes(col.toUpperCase());
  }

  private fetch(): void {
    const table = this.table();
    this.loading.set(true);
    this.error.set('');
    this.api.browse(table, this.page(), PAGE_SIZE, this.sort(), this.dir()).subscribe({
      next: res => {
        if (res.error) { this.error.set(res.error); this.loading.set(false); return; }
        this.columns.set(res.result.columns ?? []);
        this.pk.set((res.pkColumns ?? []).map(c => String(c).toUpperCase()));
        this.can.set(res.can ?? { insert: false, update: false, delete: false });
        this.total.set(res.total);
        this.original = (res.result.rows ?? []).map(r => r.slice());
        this.working.set(this.original.map(r => r.slice()));
        this.newRows.set([]);
        this.deleted.set(new Set());
        this.dirty.set(new Set());
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.error?.error || 'Failed to load data');
      },
    });
  }

  toggleSort(col: string): void {
    if (this.sort()?.toUpperCase() === col.toUpperCase()) {
      this.dir.set(this.dir() === 'ASC' ? 'DESC' : 'ASC');
    } else {
      this.sort.set(col);
      this.dir.set('ASC');
    }
    this.page.set(0);
    this.fetch();
  }

  prev(): void { if (this.hasPrev()) { this.page.update(p => p - 1); this.fetch(); } }
  next(): void { if (this.hasNext()) { this.page.update(p => p + 1); this.fetch(); } }

  addRow(): void {
    this.newRows.update(rows => [{ ...this.blankRow() }, ...rows]);
  }
  discardNew(index: number): void {
    this.newRows.update(rows => rows.filter((_, i) => i !== index));
  }
  private blankRow(): Record<string, string> {
    const r: Record<string, string> = {};
    this.columns().forEach(c => (r[c] = ''));
    return r;
  }

  onCellChange(ri: number): void {
    this.dirty.update(set => new Set(set).add(ri));
  }

  toggleDelete(ri: number): void {
    this.deleted.update(set => {
      const next = new Set(set);
      next.has(ri) ? next.delete(ri) : next.add(ri);
      return next;
    });
  }

  /** Persist all pending inserts, updates and deletes, then reload. */
  async save(): Promise<void> {
    const table = this.table();
    const cols = this.columns();
    const colIndex = new Map(cols.map((c, i) => [c.toUpperCase(), i]));
    const ops: Promise<unknown>[] = [];

    // INSERTs — only include non-empty values.
    for (const nr of this.newRows()) {
      const values: Record<string, string> = {};
      for (const c of cols) if (nr[c] !== '') values[c] = nr[c];
      if (Object.keys(values).length) ops.push(firstValueFrom(this.api.insertRow(table, values)));
    }

    // UPDATEs — diff working vs. original for dirty, non-deleted rows.
    for (const ri of this.dirty()) {
      if (this.deleted().has(ri)) continue;
      const values: Record<string, any> = {};
      cols.forEach((c, ci) => {
        if (this.isPk(c)) return;
        const orig = this.original[ri][ci];
        const origStr = orig === null || orig === undefined ? '' : String(orig);
        const cur = this.working()[ri][ci];
        const curStr = cur === null || cur === undefined ? '' : String(cur);
        if (curStr !== origStr) values[c] = cur;
      });
      if (Object.keys(values).length) ops.push(firstValueFrom(this.api.updateRow(table, this.keyFor(ri, colIndex), values)));
    }

    // DELETEs
    for (const ri of this.deleted()) {
      ops.push(firstValueFrom(this.api.deleteRow(table, this.keyFor(ri, colIndex))));
    }

    if (ops.length === 0) { this.notify.warn('Nothing to save'); return; }

    const settled = await Promise.allSettled(ops);
    const failed = settled.filter(s => s.status === 'rejected');
    if (failed.length) {
      const first = failed[0] as PromiseRejectedResult;
      const msg = first.reason?.error?.error || 'see console';
      this.notify.warn(`Saved ${ops.length - failed.length}, failed ${failed.length}: ${msg}`);
    } else {
      this.notify.success(`Saved ${ops.length} change(s)`);
    }
    this.fetch();
  }

  private keyFor(ri: number, colIndex: Map<string, number>): Record<string, any> {
    const key: Record<string, any> = {};
    for (const p of this.pk()) key[p] = this.original[ri][colIndex.get(p)!];
    return key;
  }
}
