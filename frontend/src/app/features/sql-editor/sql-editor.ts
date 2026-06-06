import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { QueryResult } from '../../core/models';
import { MonacoEditorComponent } from '../../shared/monaco-editor';
import { ResultTableComponent } from '../../shared/result-table';

@Component({
  selector: 'app-sql-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule, MonacoEditorComponent, ResultTableComponent],
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
  readonly editorHeight = signal(260);

  run(): void {
    const sql = this.sql().trim();
    if (!sql || this.running()) return;
    this.running.set(true);
    this.hasRun.set(true);
    this.api.runSql(sql).subscribe({
      next: results => { this.results.set(Array.isArray(results) ? results : []); this.running.set(false); },
      error: err => { this.results.set([]); this.running.set(false); this.notify.error(err, 'Query failed'); },
    });
  }

  clear(): void { this.results.set([]); this.hasRun.set(false); }

  startResize(e: MouseEvent): void {
    e.preventDefault();
    const startY = e.clientY;
    const startH = this.editorHeight();
    const onMove = (ev: MouseEvent) => this.editorHeight.set(Math.max(100, Math.min(600, startH + ev.clientY - startY)));
    const onUp = () => { window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }

  exportCsv(r: QueryResult): void {
    if (!r.resultSet || !r.columns?.length) return;
    const escape = (v: any) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [r.columns.join(','), ...(r.rows ?? []).map(row => row.map(escape).join(','))];
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'query_result.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
