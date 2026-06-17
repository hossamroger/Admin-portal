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

  readonly isReadOnly = computed(() => {
    if (this.overview()?.readOnly) return true;
    const can = this.me()?.can;
    return !!can && !can.insert && !can.update && !can.delete;
  });
  readonly schemaName = computed(() => this.overview()?.schema || '—');

  readonly features: FeatureCard[] = [
    {
      icon: 'settings',
      title: 'Service Config',
      description: 'Configure BPM services, steps, fees, documents, providers, audience, and confirmation screens.',
      link: '/service-config',
      accent: 'green',
    },
    {
      icon: 'volunteer_activism',
      title: 'Donation Projects',
      description: 'Manage donation projects, categories, and organizations across the platform.',
      link: '/donations',
      accent: 'teal',
    },
    {
      icon: 'cloud_upload',
      title: 'Attachments',
      description: 'Upload any file and instantly get a permanent shareable public URL.',
      link: '/attachments',
      accent: 'mint',
    },
    {
      icon: 'manage_accounts',
      title: 'User Management',
      description: 'Create and manage users, assign roles and privileges, and control access.',
      link: '/admin/users',
      accent: 'green',
    },
  ];

  /** Hide admin-only cards from non-admin users. */
  readonly visibleFeatures = computed(() =>
    this.features.filter(f => f.link !== '/admin/users' || this.isAdmin()));

  readonly tips: QuickTip[] = [
    { icon: 'settings',        text: 'Open <strong>Service Config</strong> from the sidebar to configure BPM processes end-to-end.' },
    { icon: 'tab',             text: 'Each service has tabs: <strong>Basic Info, Steps, Fees, Documents, Providers, Audience, Confirmation</strong>.' },
    { icon: 'volunteer_activism', text: 'Use <strong>Donation Projects</strong> to manage amounts, categories, and organizations.' },
    { icon: 'sync',            text: 'Hit <strong>Sync</strong> in the toolbar whenever the DB schema changes.' },
    { icon: 'chat',            text: 'Use the <strong>help chatbot</strong> (bottom-right) for guidance at any time.' },
  ];

  navigate(link: string): void {
    if (link) this.router.navigateByUrl(link);
  }
}
