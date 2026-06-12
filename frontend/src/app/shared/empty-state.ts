import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Shared empty / no-results state: a centered icon, a message, and an optional
 * projected action (e.g. a "Clear search" or "Retry" button). Gives every list
 * the same look instead of each page hand-rolling its own markup.
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <div class="es" role="status">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      <span class="es-msg">{{ message() }}</span>
      @if (hint()) { <span class="es-hint">{{ hint() }}</span> }
      <ng-content></ng-content>
    </div>
  `,
  styles: [`
    .es {
      display: flex; flex-direction: column; align-items: center; gap: 8px;
      padding: 64px 0; color: var(--app-muted); text-align: center;
    }
    mat-icon { font-size: 40px; height: 40px; width: 40px; color: var(--app-faint); }
    .es-msg { font-size: 14px; }
    .es-hint { font-size: 12px; color: var(--app-faint); }
  `],
})
export class EmptyStateComponent {
  readonly icon = input('inbox');
  readonly message = input('');
  readonly hint = input('');
}
