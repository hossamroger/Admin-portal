package com.example.dbexplorer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic "replace all children of a parent" engine for master-detail sub-resources.
 *
 * <p>Encapsulates the algorithm that was previously copy-pasted across the bespoke
 * services: validate the whole payload up front, delete the existing child rows for
 * the parent, allocate ids with MAX+1, then insert each row in order. The caller owns
 * the transaction boundary (annotate the calling service method {@code @Transactional}).
 *
 * <p>Per-sub-resource specifics — table/column names, validation, and how an input row
 * maps to insert columns — are supplied as a {@link SubResourceSpec}, so a new
 * sub-resource is a spec object rather than another hand-written delete+reinsert method.
 */
@Service
public class SubResourceService {

    private final JdbcTemplate jdbc;

    public SubResourceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Maps one input row to its ordered bound insert columns (column name → value). */
    @FunctionalInterface
    public interface RowBinder {
        LinkedHashMap<String, Object> bind(Map<String, Object> input, long id, int order, long parentKey);
    }

    /** Declarative description of a child table and how to (re)write its rows. */
    public static final class SubResourceSpec {
        final String childTable;
        final String parentFk;
        final String childPk;
        /** Runs over the whole payload before any row is touched; throws to reject. Nullable. */
        final Consumer<List<Map<String, Object>>> validator;
        final RowBinder binder;
        /** Columns inserted as raw SQL literals (e.g. CREATION_DATE → SYSDATE), no bind arg. Nullable. */
        final LinkedHashMap<String, String> literalColumns;

        public SubResourceSpec(String childTable, String parentFk, String childPk,
                               Consumer<List<Map<String, Object>>> validator,
                               RowBinder binder,
                               LinkedHashMap<String, String> literalColumns) {
            this.childTable = childTable;
            this.parentFk = parentFk;
            this.childPk = childPk;
            this.validator = validator;
            this.binder = binder;
            this.literalColumns = literalColumns;
        }
    }

    /**
     * Validate the payload, delete the parent's existing child rows, then insert the
     * supplied rows with MAX+1-allocated ids and 1-based ordering. Runs in the caller's
     * transaction.
     */
    public void replaceAll(SubResourceSpec spec, long parentKey, List<Map<String, Object>> rows) {
        if (spec.validator != null) spec.validator.accept(rows);

        jdbc.update("DELETE FROM " + spec.childTable + " WHERE " + spec.parentFk + " = ?", parentKey);

        long base = Optional.ofNullable(jdbc.queryForObject(
            "SELECT NVL(MAX(" + spec.childPk + "),0) FROM " + spec.childTable, Long.class)).orElse(0L);

        int order = 1;
        for (Map<String, Object> row : rows) {
            long id = base + order;
            LinkedHashMap<String, Object> bound = spec.binder.bind(row, id, order, parentKey);

            List<String> cols = new ArrayList<>(bound.keySet());
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) placeholders.add("?");
            List<Object> args = new ArrayList<>(bound.values());

            if (spec.literalColumns != null) {
                for (Map.Entry<String, String> e : spec.literalColumns.entrySet()) {
                    cols.add(e.getKey());
                    placeholders.add(e.getValue());
                }
            }

            jdbc.update(
                "INSERT INTO " + spec.childTable + " (" + String.join(", ", cols) + ") VALUES (" +
                String.join(", ", placeholders) + ")",
                args.toArray());
            order++;
        }
    }

    // ── Lower-level helpers for typed sub-resources (e.g. ServiceConfig DTOs) ────

    /** A delete to run (in order) before re-inserting children — supports cascade FKs. */
    public static final class DeleteStep {
        final String sql;
        final Object[] args;
        public DeleteStep(String sql, Object... args) { this.sql = sql; this.args = args; }
    }

    /** Use the supplied sequence value, or mint a fresh UUID string when absent. */
    public Object seqOrUuid(Object supplied) {
        return supplied != null ? supplied : UUID.randomUUID().toString();
    }

    /** Use the supplied id, or allocate MAX(idColumn)+1 for the table when absent. */
    public Object idOrMaxPlusOne(Object supplied, String table, String idColumn) {
        if (supplied != null) return supplied;
        return jdbc.queryForObject(
            "SELECT NVL(MAX(" + idColumn + "),0)+1 FROM " + table, Long.class);
    }

    /**
     * Run the (cascade-ordered) deletes, then insert each typed row. Lets callers keep
     * their table-specific INSERT SQL while sharing the delete-then-reinsert orchestration
     * and the id-allocation strategies above. Caller owns the transaction.
     */
    public <T> void replace(List<DeleteStep> deletes, List<T> rows, Consumer<T> insertOne) {
        for (DeleteStep d : deletes) jdbc.update(d.sql, d.args);
        if (rows != null) for (T row : rows) insertOne.accept(row);
    }
}
