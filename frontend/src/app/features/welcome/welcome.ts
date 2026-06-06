import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { SchemaStateService } from '../../core/schema-state.service';

@Component({
  selector: 'app-welcome',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatButtonModule],
  templateUrl: './welcome.html',
  styleUrl: './welcome.scss',
})
export class WelcomeComponent {
  private readonly router = inject(Router);
  private readonly schemaState = inject(SchemaStateService);

  readonly overview = this.schemaState.overview;

  goToUsers(): void { this.router.navigateByUrl('/admin/users'); }
}
