import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  message: string;
  confirmLabel?: string;
}

@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="confirm-wrap">
      <mat-icon class="warn-icon">warning</mat-icon>
      <h3 mat-dialog-title>Confirm</h3>
      <mat-dialog-content>{{ data.message }}</mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="warn" (click)="ref.close(true)">
          {{ data.confirmLabel ?? 'Delete' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .confirm-wrap { padding: 8px 4px 0; }
    .warn-icon { color: #c0392b; font-size: 32px; height: 32px; width: 32px; display: block; margin-bottom: 8px; }
    h3 { margin: 0 0 4px; font-size: 16px; }
    mat-dialog-content { color: #565E59; font-size: 14px; }
    mat-dialog-actions { padding-top: 8px; }
  `],
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ConfirmDialogComponent>);
}
