import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
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
import { AuthService } from '../../core/auth.service';
import { ServiceSummary } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';

@Component({
  selector: 'app-service-config-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, LowerCasePipe, MatButtonModule, MatIconModule, MatTooltipModule,
            MatSelectModule, MatProgressBarModule, MatDialogModule],
  templateUrl: './service-config-list.html',
  styleUrl:    './service-config-list.scss',
})
export class ServiceConfigListComponent {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  readonly auth           = inject(AuthService);

  readonly loading  = signal(false);
  readonly items    = signal<ServiceSummary[]>([]);
  readonly total    = signal(0);
  readonly page     = signal(0);
  readonly pageSize = 20;

  readonly search   = signal('');
  readonly status   = signal('');
  readonly type     = signal('');

  readonly statuses = signal<string[]>([]);
  readonly types    = signal<string[]>([]);

  readonly fromRow  = computed(() => this.total() === 0 ? 0 : this.page() * this.pageSize + 1);
  readonly toRow    = computed(() => Math.min((this.page() + 1) * this.pageSize, this.total()));
  readonly hasPrev  = computed(() => this.page() > 0);
  readonly hasNext  = computed(() => (this.page() + 1) * this.pageSize < this.total());

  constructor() {
    this.loadFilters();
    this.load();
  }

  private loadFilters(): void {
    this.api.lookupServiceStatuses().subscribe({ next: v => this.statuses.set(v), error: () => {} });
    this.api.lookupServiceTypes().subscribe({ next: v => this.types.set(v), error: () => {} });
  }

  load(resetPage = false): void {
    if (resetPage) this.page.set(0);
    this.loading.set(true);
    this.api.listServices({
      search:   this.search() || undefined,
      status:   this.status() || undefined,
      type:     this.type()   || undefined,
      page:     this.page(),
      pageSize: this.pageSize,
    }).subscribe({
      next: r => {
        this.items.set(r.items);
        this.total.set(r.total);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.notify.error(err, 'Failed to load services');
      },
    });
  }

  clearSearch(): void { this.search.set(''); this.load(true); }
  prev(): void { if (this.hasPrev()) { this.page.update(p => p - 1); this.load(); } }
  next(): void { if (this.hasNext()) { this.page.update(p => p + 1); this.load(); } }

  create(): void { this.router.navigate(['/service-config', 'new']); }
  edit(code: string): void { this.router.navigate(['/service-config', code]); }

  remove(code: string, labelEn: string | null): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete service "${labelEn || code}"? This will also delete all steps, fees, documents and related data.` },
      width: '480px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteService(code).subscribe({
        next: () => { this.notify.success('Service deleted'); this.load(); },
        error: err => this.notify.error(err, 'Delete failed'),
      });
    });
  }
}
