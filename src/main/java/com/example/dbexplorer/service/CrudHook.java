package com.example.dbexplorer.service;

import com.example.dbexplorer.config.CrudEntities.CrudEntity;

import java.util.Map;

/**
 * Per-entity extension point for the generic CRUD engine. Register a Spring bean
 * implementing this interface to run custom behavior (derived columns, cascades,
 * cross-row checks, side effects) around a specific entity's writes — the escape
 * hatch that previously forced teams into a fully bespoke service.
 *
 * <p>All callbacks default to no-ops, so a hook overrides only what it needs. Hooks
 * run inside the engine's transaction, so throwing rolls the write back.
 */
public interface CrudHook {

    /** URL slug of the entity this hook applies to (e.g. "donation-project"). */
    String entity();

    /** Mutate/validate the row before it is inserted. */
    default void beforeInsert(CrudEntity e, Map<String, Object> row) {}

    /** React after a successful insert (e.g. write child rows). */
    default void afterInsert(CrudEntity e, Map<String, Object> row, Object newId) {}

    /** Mutate/validate the row before it is updated. */
    default void beforeUpdate(CrudEntity e, String id, Map<String, Object> row) {}

    /** React before a delete (e.g. cascade or guard). */
    default void beforeDelete(CrudEntity e, String id) {}
}
