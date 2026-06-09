import {
  ChangeDetectionStrategy, Component, computed, inject, signal,
  ElementRef, ViewChild, AfterViewInit, OnDestroy, OnInit, NgZone,
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

/** Generic list page for any entity registered in ENTITY_CONFIGS. */
@Component({
  selector: 'app-dynamic-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './dynamic-list.html',
  styleUrl:    './dynamic-list.scss',
})
export class DynamicListComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('tableWrap') private tableWrap!: ElementRef<HTMLDivElement>;

  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly zone   = inject(NgZone);

  readonly cfg = signal<EntityConfig | null>(null);

  readonly loading  = signal(false);
  readonly items    = signal<CrudRow[]>([]);
  readonly total    = signal(0);
  readonly page     = signal(0);
  readonly pageSize = 50;
  readonly search   = signal('');

  readonly hasMore = computed(() => this.items().length < this.total());

  private routeSub?: Subscription;
  private scrollListener?: () => void;

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const cfg = ENTITY_CONFIGS[params.get('entity') ?? ''];
      if (!cfg) { this.router.navigate(['/']); return; }
      this.cfg.set(cfg);
      this.search.set('');
      this.load(true);
    });
  }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.scrollListener = () => {
        const el = this.tableWrap?.nativeElement;
        if (!el) return;
        if (el.scrollTop + el.clientHeight >= el.scrollHeight - 120 && !this.loading() && this.hasMore()) {
          this.zone.run(() => this.loadMore());
        }
      };
      this.tableWrap?.nativeElement.addEventListener('scroll', this.scrollListener!);
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    if (this.scrollListener) {
      this.tableWrap?.nativeElement.removeEventListener('scroll', this.scrollListener);
    }
  }

  load(reset = false): void {
    const cfg = this.cfg();
    if (!cfg) return;
    if (reset) { this.page.set(0); this.items.set([]); }
    this.loading.set(true);
    this.api.crudList(cfg.name, {
      search: this.search() || undefined,
      page: this.page(),
      pageSize: this.pageSize,
    }).subscribe({
      next: r => {
        this.items.update(arr => [...arr, ...r.items]);
        this.total.set(r.total);
        this.loading.set(false);
      },
      error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load data'); },
    });
  }

  private loadMore(): void {
    if (this.loading() || !this.hasMore()) return;
    this.page.update(p => p + 1);
    this.load();
  }

  clearSearch(): void { this.search.set(''); this.load(true); }

  create(): void { this.router.navigate(['/manage', this.cfg()!.name, 'new']); }
  edit(row: CrudRow): void { this.router.navigate(['/manage', this.cfg()!.name, row[this.cfg()!.pk]]); }

  trackId(row: CrudRow): unknown {
    return row[this.cfg()?.pk ?? 'ID'];
  }

  cellValue(row: CrudRow, col: ListColDef): string {
    const v = row[col.col];
    if (v == null || v === '') return '—';
    const s = String(v);
    return col.truncate ? s.slice(0, col.truncate) : s;
  }
}
