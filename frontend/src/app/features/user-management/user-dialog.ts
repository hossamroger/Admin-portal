import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { RowFilter, SaveUserRequest } from '../../core/models';

export interface UserDialogData {
  username?: string; // present => edit mode
}

const ALL_PRIVILEGES = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'EXECUTE_SQL'];
const ROLES = ['USER', 'EDITOR', 'ANALYST', 'ADMIN'];

@Component({
  selector: 'app-user-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatCheckboxModule,
  ],
  templateUrl: './user-dialog.html',
  styleUrl: './user-dialog.scss',
})
export class UserDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialogRef = inject(MatDialogRef<UserDialogComponent>);
  readonly data = inject<UserDialogData>(MAT_DIALOG_DATA);

  readonly allPrivileges = ALL_PRIVILEGES;
  readonly roles = ROLES;
  readonly isEdit = !!this.data.username;
  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: [{ value: '', disabled: this.isEdit }, Validators.required],
    password: [''],
    role: ['USER', Validators.required],
    privileges: [['SELECT'] as string[]],
    allowedTables: [''], // comma separated
    filters: [''],       // "TABLE:condition" per line
  });

  constructor() {
    if (this.isEdit) this.loadUser(this.data.username!);
  }

  private loadUser(username: string): void {
    this.api.getUser(username).subscribe(u => {
      this.form.patchValue({
        username: u.USERNAME,
        role: u.ROLE,
        privileges: u.privileges ?? [],
        allowedTables: (u.allowedTables ?? []).join(', '),
        filters: (u.filters ?? []).map(f => `${f.TABLE_NAME}:${f.FILTER_CONDITION}`).join('\n'),
      });
    });
  }

  save(): void {
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);

    const raw = this.form.getRawValue();
    const body: SaveUserRequest = {
      username: raw.username.trim(),
      password: raw.password,
      role: raw.role,
      privileges: raw.privileges,
      allowedTables: this.splitList(raw.allowedTables),
      filters: this.parseFilters(raw.filters),
    };

    this.api.saveUser(body).subscribe({
      next: () => { this.notify.success('User saved'); this.dialogRef.close(true); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Failed to save user'); },
    });
  }

  private splitList(value: string): string[] {
    return value.split(',').map(s => s.trim()).filter(Boolean);
  }

  private parseFilters(value: string): RowFilter[] {
    return value
      .split('\n')
      .filter(line => line.includes(':'))
      .map(line => {
        const idx = line.indexOf(':');
        return {
          TABLE_NAME: line.slice(0, idx).trim(),
          FILTER_CONDITION: line.slice(idx + 1).trim(),
        };
      });
  }
}
