import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { SourceCode } from '../../core/models';
import { MonacoEditorComponent } from '../../shared/monaco-editor';

@Component({
  selector: 'app-source-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatProgressBarModule, MatButtonModule, MatTooltipModule, MonacoEditorComponent],
  templateUrl: './source-view.html',
  styleUrl: './source-view.scss',
})
export class SourceViewComponent {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  readonly type = input.required<string>();
  readonly name = input.required<string>();
  readonly loading = signal(false);
  readonly code = signal('');
  readonly copied = signal(false);

  constructor() {
    effect(() => {
      const name = this.name(); const type = this.type();
      this.loading.set(true); this.code.set('');
      this.api.source(name, type).subscribe({
        next: (res: SourceCode) => { this.code.set(res.source ?? '-- no source available --'); this.loading.set(false); },
        error: err => { this.code.set('-- failed to load source --'); this.loading.set(false); this.notify.error(err, 'Failed to load source'); },
      });
    });
  }

  monacoLang(): string { return 'sql'; }

  async copySource(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.code());
      this.copied.set(true);
      this.notify.success('Source copied to clipboard');
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      this.notify.error(null, 'Copy not supported in this browser');
    }
  }
}
