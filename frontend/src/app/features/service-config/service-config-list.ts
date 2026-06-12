import {
  ChangeDetectionStrategy, Component, computed, inject, signal,
  ElementRef, ViewChild, AfterViewInit, OnDestroy, NgZone,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LowerCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { ServiceSummary } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';
import { EmptyStateComponent } from '../../shared/empty-state';

@Component({
  selector: 'app-service-config-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, LowerCasePipe, MatButtonModule, MatIconModule, MatTooltipModule,
            MatSelectModule, MatProgressBarModule, MatDialogModule, EmptyStateComponent],
  templateUrl: './service-config-list.html',
  styleUrl:    './service-config-list.scss',
})
export class ServiceConfigListComponent implements AfterViewInit, OnDestroy {
  @ViewChild('tableWrap') private tableWrap!: ElementRef<HTMLDivElement>;

  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly zone   = inject(NgZone);

  readonly loading     = signal(false);
  readonly items       = signal<ServiceSummary[]>([]);
  readonly total       = signal(0);
  readonly page        = signal(0);
  readonly pageSize    = 50;

  readonly search  = signal('');
  readonly status  = signal('');
  readonly type    = signal('');

  readonly statuses = signal<string[]>([]);
  readonly types    = signal<string[]>([]);

  readonly hasMore = computed(() => this.items().length < this.total());

  private scrollListener?: () => void;
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    this.loadFilters();
    this.load(true);
  }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.scrollListener = () => {
        const el = this.tableWrap?.nativeElement;
        if (!el) return;
        const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 120;
        if (nearBottom && !this.loading() && this.hasMore()) {
          this.zone.run(() => this.loadMore());
        }
      };
      this.tableWrap.nativeElement.addEventListener('scroll', this.scrollListener!);
    });
  }

  ngOnDestroy(): void {
    if (this.scrollListener) {
      this.tableWrap?.nativeElement.removeEventListener('scroll', this.scrollListener);
    }
    clearTimeout(this.searchTimer);
  }

  private loadFilters(): void {
    this.api.lookupServiceStatuses().subscribe({ next: v => this.statuses.set(v), error: () => {} });
    this.api.lookupServiceTypes().subscribe({ next: v => this.types.set(v), error: () => {} });
  }

  load(reset = false): void {
    if (reset) { this.page.set(0); this.items.set([]); }
    this.loading.set(true);
    this.api.listServices({
      search:   this.search()  || undefined,
      status:   this.status()  || undefined,
      type:     this.type()    || undefined,
      page:     this.page(),
      pageSize: this.pageSize,
    }).subscribe({
      next: r => {
        this.items.update(arr => [...arr, ...r.items]);
        this.total.set(r.total);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.notify.error(err, 'Failed to load services');
      },
    });
  }

  private loadMore(): void {
    if (this.loading() || !this.hasMore()) return;
    this.page.update(p => p + 1);
    this.load();
  }

  onSearchChange(): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(true), 300);
  }

  clearSearch(): void { this.search.set(''); this.load(true); }

  create(): void { this.router.navigate(['/service-config', 'new']); }
  edit(code: string): void { this.router.navigate(['/service-config', code]); }

  remove(code: string, labelEn: string | null): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete service "${labelEn || code}"? This will also delete all steps, fees, documents and related data.` },
      width: '480px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteService(code).subscribe({
        next: () => { this.notify.success('Service deleted'); this.load(true); },
        error: err => this.notify.error(err, 'Delete failed'),
      });
    });
  }
}
