import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { DynamicFormComponent } from './dynamic-form';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { LookupCacheService } from '../../core/lookup-cache.service';
import { CrudRow } from '../../core/models';

/**
 * Regression coverage for the dirty-guard false positive: forms initialized
 * savedSnapshot to '' while snapshot({}) is '{}', so isDirty() returned true
 * the instant the component loaded — before the record arrived — which made
 * the canDeactivate guard pop "Discard changes?" and block tab navigation.
 */
describe('DynamicFormComponent — isDirty during load', () => {
  let crudGet: ReturnType<typeof vi.fn>;
  let routeParams: Map<string, string>;

  function configure() {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: { crudGet } },
        { provide: NotifyService, useValue: { error: vi.fn(), success: vi.fn(), warn: vi.fn() } },
        { provide: LookupCacheService, useValue: { crudLookup: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (k: string) => routeParams.get(k) ?? null } } },
        },
      ],
    });
  }

  beforeEach(() => {
    routeParams = new Map();
  });

  it('is NOT dirty while an existing record is still loading (the guard bug)', () => {
    // crudGet never emits → component stays in the loading window
    crudGet = vi.fn().mockReturnValue(new Subject<CrudRow>());
    routeParams.set('entity', 'home-banner');
    routeParams.set('id', '13');
    configure();

    const fixture = TestBed.createComponent(DynamicFormComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(true);
    expect(fixture.componentInstance.isDirty()).toBe(false);
  });

  it('is NOT dirty immediately after loading an unedited record', () => {
    crudGet = vi.fn().mockReturnValue(of({ ID: 13, URL: 'https://x', PLATFORM: 'WEB' } as CrudRow));
    routeParams.set('entity', 'home-banner');
    routeParams.set('id', '13');
    configure();

    const fixture = TestBed.createComponent(DynamicFormComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.componentInstance.isDirty()).toBe(false);
  });

  it('is NOT dirty on a fresh new record, but becomes dirty after an edit', () => {
    crudGet = vi.fn();
    routeParams.set('entity', 'home-banner');
    routeParams.set('id', 'new');
    configure();

    const fixture = TestBed.createComponent(DynamicFormComponent);
    fixture.detectChanges();
    const cmp = fixture.componentInstance;

    expect(cmp.isDirty()).toBe(false);

    // Edit a real field on this entity
    const urlField = cmp.cfg()!.fields.find(f => f.col === 'URL')!;
    cmp.set(urlField, 'https://changed');

    expect(cmp.isDirty()).toBe(true);
  });
});
