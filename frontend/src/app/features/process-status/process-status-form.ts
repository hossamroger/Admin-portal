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
import { ProcessStatusDto, ProcessStatusMsgDto } from '../../core/models';

function emptyStatus(): ProcessStatusDto {
  return {
    id: null, processCode: null, processName: '',
    statusCode: null, statusOnWeb: null, statusOnIos: null, statusOnAndroid: null,
    iosVersion: null, timeToBeAvailable: null, androidVersion: null,
    msgAr: null, msgEn: null,
  };
}

@Component({
  selector: 'app-process-status-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './process-status-form.html',
  styleUrl:    './process-status-form.scss',
})
export class ProcessStatusFormComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly saving  = signal(false);
  readonly isNew   = signal(false);

  readonly dto   = signal<ProcessStatusDto>(emptyStatus());
  readonly msgs  = signal<ProcessStatusMsgDto[]>([]);

  readonly title = computed(() => this.isNew() ? 'New Process Status' : `Status #${this.dto().id}`);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.isNew.set(id === 'new');
    this.api.lookupProcessStatusMsgs().subscribe({ next: v => this.msgs.set(v), error: () => {} });
    if (!this.isNew()) {
      this.loading.set(true);
      this.api.getProcessStatus(Number(id)).subscribe({
        next: d => { this.dto.set(d); this.loading.set(false); },
        error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load status'); },
      });
    }
  }

  save(): void {
    this.saving.set(true);
    const call = this.isNew()
      ? this.api.createProcessStatus(this.dto())
      : this.api.updateProcessStatus(this.dto().id!, this.dto());
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notify.success(this.isNew() ? 'Status created' : 'Status updated');
        this.router.navigate(['/process-status']);
      },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  set(field: keyof ProcessStatusDto, value: any): void {
    this.dto.update(v => ({ ...v, [field]: value === '' ? null : value }));
  }
  setNum(field: keyof ProcessStatusDto, value: string): void {
    this.dto.update(v => ({ ...v, [field]: value === '' ? null : Number(value) }));
  }

  back(): void { this.router.navigate(['/process-status']); }
}
