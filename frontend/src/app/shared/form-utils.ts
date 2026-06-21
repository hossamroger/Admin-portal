/**
 * Shared form helpers: flag normalization and JSON-snapshot dirty tracking.
 * These centralize logic that was previously copy-pasted across the form
 * components, with no behavior change.
 */

/** Values legacy data may use for "on" — normalized to the canonical pair. */
export const TRUTHY = new Set<unknown>([1, '1', 'T', 't', 'Y', 'y', true, 'true', 'TRUE']);

/**
 * Normalize a loose/legacy flag value to a canonical on/off value.
 *
 * A value is "on" when it is in {@link TRUTHY}; anything else (including null)
 * is "off". Defaults to the 'Y'/'N' pair used by most forms; pass `on`/`off`
 * to target a different canonical pair (e.g. [1, 0] or ['T', 'F']).
 */
export function normalizeFlag(value: unknown, on: unknown = 'Y', off: unknown = 'N'): unknown {
  return TRUTHY.has(value) ? on : off;
}

/** JSON snapshot of a value, used as the dirty-tracking baseline. */
export function snapshot(value: unknown): string {
  return JSON.stringify(value);
}

/** True when the current value differs from a previously taken {@link snapshot}. */
export function isDirty(snap: string, value: unknown): boolean {
  return JSON.stringify(value) !== snap;
}

/**
 * Snapshot individual named sections and return which ones differ from the
 * baseline snap (which was taken of the whole object at load/save time).
 *
 * Usage: snapFields(baselineSnap, { master: obj1, details: obj2 })
 * Returns the keys whose current JSON differs from what's in the baseline.
 */
export function changedSections(
  baselineSnap: string,
  sections: Record<string, unknown>,
): string[] {
  let baseline: Record<string, unknown>;
  try { baseline = JSON.parse(baselineSnap); } catch { return Object.keys(sections); }
  return Object.keys(sections).filter(
    key => JSON.stringify(sections[key]) !== JSON.stringify(baseline[key]),
  );
}
