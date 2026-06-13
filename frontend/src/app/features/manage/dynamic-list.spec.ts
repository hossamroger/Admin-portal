import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { DynamicListComponent } from './dynamic-list';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { CrudListResponse } from '../../core/models';

/**
 * Regression coverage for the bug where navigating between two entities that
 * both render through DynamicListComponent (e.g. Home Banner Config ->
 * Process Statuses) left stale data on screen: ngOnInit ran once and never
 * reacted to the entity input changing under withComponentInputBinding().
 */
describe('DynamicListComponent — entity reload', () => {
  let crudList: ReturnType<typeof vi.fn>;

  const emptyResponse: CrudListResponse = { items: [], total: 0, page: 0, pageSize: 50 };

  beforeEach(() => {
    crudList = vi.fn().mockReturnValue(of(emptyResponse));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: { crudList } },
        { provide: NotifyService, useValue: { error: vi.fn(), success: vi.fn(), warn: vi.fn() } },
      ],
    });
  });

  it('loads data for the initial entity', () => {
    const fixture = TestBed.createComponent(DynamicListComponent);
    fixture.componentRef.setInput('entity', 'home-banner');
    fixture.detectChanges(); // flush the effect

    expect(crudList).toHaveBeenCalledTimes(1);
    expect(crudList.mock.calls[0][0]).toBe('home-banner');
    expect(fixture.componentInstance.cfg()?.name).toBe('home-banner');
  });

  it('reloads when the entity input changes (the navigation bug)', () => {
    const fixture = TestBed.createComponent(DynamicListComponent);
    fixture.componentRef.setInput('entity', 'home-banner');
    fixture.detectChanges();

    expect(crudList).toHaveBeenCalledTimes(1);

    // Simulate the router reusing the component for a different :entity param.
    fixture.componentRef.setInput('entity', 'process-status');
    fixture.detectChanges();

    expect(crudList).toHaveBeenCalledTimes(2);
    expect(crudList.mock.calls[1][0]).toBe('process-status');
    expect(fixture.componentInstance.cfg()?.name).toBe('process-status');
  });

  it('resets search and sort state when switching entities', () => {
    const fixture = TestBed.createComponent(DynamicListComponent);
    fixture.componentRef.setInput('entity', 'home-banner');
    fixture.detectChanges();

    fixture.componentInstance.search.set('acme');
    fixture.componentInstance.sortCol.set('URL');

    fixture.componentRef.setInput('entity', 'process-status');
    fixture.detectChanges();

    expect(fixture.componentInstance.search()).toBe('');
    expect(fixture.componentInstance.sortCol()).toBeNull();
  });
});
