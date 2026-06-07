import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { SchemaStateService } from '../../core/schema-state.service';
import { AuthService } from '../../core/auth.service';

interface FeatureCard {
  icon: string;
  title: string;
  description: string;
  link: string;
  accent: string;
}

interface QuickTip {
  icon: string;
  text: string;
}

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
  private readonly auth = inject(AuthService);

  readonly overview = this.schemaState.overview;
  readonly me = this.auth.me;
  readonly isAdmin = this.auth.isAdmin;

  readonly greeting = computed(() => {
    const name = this.me()?.username;
    const h = new Date().getHours();
    const time = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
    return name ? `${time}, ${name}` : time;
  });

  readonly tableCount = computed(() => this.overview()?.counts?.['TABLE'] ?? 0);
  readonly isReadOnly = computed(() => this.overview()?.readOnly ?? false);
  readonly schemaName = computed(() => this.overview()?.schema || '—');

  readonly features: FeatureCard[] = [
    {
      icon: 'table_chart',
      title: 'Browse Tables',
      description: 'Explore, filter, sort and paginate any table in your schema. Inline editing with per-row save.',
      link: '',
      accent: 'green',
    },
    {
      icon: 'cloud_upload',
      title: 'Attachments',
      description: 'Upload any file to Firebase Cloud Storage and instantly get a permanent shareable public URL.',
      link: '/attachments',
      accent: 'teal',
    },
    {
      icon: 'manage_accounts',
      title: 'User Management',
      description: 'Create and manage users, assign roles and privileges, restrict access to specific tables.',
      link: '/admin/users',
      accent: 'mint',
    },
  ];

  /** Hide admin-only cards from non-admin users. */
  readonly visibleFeatures = computed(() =>
    this.features.filter(f => f.link !== '/admin/users' || this.isAdmin()));

  readonly tips: QuickTip[] = [
    { icon: 'sidebar',       text: 'Expand <strong>Tables</strong> in the sidebar to browse your schema objects.' },
    { icon: 'filter_alt',   text: 'Type in the sidebar search box to instantly filter hundreds of tables.' },
    { icon: 'sync',         text: 'Hit <strong>Sync</strong> in the toolbar whenever the DB schema changes.' },
    { icon: 'smart_button', text: 'Click a column header to sort across all records — not just the current page.' },
    { icon: 'chat',         text: 'Use the <strong>help chatbot</strong> (bottom-right) for guidance at any time.' },
  ];

  navigate(link: string): void {
    if (link) this.router.navigateByUrl(link);
  }
}
