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
      <div class="confirm-head">
        <div class="warn-badge">
          <mat-icon>warning</mat-icon>
        </div>
        <h3 mat-dialog-title>Confirm</h3>
      </div>
      <mat-dialog-content>{{ data.message }}</mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="warn" (click)="ref.close(true)">
          {{ data.confirmLabel ?? 'Delete' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .confirm-wrap {
      padding: 24px 24px 16px;
      min-width: 320px;
      max-width: 420px;
    }
    .confirm-head {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;
    }
    .warn-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: 0 0 auto;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: color-mix(in srgb, var(--app-error) 14%, transparent);
    }
    .warn-badge mat-icon {
      color: var(--app-error);
      font-size: 24px;
      width: 24px;
      height: 24px;
    }
    h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.2;
    }
    mat-dialog-content {
      margin: 0;
      padding: 0;
      color: var(--app-muted);
      font-size: 14px;
      line-height: 1.5;
    }
    mat-dialog-actions {
      gap: 8px;
      padding: 20px 0 0;
      margin: 0;
      min-height: 0;
    }
  `],
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ConfirmDialogComponent>);
}
