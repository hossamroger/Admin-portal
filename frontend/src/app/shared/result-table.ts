import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Read-only grid for arbitrary column/row result sets (SQL editor output). */
@Component({
  selector: 'app-result-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid-scroll">
      <table class="grid mono">
        <thead>
          <tr>
            @for (c of columns(); track $index) { <th>{{ c }}</th> }
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track $index) {
            <tr>
              @for (v of row; track $index) {
                <td>
                  @if (v === null || v === undefined) {
                    <span class="null">(null)</span>
                  } @else { {{ v }} }
                </td>
              }
            </tr>
          }
          @if (rows().length === 0) {
            <tr><td class="no-rows" [attr.colspan]="columns().length || 1">no rows</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './result-table.scss',
})
export class ResultTableComponent {
  readonly columns = input<string[]>([]);
  readonly rows = input<any[][]>([]);
}
