import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';

/** Thin wrapper over MatSnackBar for consistent toast styling/duration. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snack = inject(MatSnackBar);

  success(message: string): void {
    this.open(message, 'notify-ok');
  }

  warn(message: string): void {
    this.open(message, 'notify-warn');
  }

  /** Extract a readable message from an HttpErrorResponse (backend sends {error}). */
  error(err: unknown, fallback = 'Something went wrong'): void {
    let msg = fallback;
    if (err instanceof HttpErrorResponse) {
      msg = err.error?.error || err.error?.message || err.message || fallback;
    } else if (err instanceof Error) {
      msg = err.message;
    }
    // errors stay longer — Oracle/validation messages take time to read
    this.open(msg, 'notify-warn', 8000);
  }

  private open(message: string, panelClass: string, duration = 3500): void {
    this.snack.open(message, 'Dismiss', {
      duration,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
      panelClass: [panelClass],
    });
  }
}
