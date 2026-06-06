import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { SourceCode } from '../../core/models';

/** Shows the DDL / PL-SQL source of a code object. Route params bind to inputs. */
@Component({
  selector: 'app-source-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatProgressBarModule],
  templateUrl: './source-view.html',
  styleUrl: './source-view.scss',
})
export class SourceViewComponent {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  // Bound from the route: /source/:type/:name
  readonly type = input.required<string>();
  readonly name = input.required<string>();

  readonly loading = signal(false);
  readonly source = signal('');
  readonly code = computed(() => this.source() || '-- no source available --');

  constructor() {
    // Reload whenever the route (name/type) changes.
    effect(() => {
      const name = this.name();
      const type = this.type();
      this.loading.set(true);
      this.api.source(name, type).subscribe({
        next: (res: SourceCode) => {
          this.source.set(res.source ?? '');
          this.loading.set(false);
        },
        error: err => {
          this.source.set('');
          this.loading.set(false);
          this.notify.error(err, 'Failed to load source');
        },
      });
    });
  }
}
