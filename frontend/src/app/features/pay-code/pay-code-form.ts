import {
  ChangeDetectionStrategy, Component, computed, inject, signal, OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { PayCodeDto, PayCodeDetailsDto, PayCodePayload, EntityLookup } from '../../core/models';
import { LookupCacheService } from '../../core/lookup-cache.service';
import { snapshot, isDirty } from '../../shared/form-utils';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';
import { Dirtyable } from '../../core/guards';

function emptyPayCode(): PayCodeDto {
  return {
    entityCode: '',
    entityDepartmentCode: '',
    entityServiceCategoryCode: '',
    entityServiceCode: '',
    processCode: '',
    processIdentifier: null,
  };
}

function emptyDetails(): PayCodeDetailsDto {
  return {
    serviceDescAr: '',
    serviceDescEn: null,
    entityNameAr: null,
    entityNameEn: null,
    entityCode: null,
  };
}

@Component({
  selector: 'app-pay-code-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule,
            MatProgressBarModule, MatDialogModule, MatSelectModule],
  templateUrl: './pay-code-form.html',
  styleUrl:    './pay-code-form.scss',
})
export class PayCodeFormComponent implements OnInit, Dirtyable {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly cache  = inject(LookupCacheService);

  readonly loading  = signal(false);
  readonly saving   = signal(false);
  readonly isNew    = signal(false);

  readonly payCode  = signal<PayCodeDto>(emptyPayCode());
  readonly details  = signal<PayCodeDetailsDto>(emptyDetails());

  readonly entities = signal<EntityLookup[]>([]);

  private readonly snap = signal('');

  private readonly dirtySignal = computed(() =>
    isDirty(this.snap(), { payCode: this.payCode(), details: this.details() }));
  readonly dirty = this.dirtySignal;
  isDirty(): boolean { return this.dirtySignal(); }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id') ?? '';
    this.isNew.set(idParam === 'new');

    this.cache.lookupPayCodeEntities()
      .subscribe({ next: v => this.entities.set(v), error: () => {} });

    if (!this.isNew()) {
      this.loading.set(true);
      this.api.getPayCode(idParam).subscribe({
        next: p => {
          this.payCode.set(p.payCode);
          this.details.set(p.details ?? emptyDetails());
          this.snap.set(snapshot({ payCode: p.payCode, details: p.details }));
          this.loading.set(false);
        },
        error: err => {
          this.loading.set(false);
          this.notify.error(err, 'Failed to load payment code');
        },
      });
    } else {
      this.snap.set(snapshot({ payCode: this.payCode(), details: null }));
    }
  }

  // ── Field updaters ────────────────────────────────────────────────────────

  setPayCode<K extends keyof PayCodeDto>(field: K, value: PayCodeDto[K]): void {
    this.payCode.update(c => ({ ...c, [field]: value }));
  }

  setDetails<K extends keyof PayCodeDetailsDto>(field: K, value: PayCodeDetailsDto[K]): void {
    this.details.update(d => ({ ...d, [field]: value }));
  }

  // ── Save ──────────────────────────────────────────────────────────────────

  save(): void {
    const pc = this.payCode();
    if (!pc.processCode?.trim() || !pc.entityCode?.trim() || !pc.entityServiceCode?.trim()) {
      this.notify.error(null, 'Process Code, Entity Code and Service Code are required');
      return;
    }
    const payload: PayCodePayload = { payCode: pc, details: this.details() };
    this.saving.set(true);

    const req$ = this.isNew()
      ? this.api.createPayCode(payload)
      : this.api.updatePayCode(pc.id!, payload);

    req$.subscribe({
      next: res => {
        this.snap.set(snapshot(payload));
        this.saving.set(false);
        this.notify.success(this.isNew() ? 'Payment code created' : 'Payment code updated');
        if (this.isNew()) {
          const newId = res?.id ?? pc.id;
          if (newId != null) {
            this.payCode.update(c => ({ ...c, id: Number(newId) }));
            this.router.navigate(['/pay-code', newId], { replaceUrl: true });
          }
        }
        this.isNew.set(false);
      },
      error: err => {
        this.saving.set(false);
        this.notify.error(err, 'Save failed');
      },
    });
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  delete(): void {
    const pc = this.payCode();
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete payment code "${pc.processCode}"? This will also delete the associated detail record.` },
      width: '480px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deletePayCode(pc.id!).subscribe({
        next: () => {
          this.notify.success('Payment code deleted');
          this.router.navigate(['/pay-code']);
        },
        error: err => this.notify.error(err, 'Delete failed'),
      });
    });
  }

  back(): void { this.router.navigate(['/pay-code']); }
}
