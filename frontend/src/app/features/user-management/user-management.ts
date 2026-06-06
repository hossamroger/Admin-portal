import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { AdminUserSummary } from '../../core/models';
import { UserDialogComponent } from './user-dialog';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';

@Component({
  selector: 'app-user-management',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule, MatDialogModule],
  templateUrl: './user-management.html',
  styleUrl: './user-management.scss',
})
export class UserManagementComponent {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly users = signal<AdminUserSummary[]>([]);
  readonly columns = ['username', 'role', 'status', 'actions'];

  constructor() { this.load(); }

  load(): void {
    this.api.listUsers().subscribe({
      next: users => this.users.set(users),
      error: err => this.notify.error(err, 'Failed to load users'),
    });
  }

  add(): void { this.openDialog({}); }

  edit(username: string): void { this.openDialog({ username }); }

  remove(username: string): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: { message: `Delete user "${username}"? This cannot be undone.` },
        width: '400px',
      })
      .afterClosed()
      .subscribe(confirmed => {
        if (!confirmed) return;
        this.api.deleteUser(username).subscribe({
          next: () => { this.notify.success('User deleted'); this.load(); },
          error: err => this.notify.error(err, 'Failed to delete user'),
        });
      });
  }

  isPrimaryAdmin(username: string): boolean {
    return username.toLowerCase() === 'admin';
  }

  private openDialog(data: { username?: string }): void {
    this.dialog
      .open(UserDialogComponent, { data, width: '520px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe(saved => { if (saved) this.load(); });
  }
}
