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
import { CrudRow, LookupItem } from '../../core/models';
import { ENTITY_CONFIGS, EntityConfig, FieldDef } from './entity-configs';

const CHECKBOX_VALUES: Record<string, [unknown, unknown]> = {
  checkbox01: [1, 0],
  checkboxTF: ['T', 'F'],
  checkboxYN: ['Y', 'N'],
};

/** Generic create/edit form for any entity registered in ENTITY_CONFIGS. */
@Component({
  selector: 'app-dynamic-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './dynamic-form.html',
  styleUrl:    './dynamic-form.scss',
})
export class DynamicFormComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  readonly cfg = signal<EntityConfig | null>(null);

  readonly loading = signal(false);
  readonly saving  = signal(false);
  readonly isNew   = signal(false);

  readonly dto     = signal<CrudRow>({});
  readonly lookups = signal<Record<string, LookupItem[]>>({});

  readonly title = computed(() => {
    const c = this.cfg();
    if (!c) return '';
    return this.isNew() ? `New ${c.titleSingular}` : `${c.titleSingular} #${this.dto()[c.pk] ?? ''}`;
  });

  /** Non-checkbox fields, in declared order (readonly hidden on create). */
  readonly gridFields = computed(() => {
    const c = this.cfg();
    if (!c) return [];
    return c.fields.filter(f =>
      !CHECKBOX_VALUES[f.type] && (f.type !== 'readonly' || !this.isNew()));
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
      this.dto.set(initial);
    } else {
      this.loading.set(true);
      this.api.crudGet(cfg.name, id!).subscribe({
        next: row => { this.dto.set(row); this.loading.set(false); },
        error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load record'); },
      });
    }
  }

  private loadLookups(cfg: EntityConfig): void {
    const names = [...new Set(cfg.fields.map(f => f.lookup).filter((n): n is string => !!n))];
    for (const name of names) {
      this.api.crudLookup(cfg.name, name).subscribe({
        next: v => this.lookups.update(m => ({ ...m, [name]: v })),
        error: () => {},
      });
    }
  }

  save(): void {
    const cfg = this.cfg()!;
    this.saving.set(true);
    const call = this.isNew()
      ? this.api.crudCreate(cfg.name, this.dto())
      : this.api.crudUpdate(cfg.name, String(this.dto()[cfg.pk]), this.dto());
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notify.success(this.isNew() ? `${cfg.titleSingular} created` : `${cfg.titleSingular} updated`);
        this.router.navigate(['/manage', cfg.name]);
      },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
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
    return s;
  }

  set(f: FieldDef, raw: string): void {
    this.dto.update(d => ({ ...d, [f.col]: raw === '' ? null : raw }));
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

  isSelected(f: FieldDef, o: LookupItem): boolean {
    return String(o.id) === this.value(f);
  }
}
