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
import { Subscription } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { EntityDto } from '../../core/models';
import { snapshot, isDirty } from '../../shared/form-utils';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';
import { Dirtyable } from '../../core/guards';

function emptyEntity(): EntityDto {
  return {
    id: null,
    entityCode: null,
    entityNameEn: null,
    entityNameAr: null,
    entityLogoUrl: null,
    entityStatus: null,
    androidStatus: null,
    iosStatus: null,
    actionType: null,
    actionUrl: null,
  };
}

@Component({
  selector: 'app-entity-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule,
            MatProgressBarModule, MatDialogModule],
  templateUrl: './entity-form.html',
  styleUrl:    './entity-form.scss',
})
export class EntityFormComponent implements OnInit, OnDestroy, Dirtyable {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(false);
  readonly saving  = signal(false);
  readonly isNew   = signal(false);

  private paramSub?: Subscription;

  readonly entity = signal<EntityDto>(emptyEntity());

  private readonly snap = signal('');

  private readonly dirtySignal = computed(() => isDirty(this.snap(), this.entity()));
  readonly dirty = this.dirtySignal;
  isDirty(): boolean { return this.dirtySignal(); }

  dirtyMessage(): string {
    const e = this.entity();
    const label = e.entityNameEn?.trim() || e.entityCode?.trim() || 'this entity';
    if (this.isNew()) {
      return `"${label}" has not been saved yet. Leave and discard it?`;
    }
    return `You have unsaved changes to "${label}". Leave and discard them?`;
  }

  ngOnInit(): void {
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
      this.api.getEntity(idParam).subscribe({
        next: e => {
          this.entity.set(e);
          this.snap.set(snapshot(e));
          this.loading.set(false);
        },
        error: err => {
          this.loading.set(false);
          this.notify.error(err, 'Failed to load entity');
        },
      });
    } else {
      const empty = emptyEntity();
      this.entity.set(empty);
      this.snap.set(snapshot(empty));
    }
  }

  set<K extends keyof EntityDto>(field: K, value: EntityDto[K]): void {
    this.entity.update(e => ({ ...e, [field]: value }));
  }

  save(): void {
    const e = this.entity();
    if (!e.entityCode?.trim()) {
      this.notify.error(null, 'Entity Code is required');
      return;
    }
    this.saving.set(true);

    const req$ = this.isNew()
      ? this.api.createEntity(e)
      : this.api.updateEntity(e.id!, e);

    req$.subscribe({
      next: res => {
        this.saving.set(false);
        this.notify.success(this.isNew() ? 'Entity created' : 'Entity updated');
        if (this.isNew()) {
          const newId = res?.id ?? e.id;
          if (newId != null) {
            this.snap.set(snapshot(e));
            this.router.navigate(['/entity', newId], { replaceUrl: true });
          }
        } else {
          this.snap.set(snapshot(e));
        }
      },
      error: err => {
        this.saving.set(false);
        this.notify.error(err, 'Save failed');
      },
    });
  }

  delete(): void {
    const e = this.entity();
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete entity "${e.entityNameEn || e.entityCode}"?` },
      width: 'auto',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteEntity(e.id!).subscribe({
        next: () => {
          this.notify.success('Entity deleted');
          this.router.navigate(['/entity']);
        },
        error: err => this.notify.error(err, 'Delete failed'),
      });
    });
  }

  back(): void { this.router.navigate(['/entity']); }
}
