import {
  ChangeDetectionStrategy, Component, computed, inject, signal, OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Dirtyable } from '../../core/guards';
import { CrudRow, LookupItem } from '../../core/models';
import { LookupCacheService } from '../../core/lookup-cache.service';
import { normalizeFlag, snapshot, isDirty } from '../../shared/form-utils';
import { ENTITY_CONFIGS, EntityConfig, FieldDef } from './entity-configs';

const CHECKBOX_VALUES: Record<string, [unknown, unknown]> = {
  checkbox01: [1, 0],
  checkboxTF: ['T', 'F'],
  checkboxYN: ['Y', 'N'],
};

interface FormSection {
  name: string;
  fields: FieldDef[];
}

/** Generic create/edit form for any entity registered in ENTITY_CONFIGS. */
@Component({
  selector: 'app-dynamic-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './dynamic-form.html',
  styleUrl:    './dynamic-form.scss',
})
export class DynamicFormComponent implements OnInit, Dirtyable {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly lookupCache = inject(LookupCacheService);

  readonly cfg = signal<EntityConfig | null>(null);

  readonly loading = signal(false);
  readonly saving  = signal(false);
  readonly isNew   = signal(false);

  readonly dto     = signal<CrudRow>({});
  readonly lookups = signal<Record<string, LookupItem[]>>({});
  /** lookup name -> 'loading' | 'ready' | 'error' */
  readonly lookupState = signal<Record<string, string>>({});
  /** col -> validation message (set on save attempt). */
  readonly errors = signal<Record<string, string>>({});

  /** Snapshot taken after load/save; anything different means unsaved changes. */
  private savedSnapshot = snapshot({});

  readonly title = computed(() => {
    const c = this.cfg();
    if (!c) return '';
    return this.isNew() ? `New ${c.titleSingular}` : `${c.titleSingular} #${this.dto()[c.pk] ?? ''}`;
  });

  /** Non-checkbox fields grouped by section, in declared order (readonly hidden on create). */
  readonly sections = computed<FormSection[]>(() => {
    const c = this.cfg();
    if (!c) return [];
    const visible = c.fields.filter(f =>
      !CHECKBOX_VALUES[f.type] && (f.type !== 'readonly' || !this.isNew()));
    const out: FormSection[] = [];
    for (const f of visible) {
      const name = f.section ?? '';
      const last = out[out.length - 1];
      if (last && last.name === name) last.fields.push(f);
      else out.push({ name, fields: [f] });
    }
    return out;
  });

  /** Checkbox fields rendered together as a flag row. */
  readonly flagFields = computed(() =>
    (this.cfg()?.fields ?? []).filter(f => !!CHECKBOX_VALUES[f.type]));

  ngOnInit(): void {
    const cfg = ENTITY_CONFIGS[this.route.snapshot.paramMap.get('entity') ?? ''];
    if (!cfg) { this.router.navigate(['/']); return; }
    this.cfg.set(cfg);

    const id = this.route.snapshot.paramMap.get('id');
    this.isNew.set(id === 'new');

    this.loadLookups(cfg);

    if (this.isNew()) {
      const initial: CrudRow = {};
      for (const f of cfg.fields) {
        if (f.default !== undefined) initial[f.col] = f.default;
      }
      this.dto.set(this.normalizeFlags(cfg, initial));
      this.takeSnapshot();
    } else {
      this.loading.set(true);
      this.api.crudGet(cfg.name, id!).subscribe({
        next: row => {
          this.dto.set(this.normalizeFlags(cfg, row));
          this.loading.set(false);
          this.takeSnapshot();
        },
        error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load record'); },
      });
    }
  }

  /**
   * Make flag columns canonical: legacy/loose truthy values ('t', 'Y', '1'…)
   * become the configured "on" value, anything else (including NULL) the "off"
   * value — so an untouched save persists an explicit off instead of NULL.
   */
  private normalizeFlags(cfg: EntityConfig, row: CrudRow): CrudRow {
    const out = { ...row };
    for (const f of cfg.fields) {
      const pair = CHECKBOX_VALUES[f.type];
      if (!pair) continue;
      out[f.col] = normalizeFlag(out[f.col], pair[0], pair[1]);
    }
    return out;
  }

  private takeSnapshot(): void {
    this.savedSnapshot = snapshot(this.dto());
  }

  isDirty(): boolean {
    return !this.saving() && isDirty(this.savedSnapshot, this.dto());
  }

