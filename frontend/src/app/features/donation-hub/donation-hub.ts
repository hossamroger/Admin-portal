import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

import { DynamicListComponent } from '../manage/dynamic-list';

const TABS = ['projects', 'categories', 'organizations'] as const;

/** Donations hub: Projects is the main feature, with Categories and
 *  Organizations as sub-features in adjacent tabs. The active tab is mirrored
 *  to the ?tab= query param so edit-form back navigation restores it. */
@Component({
  selector: 'app-donation-hub',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTabsModule, DynamicListComponent],
  templateUrl: './donation-hub.html',
  styleUrl: './donation-hub.scss',
})
export class DonationHubComponent {
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  readonly selectedIndex = signal(
    Math.max(0, TABS.indexOf((this.route.snapshot.queryParamMap.get('tab') ?? 'projects') as typeof TABS[number])),
  );

  onTabChange(index: number): void {
    this.selectedIndex.set(index);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: index === 0 ? null : TABS[index] },
      replaceUrl: true,
    });
  }
}
