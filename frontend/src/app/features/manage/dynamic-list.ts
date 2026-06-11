import {
  ChangeDetectionStrategy, Component, computed, inject, input, signal, OnDestroy, OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subscription } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { CrudRow } from '../../core/models';
import { ENTITY_CONFIGS, EntityConfig, ListColDef } from './entity-configs';

const SEARCH_DEBOUNCE_MS = 300;

/** Generic list page for any entity registered in ENTITY_CONFIGS. */
@Component({
  selector: 'app-dynamic-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './dynamic-list.html',
  styleUrl:    './dynamic-list.scss',
})
export class DynamicListComponent implements OnInit, OnDestroy {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  /** When embedded (e.g. inside the Donations hub) the host names the entity
   *  directly instead of the :entity route param. */
  readonly entity = input<string>();

  readonly cfg = signal<EntityConfig | null>(null);

  readonly loading   = signal(false);
  readonly loadError = signal(false);
  readonly items     = signal<CrudRow[]>([]);
  readonly total     = signal(0);
  readonly page      = signal(0);
  readonly pageSize  = 50;
  readonly search    = signal('');

  readonly sortCol = signal<string | null>(null);
  readonly sortDir = signal<'ASC' | 'DESC'>('ASC');
  readonly deleting = signal<Set<unknown>>(new Set());

  readonly hasMore = computed(() => this.items().length < this.total());

  private routeSub?: Subscription;
  private searchTimer?: ReturnType<typeof setTimeout>;
  /** Monotonic id so out-of-order/overlapping responses are ignored. */
  private reqSeq = 0;

  ngOnInit(): void {
    const fixed = this.entity();
    if (fixed) {
      const cfg = ENTITY_CONFIGS[fixed];
      if (!cfg) { this.router.navigate(['/']); return; }
      this.cfg.set(cfg);
      this.load(true);
      return;
    }
    this.routeSub = this.route.paramMap.subscribe(params => {
      const cfg = ENTITY_CONFIGS[params.get('entity') ?? ''];
      if (!cfg) { this.router.navigate(['/']); return; }
      this.cfg.set(cfg);
      this.search.set('');
      this.sortCol.set(null);
      this.load(true);
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    clearTimeout(this.searchTimer);
  }

  load(reset = false): void {
    const cfg = this.cfg();
    if (!cfg) return;
    if (reset) { this.page.set(0); this.items.set([]); }
    this.loading.set(true);
    this.loadError.set(false);
    const seq = ++this.reqSeq;
    this.api.crudList(cfg.name, {
      search: this.search() || undefined,
      page: this.page(),
      pageSize: this.pageSize,
      sort: this.sortCol() || undefined,
      dir: this.sortCol() ? this.sortDir() : undefined,
    }).subscribe({
      next: r => {
        if (seq !== this.reqSeq) return; // a newer request superseded this one
        // dedupe by pk: ROWNUM windows can drift when rows are inserted between pages
        this.items.update(arr => {
          const seen = new Set(arr.map(x => x[cfg.pk]));
          return [...arr, ...r.items.filter(x => !seen.has(x[cfg.pk]))];
        });
        this.total.set(r.total);
        this.loading.set(false);
      },
      error: err => {
        if (seq !== this.reqSeq) return;
        this.loading.set(false);
        this.loadError.set(true);
        this.notify.error(err, 'Failed to load data');
      },
    });
  }

  onSearchChange(): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(true), SEARCH_DEBOUNCE_MS);
  }

  onScroll(el: HTMLElement): void {
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 120 && !this.loading() && this.hasMore()) {
      this.page.update(p => p + 1);
      this.load();
    }
  }

  clearSearch(): void {
    clearTimeout(this.searchTimer); // cancel a pending debounce so it can't double-load
    this.search.set('');
    this.load(true);
  }

  toggleSort(col: ListColDef): void {
    if (col.noSort) return;
    if (this.sortCol() === col.col) {
      if (this.sortDir() === 'ASC') this.sortDir.set('DESC');
      else { this.sortCol.set(null); this.sortDir.set('ASC'); } // third click resets
    } else {
      this.sortCol.set(col.col);
      this.sortDir.set('ASC');
    }
    this.load(true);
  }

  sortIcon(col: ListColDef): string {
    if (this.sortCol() !== col.col) return '';
    return this.sortDir() === 'ASC' ? 'arrow_upward' : 'arrow_downward';
  }

  create(): void { this.router.navigate(['/manage', this.cfg()!.name, 'new']); }
  edit(row: CrudRow): void { this.router.navigate(['/manage', this.cfg()!.name, row[this.cfg()!.pk]]); }

  canDelete(cfg: EntityConfig): boolean { return cfg.canDelete ?? false; }

  deleteRow(row: CrudRow): void {
    const cfg = this.cfg()!;
    const id = row[cfg.pk];
    const name = cfg.nameCol ? String(row[cfg.nameCol] ?? '').trim() : '';
    if (!confirm(`Delete ${cfg.titleSingular} #${id}${name ? ` "${name}"` : ''}? This cannot be undone.`)) return;
    this.deleting.update(s => new Set([...s, id]));
    this.api.crudDelete(cfg.name, String(id)).subscribe({
      next: () => {
        this.reqSeq++; // discard any in-flight list response as stale
        this.items.update(arr => arr.filter(r => r[cfg.pk] !== id));
        this.total.update(t => Math.max(t - 1, 0));
        this.deleting.update(s => { const n = new Set(s); n.delete(id); return n; });
        this.notify.success(`${cfg.titleSingular} deleted`);
      },
      error: err => {
        this.deleting.update(s => { const n = new Set(s); n.delete(id); return n; });
        this.notify.error(err, 'Delete failed');
      },
    });
  }

  trackId(row: CrudRow): unknown {
    return row[this.cfg()?.pk ?? 'ID'];
  }

  cellValue(row: CrudRow, col: ListColDef): string {
    const v = row[col.col];
    if (v == null || v === '') return '—';
    const s = String(v);
    if (col.numberFormat && s.trim() !== '' && !isNaN(Number(s))) {
      return Number(s).toLocaleString('en-US');
    }
    return col.truncate ? s.slice(0, col.truncate) : s;
  }

  /** Badge for a badgeMap column; CHAR columns may be space-padded, so trim. */
  badgeFor(row: CrudRow, col: ListColDef): { label: string; kind: 'ok' | 'warn' | 'muted' } | null {
    if (!col.badgeMap) return null;
    const raw = String(row[col.col] ?? '').trim();
    return col.badgeMap[raw] ?? null;
  }
}
