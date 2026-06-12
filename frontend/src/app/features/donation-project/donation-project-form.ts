import {
  ChangeDetectionStrategy, Component, computed, inject, signal, OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDialog } from '@angular/material/dialog';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Dirtyable } from '../../core/guards';
import { CrudRow, LookupItem } from '../../core/models';
import { LookupCacheService } from '../../core/lookup-cache.service';
import { normalizeFlag, snapshot, isDirty } from '../../shared/form-utils';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';
import { ENTITY_CONFIGS } from '../manage/entity-configs';

interface AmountRow { _k: number; AMOUNT: string; }
interface DetailRow {
  _k: number;
  REC_ID?: unknown;
  LABEL_AR: string; LABEL_EN: string;
  DESC_AR: string;  DESC_EN: string;
}

@Component({
  selector: 'app-donation-project-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule,
            MatProgressBarModule, MatTabsModule],
  templateUrl: './donation-project-form.html',
  styleUrl:    './donation-project-form.scss',
})
export class DonationProjectFormComponent implements OnInit, Dirtyable {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly lookupCache = inject(LookupCacheService);

  private readonly cfg = ENTITY_CONFIGS['donation-project'];

  readonly loading = signal(false);
  readonly saving  = signal(false);
  readonly isNew   = signal(false);
  readonly projectId = signal<string | null>(null);

  readonly dto = signal<CrudRow>({});
  readonly amounts  = signal<AmountRow[]>([]);
  readonly details  = signal<DetailRow[]>([]);
  readonly orgs     = signal<LookupItem[]>([]);
  readonly cats     = signal<LookupItem[]>([]);
  /** lookup name -> 'loading' | 'ready' | 'error' */
  readonly lookupState = signal<Record<string, string>>({});
  /** col -> validation message (set on save attempt). */
  readonly errors = signal<Record<string, string>>({});
  /** Indexes of invalid amount rows (set on save attempt). */
  readonly amountErrors = signal<Set<number>>(new Set());
  readonly amountsState = signal<'loading' | 'ready' | 'error'>('ready');
  readonly detailsState = signal<'loading' | 'ready' | 'error'>('ready');

  /** Per-tab snapshots taken after load/save; anything different means unsaved changes. */
  private basicSnapshot   = '';
  private amountsSnapshot = '';
  private detailsSnapshot = '';

  /** Monotonic key generator so @for tracking survives row removal/reorder. */
  private keySeq = 0;
  private nextKey(): number { return ++this.keySeq; }

  readonly title = computed(() => {
    const id = this.projectId();
    return this.isNew() ? 'New Project' : `Project #${id ?? ''}`;
  });