  private loadLookups(cfg: EntityConfig): void {
    const names = [...new Set(cfg.fields.map(f => f.lookup).filter((n): n is string => !!n))];
    for (const name of names) {
      this.lookupState.update(m => ({ ...m, [name]: 'loading' }));
      this.lookupCache.crudLookup(cfg.name, name).subscribe({
        next: v => {
          this.lookups.update(m => ({ ...m, [name]: v }));
          this.lookupState.update(m => ({ ...m, [name]: 'ready' }));
        },
        error: () => this.lookupState.update(m => ({ ...m, [name]: 'error' })),
      });
    }
  }

  // ── Validation ─────────────────────────────────────────────────────────────

  private validate(cfg: EntityConfig): boolean {
    const errs: Record<string, string> = {};
    const d = this.dto();
    for (const f of cfg.fields) {
      const v = d[f.col];
      const empty = v == null || String(v).trim() === '';
      if (f.required && empty) { errs[f.col] = `${f.label} is required`; continue; }
      if (empty) continue;
      const s = String(v);
      if (f.maxLength && s.length > f.maxLength) {
        errs[f.col] = `Maximum ${f.maxLength} characters`; continue;
      }
      if (f.pattern && !new RegExp(`^(?:${f.pattern})$`).test(s)) {
        errs[f.col] = f.patternMsg ?? 'Invalid format'; continue;
      }
      if (f.notBefore) {
        const other = d[f.notBefore];
        if (other != null && String(other) !== '' && s < String(other)) {
          const otherLabel = cfg.fields.find(x => x.col === f.notBefore)?.label ?? f.notBefore;
          errs[f.col] = `Must not be before ${otherLabel}`;
        }
      }
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

  errorFor(f: FieldDef): string {
    return this.errors()[f.col] ?? '';
  }

  // ── Save ───────────────────────────────────────────────────────────────────

  save(stay = false): void {
    const cfg = this.cfg()!;
    if (this.saving()) return;
    if (!this.validate(cfg)) return;
    this.saving.set(true);
    const wasNew = this.isNew();
    const call = wasNew
      ? this.api.crudCreate(cfg.name, this.dto())
      : this.api.crudUpdate(cfg.name, String(this.dto()[cfg.pk]), this.dto());
    call.subscribe({
      next: (res: any) => {
        this.notify.success(wasNew ? `${cfg.titleSingular} created` : `${cfg.titleSingular} updated`);
        this.takeSnapshot();
        if (!stay) {
          this.router.navigate(['/manage', cfg.name]);
          return;
        }
        this.saving.set(false);
        // continue editing: a create becomes an edit of the new record
        const id = wasNew ? res?.id : this.dto()[cfg.pk];
        if (id != null) {
          this.router.navigate(['/manage', cfg.name, id])
            .then(() => this.reloadAfterStay(cfg, String(id)));
        }
      },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  /** Refresh server-generated values (id, audit cols) after Save & Continue. */
  private reloadAfterStay(cfg: EntityConfig, id: string): void {
    this.isNew.set(false);
    this.api.crudGet(cfg.name, id).subscribe({
      next: row => { this.dto.set(this.normalizeFlags(cfg, row)); this.takeSnapshot(); },
      error: () => {},
    });
  }

  back(): void { this.router.navigate(['/manage', this.cfg()!.name]); }

  // ── Template value helpers ─────────────────────────────────────────────────

  /** Input-friendly string for a field's current value. */
  value(f: FieldDef): string {
    const v = this.dto()[f.col];
    if (v == null) return '';
    const s = String(v);
    if (f.type === 'date')     return s.slice(0, 10);
    if (f.type === 'datetime') return s.slice(0, 16);
    if (f.type === 'readonly') return s.replace('T', ' ');
    return s;
  }

  set(f: FieldDef, raw: string): void {
    this.dto.update(d => ({ ...d, [f.col]: raw === '' ? null : raw }));
    if (this.errors()[f.col]) {
      this.errors.update(e => { const { [f.col]: _, ...rest } = e; return rest; });
    }
  }

  isChecked(f: FieldDef): boolean {
    return this.dto()[f.col] === CHECKBOX_VALUES[f.type][0];
  }

  setChecked(f: FieldDef, checked: boolean): void {
    const [on, off] = CHECKBOX_VALUES[f.type];
    this.dto.update(d => ({ ...d, [f.col]: checked ? on : off }));
  }

  optionsFor(f: FieldDef): LookupItem[] {
    return this.lookups()[f.lookup ?? ''] ?? [];
  }

  lookupStateFor(f: FieldDef): string {
    return this.lookupState()[f.lookup ?? ''] ?? 'loading';
  }

  isSelected(f: FieldDef, o: LookupItem): boolean {
    return String(o.id) === this.value(f);
  }

  /** Static select: include the stored value even when it isn't a known option. */
  selectOptions(f: FieldDef): string[] {
    const opts = f.options ?? [];
    const cur = this.value(f);
    return cur && !opts.includes(cur) ? [cur, ...opts] : opts;
  }
}
