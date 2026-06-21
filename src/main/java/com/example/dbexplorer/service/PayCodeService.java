package com.example.dbexplorer.service;

import com.example.dbexplorer.dto.PayCodeDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PayCodeService {

    private final JdbcTemplate jdbc;

    public PayCodeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public PayCodeListResponse list(String search, int page, int pageSize) {
        String where = "";
        List<Object> args = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            where = " WHERE UPPER(PROCESS_CODE) LIKE ? OR UPPER(PROCESS_IDENTIFIER) LIKE ? OR UPPER(ENTITY_SERVICE_CODE) LIKE ?";
            String q = "%" + search.toUpperCase() + "%";
            args.add(q); args.add(q); args.add(q);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM LKP_PAY_CODE" + where, Long.class,
                args.toArray());

        String sql = "SELECT ID, ENTITY_CODE, ENTITY_SERVICE_CODE, PROCESS_CODE, PROCESS_IDENTIFIER"
                + " FROM LKP_PAY_CODE" + where
                + " ORDER BY PROCESS_CODE OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add(page * pageSize);
        args.add(pageSize);

        List<PayCodeSummary> items = jdbc.query(sql, args.toArray(), (rs, i) -> {
            PayCodeSummary s = new PayCodeSummary();
            s.id                = rs.getObject("ID");
            s.entityCode        = rs.getString("ENTITY_CODE");
            s.entityServiceCode = rs.getString("ENTITY_SERVICE_CODE");
            s.processCode       = rs.getString("PROCESS_CODE");
            s.processIdentifier = rs.getString("PROCESS_IDENTIFIER");
            return s;
        });

        PayCodeListResponse r = new PayCodeListResponse();
        r.items    = items;
        r.total    = total == null ? 0 : total;
        r.page     = page;
        r.pageSize = pageSize;
        return r;
    }

    // ── Get one (by unique ID) ──────────────────────────────────────────────────

    public PayCodePayload get(long id) {
        List<PayCodeDto> masters = jdbc.query(
                "SELECT ID, ENTITY_CODE, ENTITY_DEPARTMENT_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + " ENTITY_SERVICE_CODE, PROCESS_CODE, PROCESS_IDENTIFIER"
                + " FROM LKP_PAY_CODE WHERE ID = ?",
                new Object[]{ id },
                (rs, i) -> {
                    PayCodeDto d = new PayCodeDto();
                    d.id                        = rs.getObject("ID");
                    d.entityCode                = rs.getString("ENTITY_CODE");
                    d.entityDepartmentCode      = rs.getString("ENTITY_DEPARTMENT_CODE");
                    d.entityServiceCategoryCode = rs.getString("ENTITY_SERVICE_CATEGORY_CODE");
                    d.entityServiceCode         = rs.getString("ENTITY_SERVICE_CODE");
                    d.processCode               = rs.getString("PROCESS_CODE");
                    d.processIdentifier         = rs.getString("PROCESS_IDENTIFIER");
                    return d;
                });
        if (masters.isEmpty()) return null;

        PayCodeDto master = masters.get(0);
        PayCodePayload p = new PayCodePayload();
        p.payCode = master;
        p.details = fetchDetail(id, master);
        return p;
    }

    private PayCodeDetailsDto fetchDetail(long payCodeId, PayCodeDto master) {
        List<PayCodeDetailsDto> rows = jdbc.query(
                "SELECT d.ID, d.PROCESS_CODE, d.ENTITY_SERVICE_CODE, d.ENTITY_SERVICE_CATEGORY_CODE,"
                + "     d.PAY_ENTITY_CODE, d.PAY_DEPARTMENT_CODE,"
                + "     d.SERVICE_DESC_AR, d.SERVICE_DESC_EN,"
                + "     d.ENTITY_NAME_AR, d.ENTITY_NAME_EN, d.ENTITY_CODE"
                + " FROM LKP_PAY_CODE_DETAILS d"
                + " JOIN LKP_PAY_CODE c"
                + "   ON c.ENTITY_CODE                  = d.PAY_ENTITY_CODE"
                + "  AND c.ENTITY_DEPARTMENT_CODE        = d.PAY_DEPARTMENT_CODE"
                + "  AND c.ENTITY_SERVICE_CATEGORY_CODE  = d.ENTITY_SERVICE_CATEGORY_CODE"
                + "  AND c.ENTITY_SERVICE_CODE           = d.ENTITY_SERVICE_CODE"
                + "  AND c.PROCESS_CODE                  = d.PROCESS_CODE"
                + " WHERE c.ID = ?"
                + " FETCH FIRST 1 ROWS ONLY",
                new Object[]{ payCodeId },
                DETAIL_MAPPER);

        if (!rows.isEmpty()) return rows.get(0);

        // Fallback: match only on the 3-column key that was available in older data
        List<PayCodeDetailsDto> fallback = jdbc.query(
                "SELECT d.ID, d.PROCESS_CODE, d.ENTITY_SERVICE_CODE, d.ENTITY_SERVICE_CATEGORY_CODE,"
                + "     d.PAY_ENTITY_CODE, d.PAY_DEPARTMENT_CODE,"
                + "     d.SERVICE_DESC_AR, d.SERVICE_DESC_EN,"
                + "     d.ENTITY_NAME_AR, d.ENTITY_NAME_EN, d.ENTITY_CODE"
                + " FROM LKP_PAY_CODE_DETAILS d"
                + " WHERE d.PROCESS_CODE = ?"
                + "   AND d.ENTITY_SERVICE_CATEGORY_CODE = ?"
                + "   AND d.ENTITY_SERVICE_CODE = ?"
                + " FETCH FIRST 1 ROWS ONLY",
                new Object[]{ master.processCode, master.entityServiceCategoryCode, master.entityServiceCode },
                DETAIL_MAPPER);
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    private static final org.springframework.jdbc.core.RowMapper<PayCodeDetailsDto> DETAIL_MAPPER =
            (rs, i) -> {
                PayCodeDetailsDto d = new PayCodeDetailsDto();
                d.id                        = rs.getObject("ID");
                d.processCode               = rs.getString("PROCESS_CODE");
                d.entityServiceCode         = rs.getString("ENTITY_SERVICE_CODE");
                d.entityServiceCategoryCode = rs.getString("ENTITY_SERVICE_CATEGORY_CODE");
                d.payEntityCode             = rs.getString("PAY_ENTITY_CODE");
                d.payDepartmentCode         = rs.getString("PAY_DEPARTMENT_CODE");
                d.serviceDescAr             = rs.getString("SERVICE_DESC_AR");
                d.serviceDescEn             = rs.getString("SERVICE_DESC_EN");
                d.entityNameAr              = rs.getString("ENTITY_NAME_AR");
                d.entityNameEn              = rs.getString("ENTITY_NAME_EN");
                d.entityCode                = rs.getString("ENTITY_CODE");
                return d;
            };

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public long create(PayCodePayload req) {
        PayCodeDto c = req.payCode;
        requireNoDuplicate(c, null);
        long id = jdbc.queryForObject("SELECT LKP_PAY_CODE_SEQ.NEXTVAL FROM DUAL", Long.class);
        jdbc.update(
                "INSERT INTO LKP_PAY_CODE"
                + " (ID, ENTITY_CODE, ENTITY_DEPARTMENT_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + "  ENTITY_SERVICE_CODE, QUANTITY, PROCESS_CODE, PROCESS_IDENTIFIER)"
                + " VALUES (?, ?, ?, ?, ?, '1', ?, ?)",
                id, c.entityCode, c.entityDepartmentCode, c.entityServiceCategoryCode,
                c.entityServiceCode, c.processCode, c.processIdentifier);

        if (req.details != null) {
            insertDetail(req.details, c);
        }
        return id;
    }

    // ── Update (by unique ID) ────────────────────────────────────────────────────

    @Transactional
    public void update(long id, PayCodePayload req) {
        PayCodeDto c = req.payCode;
        requireNoDuplicate(c, id);

        // Load the existing row so the OLD composite key can scope the detail delete.
        PayCodeDto old = jdbc.query(
                "SELECT ENTITY_CODE, ENTITY_DEPARTMENT_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + " ENTITY_SERVICE_CODE, PROCESS_CODE FROM LKP_PAY_CODE WHERE ID = ?",
                new Object[]{ id },
                (rs, i) -> {
                    PayCodeDto d = new PayCodeDto();
                    d.entityCode                = rs.getString("ENTITY_CODE");
                    d.entityDepartmentCode      = rs.getString("ENTITY_DEPARTMENT_CODE");
                    d.entityServiceCategoryCode = rs.getString("ENTITY_SERVICE_CATEGORY_CODE");
                    d.entityServiceCode         = rs.getString("ENTITY_SERVICE_CODE");
                    d.processCode               = rs.getString("PROCESS_CODE");
                    return d;
                }).stream().findFirst().orElse(null);
        if (old == null) {
            throw new java.util.NoSuchElementException("Pay code not found");
        }

        jdbc.update(
                "UPDATE LKP_PAY_CODE SET"
                + " ENTITY_CODE = ?, ENTITY_DEPARTMENT_CODE = ?,"
                + " ENTITY_SERVICE_CATEGORY_CODE = ?, ENTITY_SERVICE_CODE = ?,"
                + " PROCESS_CODE = ?, PROCESS_IDENTIFIER = ?"
                + " WHERE ID = ?",
                c.entityCode, c.entityDepartmentCode,
                c.entityServiceCategoryCode, c.entityServiceCode,
                c.processCode, c.processIdentifier, id);

        // Replace the single detail: delete the old one (scoped by the OLD key), insert the new.
        deleteDetailByKey(old);
        if (req.details != null) {
            insertDetail(req.details, c);
        }
    }

    private void deleteDetailByKey(PayCodeDto key) {
        jdbc.update(
                "DELETE FROM LKP_PAY_CODE_DETAILS"
                + " WHERE PROCESS_CODE = ?"
                + "   AND PAY_ENTITY_CODE = ?"
                + "   AND PAY_DEPARTMENT_CODE = ?"
                + "   AND ENTITY_SERVICE_CATEGORY_CODE = ?"
                + "   AND ENTITY_SERVICE_CODE = ?",
                key.processCode, key.entityCode, key.entityDepartmentCode,
                key.entityServiceCategoryCode, key.entityServiceCode);
    }

    /**
     * A pay code's identity is the full 5-column composite key. Reject a save that
     * would collide with an existing row (excluding the row being edited, by ID).
     */
    private void requireNoDuplicate(PayCodeDto c, Object excludeId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM LKP_PAY_CODE"
                + " WHERE ENTITY_CODE = ?"
                + "   AND ENTITY_DEPARTMENT_CODE = ?"
                + "   AND ENTITY_SERVICE_CATEGORY_CODE = ?"
                + "   AND ENTITY_SERVICE_CODE = ?"
                + "   AND PROCESS_CODE = ?");
        List<Object> args = new ArrayList<>();
        args.add(c.entityCode); args.add(c.entityDepartmentCode);
        args.add(c.entityServiceCategoryCode); args.add(c.entityServiceCode);
        args.add(c.processCode);
        if (excludeId != null) {
            sql.append(" AND ID <> ?");
            args.add(excludeId);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        if (count != null && count > 0) {
            throw new IllegalArgumentException(
                "A pay code with the same Entity Code, Department Code, Service Category Code, "
                + "Service Code and Process Code already exists.");
        }
    }

    private void insertDetail(PayCodeDetailsDto d, PayCodeDto parent) {
        jdbc.update(
                "INSERT INTO LKP_PAY_CODE_DETAILS"
                + " (ID, PROCESS_CODE, ENTITY_SERVICE_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + "  PAY_ENTITY_CODE, PAY_DEPARTMENT_CODE,"
                + "  SERVICE_DESC_AR, SERVICE_DESC_EN,"
                + "  ENTITY_NAME_AR, ENTITY_NAME_EN, ENTITY_CODE, SHOW_TOTAL)"
                + " VALUES (LKP_PAY_CODE_DETAILS_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'F')",
                parent.processCode,
                parent.entityServiceCode,
                parent.entityServiceCategoryCode,
                parent.entityCode,
                parent.entityDepartmentCode,
                d.serviceDescAr,
                d.serviceDescEn,
                d.entityNameAr,
                d.entityNameEn,
                d.entityCode);
    }

    // ── Delete (by unique ID) ────────────────────────────────────────────────────

    @Transactional
    public void delete(long id) {
        // Scope the detail delete to this exact row's composite key, then drop the master.
        PayCodeDto key = jdbc.query(
                "SELECT ENTITY_CODE, ENTITY_DEPARTMENT_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + " ENTITY_SERVICE_CODE, PROCESS_CODE FROM LKP_PAY_CODE WHERE ID = ?",
                new Object[]{ id },
                (rs, i) -> {
                    PayCodeDto d = new PayCodeDto();
                    d.entityCode                = rs.getString("ENTITY_CODE");
                    d.entityDepartmentCode      = rs.getString("ENTITY_DEPARTMENT_CODE");
                    d.entityServiceCategoryCode = rs.getString("ENTITY_SERVICE_CATEGORY_CODE");
                    d.entityServiceCode         = rs.getString("ENTITY_SERVICE_CODE");
                    d.processCode               = rs.getString("PROCESS_CODE");
                    return d;
                }).stream().findFirst().orElse(null);
        if (key != null) {
            deleteDetailByKey(key);
        }
        jdbc.update("DELETE FROM LKP_PAY_CODE WHERE ID = ?", id);
    }

    // ── Lookup: ENTITY_LKP ────────────────────────────────────────────────────

    public List<EntityLookup> lookupEntities() {
        return jdbc.query(
                "SELECT ENTITY_CODE, ENTITY_NAME_EN, ENTITY_NAME_AR FROM ENTITY_LKP ORDER BY ENTITY_NAME_EN",
                (rs, i) -> {
                    EntityLookup e = new EntityLookup();
                    e.entityCode   = rs.getString("ENTITY_CODE");
                    e.entityNameEn = rs.getString("ENTITY_NAME_EN");
                    e.entityNameAr = rs.getString("ENTITY_NAME_AR");
                    return e;
                });
    }
}