  readonly String = String;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.isNew.set(id === 'new');
    this.loadLookups();
    if (this.isNew()) {
      this.dto.set(this.normalizeFlags({ LIMITED: 'N', HAS_PRE_DEFINED_AMOUNT: 'N', STATUS: 'A' }));
      this.takeBasicSnapshot();
      this.takeAmountsSnapshot();
      this.takeDetailsSnapshot();
    } else {
      this.projectId.set(id);
      this.loadProject(id!);
    }
  }

  private loadLookups(): void {
    for (const name of ['orgs', 'cats'] as const) {
      this.lookupState.update(m => ({ ...m, [name]: 'loading' }));
      this.lookupCache.crudLookup('donation-project', name).subscribe({
        next: v => {
          (name === 'orgs' ? this.orgs : this.cats).set(v);
          this.lookupState.update(m => ({ ...m, [name]: 'ready' }));
        },
        error: () => this.lookupState.update(m => ({ ...m, [name]: 'error' })),
      });
    }
  }

  private loadProject(id: string): void {
    this.loading.set(true);
    this.api.crudGet('donation-project', id).subscribe({
      next: row => {
        this.dto.set(this.normalizeFlags(row));
        this.loading.set(false);
        this.takeBasicSnapshot();
      },
      error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load project'); },
    });
    this.fetchAmounts(id);
    this.fetchDetails(id);
  }

  private fetchAmounts(id: string): void {
    this.amountsState.set('loading');
    this.api.donationAmounts(id).subscribe({
      next: v => {
        this.amounts.set(v.map(r => ({ _k: this.nextKey(), AMOUNT: String(r['AMOUNT'] ?? '') })));
        this.amountsState.set('ready');
        this.takeAmountsSnapshot();
      },
      error: () => this.amountsState.set('error'),
    });
  }

  private fetchDetails(id: string): void {
    this.detailsState.set('loading');
    this.api.donationDetails(id).subscribe({
      next: v => {
        this.details.set(v.map(r => ({
          _k: this.nextKey(),
          REC_ID: r['REC_ID'],
          LABEL_AR: String(r['LABEL_AR'] ?? ''),
          LABEL_EN: String(r['LABEL_EN'] ?? ''),
          DESC_AR:  String(r['DESC_AR']  ?? ''),
          DESC_EN:  String(r['DESC_EN']  ?? ''),
        })));
        this.detailsState.set('ready');
        this.takeDetailsSnapshot();
      },
      error: () => this.detailsState.set('error'),
    });
  }

  reloadAmounts(): void { this.fetchAmounts(this.projectId()!); }
  reloadDetails(): void { this.fetchDetails(this.projectId()!); }

  /**
   * Make flag columns canonical: legacy/loose truthy values ('t', 'Y', '1'…)
   * become 'Y', anything else (including NULL) 'N' — so an untouched save
   * persists an explicit 'N' instead of NULL.
   */
  private normalizeFlags(row: CrudRow): CrudRow {
    const out = { ...row };
    for (const col of ['LIMITED', 'HAS_PRE_DEFINED_AMOUNT']) {
      out[col] = normalizeFlag(out[col]);
    }
    return out;
  }

  // ── Dirty tracking (per tab) ─────────────────────────────────────────────
  private takeBasicSnapshot(): void   { this.basicSnapshot   = snapshot(this.dto()); }
  private takeAmountsSnapshot(): void { this.amountsSnapshot = snapshot(this.amounts()); }
  private takeDetailsSnapshot(): void { this.detailsSnapshot = snapshot(this.details()); }

  basicDirty(): boolean   { return isDirty(this.basicSnapshot,   this.dto()); }
  amountsDirty(): boolean { return isDirty(this.amountsSnapshot, this.amounts()); }
  detailsDirty(): boolean { return isDirty(this.detailsSnapshot, this.details()); }

  isDirty(): boolean {
    return !this.saving() && (this.basicDirty() || this.amountsDirty() || this.detailsDirty());
  }

  /** Labels of tabs with unsaved edits — drives the aggregate warning banner. */
  dirtyTabs(): string[] {
    const t: string[] = [];
    if (this.basicDirty())   t.push('Basic Info');
    if (this.amountsDirty()) t.push('Amounts');
    if (this.detailsDirty()) t.push('Details');
    return t;
  }

  set(col: string, v: string): void {
    this.dto.update(d => ({ ...d, [col]: v === '' ? null : v }));
    if (this.errors()[col]) {
      this.errors.update(e => { const { [col]: _, ...rest } = e; return rest; });
    }
  }

  val(col: string): string {
    const v = this.dto()[col];
    if (v == null) return '';
    const s = String(v);
    return col === 'START_DATE' || col === 'CREATION_DATE' ? s.slice(0, 10) : s;
  }

  isChecked(col: string): boolean { return this.dto()[col] === 'Y'; }
  setChecked(col: string, checked: boolean): void {
    this.dto.update(d => ({ ...d, [col]: checked ? 'Y' : 'N' }));
  }

  // ── Amounts ─────────────────────────────────────────────────────────────
  addAmount(): void { this.amounts.update(a => [...a, { _k: this.nextKey(), AMOUNT: '' }]); }

  removeAmount(i: number): void {
    const row = this.amounts()[i];
    const doRemove = () => {
      this.amounts.update(a => a.filter((_, idx) => idx !== i));
      this.amountErrors.set(new Set());
    };
    if (row && String(row.AMOUNT ?? '').trim() !== '') {
      this.confirmRemoval('Remove this amount? It will be permanently deleted when you save.', doRemove);
    } else {
      doRemove();
    }
  }

  /** Opens the shared confirm dialog and runs the action only if confirmed. */
  private confirmRemoval(message: string, action: () => void): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message, confirmLabel: 'Remove' },
      width: '480px',
    }).afterClosed().subscribe(confirmed => { if (confirmed) action(); });
  }

  clearAmountError(): void {
    if (this.amountErrors().size) this.amountErrors.set(new Set());
  }

  // ── Details ─────────────────────────────────────────────────────────────
  addDetail(): void {
    this.details.update(d => [...d, {
      _k: this.nextKey(), LABEL_AR: '', LABEL_EN: '', DESC_AR: '', DESC_EN: '',
    }]);
  }

  removeDetail(i: number): void {
    const row = this.details()[i];
    const hasData = !!row && (row.REC_ID != null ||
      [row.LABEL_AR, row.LABEL_EN, row.DESC_AR, row.DESC_EN].some(v => String(v ?? '').trim() !== ''));
    const doRemove = () => this.details.update(d => d.filter((_, idx) => idx !== i));
    if (hasData) {
      this.confirmRemoval('Remove this detail section? It will be permanently deleted when you save.', doRemove);
    } else {
      doRemove();
    }
  }

  // ── Validation ──────────────────────────────────────────────────────────
  private validateBasic(): boolean {
    const errs: Record<string, string> = {};
    const d = this.dto();
    for (const [col, label] of [['NAME_EN', 'Name (EN)'], ['NAME_AR', 'Name (AR)']] as const) {
      const s = String(d[col] ?? '');
      if (!s.trim()) errs[col] = `${label} is required`;
      else if (s.length > 400) errs[col] = 'Maximum 400 characters';
    }
    this.errors.set(errs);
    if (Object.keys(errs).length) {
      this.notify.warn('Please fix the highlighted fields');
      // bring the first invalid field into view
      setTimeout(() => document.querySelector('.field-invalid')
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
      return false;
    }
    return true;
  }

  // ── Save ─────────────────────────────────────────────────────────────────
  saveBasic(): void {
    if (this.saving()) return;
    if (!this.validateBasic()) return;
    this.saving.set(true);
    const wasNew = this.isNew();
    const d = this.dto();
    const call = wasNew
      ? this.api.crudCreate('donation-project', d)
      : this.api.crudUpdate('donation-project', this.projectId()!, d);
    call.subscribe({
      next: (res: any) => {
        this.saving.set(false);
        this.notify.success(wasNew
          ? 'Project created — you can now add Amounts and Details'
          : 'Project saved');
        this.takeBasicSnapshot();
        if (wasNew) {
          const id = String(res?.id ?? '');
          this.projectId.set(id);
          this.isNew.set(false);
          this.router.navigate(['/manage', 'donation-project', id], { replaceUrl: true });
        }
      },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  saveAmounts(): void {
    if (this.saving()) return;
    const bad = new Set<number>();
    this.amounts().forEach((a, i) => {
      if (!String(a.AMOUNT ?? '').trim() || isNaN(Number(a.AMOUNT)) || Number(a.AMOUNT) <= 0) bad.add(i);
    });
    if (bad.size) {
      this.amountErrors.set(bad);
      this.notify.warn(`Row(s) ${[...bad].map(i => i + 1).join(', ')}: amount must be a number greater than zero`);
      return;
    }
    this.amountErrors.set(new Set());
    this.saving.set(true);
    const rows = this.amounts().map(a => ({ AMOUNT: a.AMOUNT }));
    this.api.saveDonationAmounts(this.projectId()!, rows).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Amounts saved'); this.takeAmountsSnapshot(); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  saveDetails(): void {
    if (this.saving()) return;
    this.saving.set(true);
    const rows = this.details().map(({ _k, ...rest }) => rest);
    this.api.saveDonationDetails(this.projectId()!, rows as any).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Details saved'); this.takeDetailsSnapshot(); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  back(): void { this.router.navigate(['/manage', 'donation-project']); }
}
