import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { Subscription, filter, map, take } from 'rxjs';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ChatbotComponent } from '../chatbot/chatbot';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { SchemaStateService } from '../../core/schema-state.service';
import { NotifyService } from '../../core/notify.service';
import { ThemeService } from '../../core/theme.service';
import { DbObject, ObjectType } from '../../core/models';
import { ENTITY_CONFIGS, NAV_ENTITIES } from '../manage/entity-configs';

interface Group { type: ObjectType; label: string; icon: string; }

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, ScrollingModule,
    MatToolbarModule, MatSidenavModule, MatButtonModule, MatIconModule,
    MatExpansionModule, MatListModule, MatMenuModule, MatTooltipModule, MatProgressSpinnerModule,
    ChatbotComponent,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class ShellComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly schemaState = inject(SchemaStateService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly themeService = inject(ThemeService);

  readonly me = this.auth.me;
  readonly isAdmin = this.auth.isAdmin;
  readonly overview = this.schemaState.overview;
  readonly changed = this.schemaState.changed;
  readonly syncing = signal(false);
  readonly filterText = signal('');

  /** Dynamic-CRUD entities pinned in the sidebar. */
  readonly navEntities = NAV_ENTITIES;

  readonly isHandset = toSignal(
    this.breakpoints.observe('(max-width: 960px)').pipe(map(r => r.matches)),
    { initialValue: false },
  );

  /** Current page context shown in the toolbar. */
  readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(() => this.titleFromUrl(this.router.url)),
    ),
    { initialValue: this.titleFromUrl(this.router.url) },
  );

  readonly groups: Group[] = [
    { type: 'TABLE', label: 'Tables', icon: 'table_chart' },
  ];

  private readonly objectsByType = signal<Record<string, DbObject[]>>({});
  private readonly loadingType = signal<string | null>(null);
  readonly groupErrors = signal<Record<string, string>>({});

  /** Stable memoized filtered list — same array reference when filter+data unchanged,
   *  so CDK virtual scroll doesn't reset its scroll position on every navigation. */
  private readonly filteredCache = computed(() => {
    const all = this.objectsByType();
    const q = this.filterText().toLowerCase().trim();
    const result: Record<string, DbObject[]> = {};
    for (const type of Object.keys(all)) {
      result[type] = q ? all[type].filter(o => o.name.toLowerCase().includes(q)) : all[type];
    }
    return result;
  });

  private syncSub?: Subscription;

  constructor() { this.schemaState.refresh(); }

  ngOnDestroy(): void { this.syncSub?.unsubscribe(); }

  private titleFromUrl(url: string): string {
    if (url.startsWith('/table/')) return decodeURIComponent(url.split('/table/')[1].split('?')[0]);
    if (url.startsWith('/source/')) { const p = url.split('/'); return decodeURIComponent(p[p.length - 1]); }
    if (url.startsWith('/admin/users')) return 'User Management';
    if (url.startsWith('/manage/')) {
      const [path, qs] = url.split('?');
      const entity = path.split('/manage/')[1].split('/')[0];
      if (entity === 'donation-project' && qs) {
        const tab = new URLSearchParams(qs).get('tab');
        if (tab === 'categories')    return ENTITY_CONFIGS['donation-category']?.title    ?? '';
        if (tab === 'organizations') return ENTITY_CONFIGS['donation-organization']?.title ?? '';
      }
      return ENTITY_CONFIGS[entity]?.title ?? '';
    }
    return '';
  }

  userInitials(username: string | null): string {
    if (!username) return '?';
    return (username as string).slice(0, 2).toUpperCase();
  }

  count(type: ObjectType): number { return this.overview()?.counts?.[type] ?? 0; }

  filteredObjects(type: ObjectType): DbObject[] { return this.filteredCache()[type] ?? []; }

  objects(type: ObjectType): DbObject[] { return this.objectsByType()[type] ?? []; }

  isLoading(type: ObjectType): boolean { return this.loadingType() === type; }

  trackByName(_: number, obj: DbObject): string { return obj.name; }

  viewportHeight(type: ObjectType): number {
    return Math.min(this.filteredObjects(type).length * 33, 380);
  }

  loadGroup(type: ObjectType): void {
    if (this.objectsByType()[type]) return;
    this.loadingType.set(type);
    this.groupErrors.update(m => { const n = { ...m }; delete n[type]; return n; });
    this.api.objects(type).subscribe({
      next: objs => { this.objectsByType.update(m => ({ ...m, [type]: objs })); this.loadingType.set(null); },
      error: err => {
        this.objectsByType.update(m => ({ ...m, [type]: [] }));
        this.loadingType.set(null);
        this.groupErrors.update(m => ({ ...m, [type]: 'Failed to load' }));
        this.notify.error(err, 'Failed to load objects');
      },
    });
  }

  linkFor(obj: DbObject): any[] {
    return obj.type === 'TABLE' || obj.type === 'VIEW' ? ['/table', obj.name] : ['/source', obj.type, obj.name];
  }

  onObjectClick(drawerClosed: () => void): void { if (this.isHandset()) drawerClosed(); }

  sync(): void {
    this.syncing.set(true);
    const alreadyLoaded = Object.keys(this.objectsByType());
    this.objectsByType.set({});
    this.syncSub?.unsubscribe();
    this.syncSub = this.schemaState.synced$.pipe(take(1)).subscribe(() => {
      this.syncing.set(false);
      this.notify.success('Synced with the latest DB changes');
    });
    this.schemaState.sync();
    // Re-fetch any groups that were already expanded so they don't go blank.
    for (const type of alreadyLoaded) this.loadGroup(type as ObjectType);
  }

  isDark(): boolean { return this.themeService.isDark(); }
  toggleTheme(): void { this.themeService.toggle(); }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
