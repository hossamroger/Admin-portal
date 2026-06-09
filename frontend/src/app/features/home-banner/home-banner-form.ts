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
import { HomeBannerDto } from '../../core/models';

function emptyBanner(): HomeBannerDto {
  return {
    id: null, url: null, platform: null, language: null,
    startDt: null, expiryDt: null, foreColor: null, bgColor: null,
    hasAction: 0, actionType: null, actionCode: null, actionUrl: null,
    bannerOrder: null, isActive: 1,
    createdAt: null, updatedAt: null,
    urlSm: null, isHeadline: 0, extensionType: null, catalogId: null,
    mainTitleColor: null, isDarkMode: 0, minVersion: null,
  };
}

@Component({
  selector: 'app-home-banner-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule],
  templateUrl: './home-banner-form.html',
  styleUrl:    './home-banner-form.scss',
})
export class HomeBannerFormComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly saving  = signal(false);
  readonly isNew   = signal(false);

  readonly dto = signal<HomeBannerDto>(emptyBanner());

  readonly title = computed(() => this.isNew() ? 'New Banner' : `Banner #${this.dto().id}`);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.isNew.set(id === 'new');
    if (this.isNew()) {
      this.api.nextBannerOrder().subscribe({
        next: r => this.dto.update(v => ({ ...v, bannerOrder: r.nextOrder })),
        error: () => {},
      });
    } else {
      this.loading.set(true);
      this.api.getHomeBanner(Number(id)).subscribe({
        next: d => { this.dto.set(d); this.loading.set(false); },
        error: err => { this.loading.set(false); this.notify.error(err, 'Failed to load banner'); },
      });
    }
  }

  save(): void {
    this.saving.set(true);
    const call = this.isNew()
      ? this.api.createHomeBanner(this.dto())
      : this.api.updateHomeBanner(this.dto().id!, this.dto());
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notify.success(this.isNew() ? 'Banner created' : 'Banner updated');
        this.router.navigate(['/home-banner']);
      },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  set(field: keyof HomeBannerDto, value: any): void {
    this.dto.update(v => ({ ...v, [field]: value === '' ? null : value }));
  }

  setFlag(field: keyof HomeBannerDto, checked: boolean): void {
    this.dto.update(v => ({ ...v, [field]: checked ? 1 : 0 }));
  }

  back(): void { this.router.navigate(['/home-banner']); }
}
