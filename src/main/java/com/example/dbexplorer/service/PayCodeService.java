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
        if (search != null && !search.isBlank()) {
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
            s.id                 = rs.getObject("ID");
            s.entityCode         = rs.getString("ENTITY_CODE");
            s.entityServiceCode  = rs.getString("ENTITY_SERVICE_CODE");
            s.processCode        = rs.getString("PROCESS_CODE");
            s.processIdentifier  = rs.getString("PROCESS_IDENTIFIER");
            return s;
        });

        PayCodeListResponse r = new PayCodeListResponse();
        r.items    = items;
        r.total    = total == null ? 0 : total;
        r.page     = page;
        r.pageSize = pageSize;
        return r;
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    public PayCodePayload get(String processCode) {
        List<PayCodeDto> masters = jdbc.query(
                "SELECT ID, ENTITY_CODE, ENTITY_DEPARTMENT_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + " ENTITY_SERVICE_CODE, PROCESS_CODE, PROCESS_IDENTIFIER"
                + " FROM LKP_PAY_CODE WHERE PROCESS_CODE = ?",
                new Object[]{ processCode },
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

        List<PayCodeDetailsDto> details = fetchDetails(processCode);

        PayCodePayload p = new PayCodePayload();
        p.payCode = masters.get(0);
        p.details = details;
        return p;
    }

    private List<PayCodeDetailsDto> fetchDetails(String processCode) {
        return jdbc.query(
                "SELECT ID, PROCESS_CODE, ENTITY_SERVICE_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + " PAY_ENTITY_CODE, PAY_DEPARTMENT_CODE,"
                + " SERVICE_DESC_AR, SERVICE_DESC_EN,"
                + " ENTITY_NAME_AR, ENTITY_NAME_EN, ENTITY_CODE"
                + " FROM LKP_PAY_CODE_DETAILS WHERE PROCESS_CODE = ?"
                + " ORDER BY ID",
                new Object[]{ processCode },
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
                });
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public void create(PayCodePayload req) {
        PayCodeDto c = req.payCode;
        jdbc.update(
                "INSERT INTO LKP_PAY_CODE"
                + " (ID, ENTITY_CODE, ENTITY_DEPARTMENT_CODE, ENTITY_SERVICE_CATEGORY_CODE,"
                + "  ENTITY_SERVICE_CODE, QUANTITY, PROCESS_CODE, PROCESS_IDENTIFIER)"
                + " VALUES (LKP_PAY_CODE_SEQ.NEXTVAL, ?, ?, ?, ?, '1', ?, ?)",
                c.entityCode, c.entityDepartmentCode, c.entityServiceCategoryCode,
                c.entityServiceCode, c.processCode, c.processIdentifier);

        if (req.details != null) {
            for (PayCodeDetailsDto d : req.details) {
                insertDetail(d, c);
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public void update(String processCode, PayCodePayload req) {
        PayCodeDto c = req.payCode;
        jdbc.update(
                "UPDATE LKP_PAY_CODE SET"
                + " ENTITY_CODE = ?, ENTITY_DEPARTMENT_CODE = ?,"
                + " ENTITY_SERVICE_CATEGORY_CODE = ?, ENTITY_SERVICE_CODE = ?,"
                + " PROCESS_IDENTIFIER = ?"
                + " WHERE PROCESS_CODE = ?",
                c.entityCode, c.entityDepartmentCode,
                c.entityServiceCategoryCode, c.entityServiceCode,
                c.processIdentifier, processCode);

        // Replace all details
        jdbc.update("DELETE FROM LKP_PAY_CODE_DETAILS WHERE PROCESS_CODE = ?", processCode);
        if (req.details != null) {
            for (PayCodeDetailsDto d : req.details) {
                insertDetail(d, c);
            }
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

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String processCode) {
        jdbc.update("DELETE FROM LKP_PAY_CODE_DETAILS WHERE PROCESS_CODE = ?", processCode);
        jdbc.update("DELETE FROM LKP_PAY_CODE WHERE PROCESS_CODE = ?", processCode);
    }

    // ── Lookup: ENTITY_LKP ────────────────────────────────────────────────────

    public List<EntityLookup> lookupEntities() {
        return jdbc.query(
                "SELECT ENTITY_CODE, ENTITY_NAME_EN, ENTITY_NAME_AR FROM ENTITY_LKP ORDER BY ENTITY_NAME_EN",
                (rs, i) -> {
                    EntityLookup e = new EntityLookup();
                    e.entityCode    = rs.getString("ENTITY_CODE");
                    e.entityNameEn  = rs.getString("ENTITY_NAME_EN");
                    e.entityNameAr  = rs.getString("ENTITY_NAME_AR");
                    return e;
                });
    }
}
