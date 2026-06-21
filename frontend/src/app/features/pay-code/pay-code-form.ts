import {
  ChangeDetectionStrategy, Component, computed, inject, signal, OnInit, OnDestroy,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { Subscription } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { PayCodeDto, PayCodeDetailsDto, PayCodePayload, EntityLookup } from '../../core/models';
import { LookupCacheService } from '../../core/lookup-cache.service';
import { snapshot, isDirty, changedSections } from '../../shared/form-utils';
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
export class PayCodeFormComponent implements OnInit, OnDestroy, Dirtyable {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly cache  = inject(LookupCacheService);

  readonly loading  = signal(false);
  readonly saving   = signal(false);
  readonly isNew    = signal(false);

  private paramSub?: Subscription;

  readonly payCode  = signal<PayCodeDto>(emptyPayCode());
  readonly details  = signal<PayCodeDetailsDto>(emptyDetails());

  readonly entities = signal<EntityLookup[]>([]);

  private readonly snap = signal('');

  private readonly dirtySignal = computed(() =>
    isDirty(this.snap(), { payCode: this.payCode(), details: this.details() }));
  readonly dirty = this.dirtySignal;
  isDirty(): boolean { return this.dirtySignal(); }

  dirtyMessage(): string {
    const pc = this.payCode();
    if (this.isNew()) {
      const label = pc.processCode?.trim() ? `"${pc.processCode}"` : 'this new payment code';
      return `${label.charAt(0).toUpperCase() + label.slice(1)} has not been saved yet. Leave and discard it?`;
    }
    const changed = changedSections(this.snap(), {
      payCode: pc,
      details: this.details(),
    });
    const label = pc.processCode?.trim() ? ` to "${pc.processCode}"` : '';
    if (changed.includes('payCode') && changed.includes('details')) {
      return `You have unsaved changes${label} in the Pay Code fields and Pay Code Details. Leave and discard them?`;
    }
    if (changed.includes('payCode')) {
      return `You have unsaved changes${label} in the Pay Code fields. Leave and discard them?`;
    }
    if (changed.includes('details')) {
      return `You have unsaved changes${label} in the Pay Code Details. Leave and discard them?`;
    }
    return `You have unsaved changes${label}. Leave and discard them?`;
  }

  ngOnInit(): void {
    this.cache.lookupPayCodeEntities()
      .subscribe({ next: v => this.entities.set(v), error: () => {} });

    // Subscribe to paramMap so re-use of this component instance (e.g. navigating
    // from /pay-code/new → /pay-code/123 after save) triggers a proper re-init.
    this.paramSub = this.route.paramMap.subscribe(params => {
      const idParam = params.get('id') ?? '';
      this.init(idParam);
    });
  }

  ngOnDestroy(): void {
    this.paramSub?.unsubscribe();
  }

  private init(idParam: string): void {
    const isNew = idParam === 'new';
    this.isNew.set(isNew);

    if (!isNew) {
      this.loading.set(true);
      this.api.getPayCode(idParam).subscribe({
        next: p => {
          this.payCode.set(p.payCode);
          this.details.set(p.details ?? emptyDetails());
          this.snap.set(snapshot({ payCode: p.payCode, details: p.details ?? emptyDetails() }));
          this.loading.set(false);
        },
        error: err => {
          this.loading.set(false);
          this.notify.error(err, 'Failed to load payment code');
        },
      });
    } else {
      this.payCode.set(emptyPayCode());
      this.details.set(emptyDetails());
      this.snap.set(snapshot({ payCode: emptyPayCode(), details: emptyDetails() }));
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
        this.saving.set(false);
        this.notify.success(this.isNew() ? 'Payment code created' : 'Payment code updated');
        if (this.isNew()) {
          const newId = res?.id ?? pc.id;
          if (newId != null) {
            // Sync snap so dirtyGuard passes, then navigate — paramMap subscription
            // will call init(newId) which loads the saved record fresh in edit mode.
            this.snap.set(snapshot(payload));
            this.router.navigate(['/pay-code', newId], { replaceUrl: true });
          }
        } else {
          this.snap.set(snapshot(payload));
        }
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
      width: 'auto',
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
