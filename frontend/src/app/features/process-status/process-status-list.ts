import {
  ChangeDetectionStrategy, Component, computed, inject, signal,
  ElementRef, ViewChild, AfterViewInit, OnDestroy, NgZone,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { ProcessStatusDto } from '../../core/models';

@Component({
  selector: 'app-process-status-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './process-status-list.html',
  styleUrl:    './process-status-list.scss',
})
export class ProcessStatusListComponent implements AfterViewInit, OnDestroy {
  @ViewChild('tableWrap') private tableWrap!: ElementRef<HTMLDivElement>;

  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly zone   = inject(NgZone);

  readonly loading  = signal(false);
  readonly items    = signal<ProcessStatusDto[]>([]);
  readonly total    = signal(0);
  readonly page     = signal(0);
  readonly pageSize = 50;
  readonly search   = signal('');

  readonly hasMore = computed(() => this.items().length < this.total());

  private scrollListener?: () => void;

  constructor() { this.load(true); }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.scrollListener = () => {
        const el = this.tableWrap?.nativeElement;
        if (!el) return;
        if (el.scrollTop + el.clientHeight >= el.scrollHeight - 120 && !this.loading() && this.hasMore()) {
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
  }

  load(reset = false): void {
    if (reset) { this.page.set(0); this.items.set([]); }
    this.loading.set(true);
    this.api.listProcessStatuses({ search: this.search() || undefined, page: this.page(), pageSize: this.pageSize }).subscribe({
      next: r => {
        this.items.update(arr => [...arr, ...r.items]);
        this.total.set(r.total);
        this.loading.set(false);
      },
      error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load statuses'); },
    });
  }

  private loadMore(): void {
    if (this.loading() || !this.hasMore()) return;
    this.page.update(p => p + 1);
    this.load();
  }

  clearSearch(): void { this.search.set(''); this.load(true); }
  create(): void { this.router.navigate(['/process-status', 'new']); }
  edit(id: number): void { this.router.navigate(['/process-status', id]); }
}
