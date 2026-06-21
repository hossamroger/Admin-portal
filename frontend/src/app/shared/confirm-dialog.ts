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
    <div class="cd">
      <div class="cd-icon">
        <mat-icon>warning_amber</mat-icon>
      </div>
      <p class="cd-title">Are you sure?</p>
      <p class="cd-msg">{{ data.message }}</p>
      <div class="cd-actions">
        <button class="cd-btn cd-btn--cancel" mat-dialog-close>Cancel</button>
        <button class="cd-btn cd-btn--confirm" (click)="ref.close(true)">
          {{ data.confirmLabel ?? 'Delete' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .cd {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 32px 28px 24px;
      width: 380px;
      box-sizing: border-box;
    }

    .cd-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: #fff3f3;
      border: 6px solid #fff7f7;
      margin-bottom: 16px;
    }
    .cd-icon mat-icon {
      color: #e53935;
      font-size: 26px;
      width: 26px;
      height: 26px;
    }

    .cd-title {
      margin: 0 0 8px;
      font-size: 17px;
      font-weight: 700;
      color: #1a1a2e;
      line-height: 1.3;
    }

    .cd-msg {
      margin: 0 0 24px;
      font-size: 13.5px;
      color: #6b7280;
      line-height: 1.6;
    }

    .cd-actions {
      display: flex;
      gap: 10px;
      width: 100%;
    }

    .cd-btn {
      flex: 1;
      height: 40px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      border: none;
      transition: opacity 0.15s, box-shadow 0.15s;
    }
    .cd-btn:hover { opacity: 0.88; }
    .cd-btn:active { opacity: 0.75; }

    .cd-btn--cancel {
      background: #f3f4f6;
      color: #374151;
      border: 1.5px solid #e5e7eb;
    }

    .cd-btn--confirm {
      background: #e53935;
      color: #fff;
      box-shadow: 0 2px 6px rgba(229,57,53,.35);
    }
  `],
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ConfirmDialogComponent>);
}
