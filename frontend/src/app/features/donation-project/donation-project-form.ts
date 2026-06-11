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

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Dirtyable } from '../../core/guards';
import { CrudRow, LookupItem } from '../../core/models';
import { ENTITY_CONFIGS } from '../manage/entity-configs';

interface AmountRow { AMOUNT: string; }
interface DetailRow {
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

  private savedSnapshot = '';

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
      this.dto.set({ LIMITED: 'N', HAS_PRE_DEFINED_AMOUNT: 'N' });
      this.takeSnapshot();
    } else {
      this.projectId.set(id);
      this.loadProject(id!);
    }
  }

  private loadLookups(): void {
    this.api.crudLookup('donation-project', 'orgs').subscribe({ next: v => this.orgs.set(v), error: () => {} });
    this.api.crudLookup('donation-project', 'cats').subscribe({ next: v => this.cats.set(v), error: () => {} });
  }

  private loadProject(id: string): void {
    this.loading.set(true);
    this.api.crudGet('donation-project', id).subscribe({
      next: row => {
        this.dto.set(row);
        this.loading.set(false);
        this.takeSnapshot();
      },
      error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load project'); },
    });
    this.api.donationAmounts(id).subscribe({ next: v => this.amounts.set(v.map(r => ({ AMOUNT: String(r['AMOUNT'] ?? '') }))), error: () => {} });
    this.api.donationDetails(id).subscribe({ next: v => this.details.set(v.map(r => ({
      REC_ID: r['REC_ID'],
      LABEL_AR: String(r['LABEL_AR'] ?? ''),
      LABEL_EN: String(r['LABEL_EN'] ?? ''),
      DESC_AR:  String(r['DESC_AR']  ?? ''),
      DESC_EN:  String(r['DESC_EN']  ?? ''),
    }))), error: () => {} });
  }

  private takeSnapshot(): void { this.savedSnapshot = JSON.stringify(this.dto()); }
  isDirty(): boolean { return !this.saving() && JSON.stringify(this.dto()) !== this.savedSnapshot; }

  set(col: string, v: string): void {
    this.dto.update(d => ({ ...d, [col]: v === '' ? null : v }));
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
  addAmount(): void { this.amounts.update(a => [...a, { AMOUNT: '' }]); }
  removeAmount(i: number): void { this.amounts.update(a => a.filter((_, idx) => idx !== i)); }

  // ── Details ─────────────────────────────────────────────────────────────
  addDetail(): void { this.details.update(d => [...d, { LABEL_AR: '', LABEL_EN: '', DESC_AR: '', DESC_EN: '' }]); }
  removeDetail(i: number): void { this.details.update(d => d.filter((_, idx) => idx !== i)); }

  // ── Save ─────────────────────────────────────────────────────────────────
  saveBasic(): void {
    const d = this.dto();
    if (!String(d['NAME_EN'] ?? '').trim()) { this.notify.warn('Name (EN) is required'); return; }
    if (!String(d['NAME_AR'] ?? '').trim()) { this.notify.warn('Name (AR) is required'); return; }
    if (this.saving()) return;
    this.saving.set(true);
    const wasNew = this.isNew();
    const call = wasNew
      ? this.api.crudCreate('donation-project', d)
      : this.api.crudUpdate('donation-project', this.projectId()!, d);
    call.subscribe({
      next: (res: any) => {
        this.saving.set(false);
        this.notify.success(wasNew ? 'Project created' : 'Project saved');
        this.takeSnapshot();
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
    this.saving.set(true);
    const rows = this.amounts().map(a => ({ AMOUNT: a.AMOUNT }));
    this.api.saveDonationAmounts(this.projectId()!, rows).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Amounts saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  saveDetails(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.saveDonationDetails(this.projectId()!, this.details() as any).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Details saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  back(): void { this.router.navigate(['/manage', 'donation-project']); }
}
