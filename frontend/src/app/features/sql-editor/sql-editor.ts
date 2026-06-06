import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { QueryResult } from '../../core/models';
import { MonacoEditorComponent } from '../../shared/monaco-editor';
import { ResultTableComponent } from '../../shared/result-table';

@Component({
  selector: 'app-sql-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule, MatIconModule, MatProgressBarModule,
    MonacoEditorComponent, ResultTableComponent,
  ],
  templateUrl: './sql-editor.html',
  styleUrl: './sql-editor.scss',
})
export class SqlEditorComponent {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  readonly sql = signal('SELECT * FROM dual;');
  readonly results = signal<QueryResult[]>([]);
  readonly running = signal(false);
  readonly hasRun = signal(false);

  run(): void {
    const sql = this.sql().trim();
    if (!sql || this.running()) return;
    this.running.set(true);
    this.hasRun.set(true);

    this.api.runSql(sql).subscribe({
      next: results => {
        this.results.set(Array.isArray(results) ? results : []);
        this.running.set(false);
      },
      error: err => {
        this.results.set([]);
        this.running.set(false);
        this.notify.error(err, 'Query failed');
      },
    });
  }

  clear(): void {
    this.results.set([]);
    this.hasRun.set(false);
  }
}
