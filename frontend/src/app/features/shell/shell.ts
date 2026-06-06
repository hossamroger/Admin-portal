import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map } from 'rxjs';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { SchemaStateService } from '../../core/schema-state.service';
import { NotifyService } from '../../core/notify.service';
import { DbObject, ObjectType } from '../../core/models';

interface Group {
  type: ObjectType;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatButtonModule, MatIconModule,
    MatExpansionModule, MatListModule, MatMenuModule, MatTooltipModule, MatProgressSpinnerModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class ShellComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly schemaState = inject(SchemaStateService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);

  readonly me = this.auth.me;
  readonly isAdmin = this.auth.isAdmin;
  readonly canRunSql = computed(() => !!this.auth.can()?.runSql);
  readonly overview = this.schemaState.overview;
  readonly changed = this.schemaState.changed;
  readonly syncing = signal(false);

  /** True on tablet/phone widths — sidenav becomes an overlay drawer. */
  readonly isHandset = toSignal(
    this.breakpoints.observe('(max-width: 960px)').pipe(map(r => r.matches)),
    { initialValue: false },
  );

  readonly groups: Group[] = [
    { type: 'TABLE', label: 'Tables', icon: 'table_chart' },
    { type: 'VIEW', label: 'Views', icon: 'grid_view' },
    { type: 'PROCEDURE', label: 'Procedures', icon: 'settings_ethernet' },
    { type: 'FUNCTION', label: 'Functions', icon: 'functions' },
    { type: 'PACKAGE', label: 'Packages', icon: 'inventory_2' },
    { type: 'TRIGGER', label: 'Triggers', icon: 'bolt' },
    { type: 'SEQUENCE', label: 'Sequences', icon: 'tag' },
  ];

  private readonly objectsByType = signal<Record<string, DbObject[]>>({});
  private readonly loadingType = signal<string | null>(null);

  constructor() {
    this.schemaState.refresh();
  }

  count(type: ObjectType): number {
    return this.overview()?.counts?.[type] ?? 0;
  }

  objects(type: ObjectType): DbObject[] {
    return this.objectsByType()[type] ?? [];
  }

  isLoading(type: ObjectType): boolean {
    return this.loadingType() === type;
  }

  /** Lazy-load a group's objects the first time its panel is expanded. */
  loadGroup(type: ObjectType): void {
    if (this.objectsByType()[type]) return;
    this.loadingType.set(type);
    this.api.objects(type).subscribe({
      next: objs => {
        this.objectsByType.update(m => ({ ...m, [type]: objs }));
        this.loadingType.set(null);
      },
      error: err => {
        this.objectsByType.update(m => ({ ...m, [type]: [] }));
        this.loadingType.set(null);
        this.notify.error(err, 'Failed to load objects');
      },
    });
  }

  /** Build the router link for an object (data view vs. source view). */
  linkFor(obj: DbObject): any[] {
    return obj.type === 'TABLE' || obj.type === 'VIEW'
      ? ['/table', obj.name]
      : ['/source', obj.type, obj.name];
  }

  onObjectClick(drawerClosed: () => void): void {
    if (this.isHandset()) drawerClosed();
  }

  sync(): void {
    this.syncing.set(true);
    this.objectsByType.set({}); // force groups to reload fresh
    this.schemaState.sync();
    // synced$ fires synchronously after overview reloads; give a short visual beat
    setTimeout(() => {
      this.syncing.set(false);
      this.notify.success('Synced with the latest DB changes');
    }, 400);
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
