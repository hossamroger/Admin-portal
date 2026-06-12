package com.example.dbexplorer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DonationProjectService {

    private final JdbcTemplate jdbc;
    private final SubResourceService subResources;

    public DonationProjectService(JdbcTemplate jdbc, SubResourceService subResources) {
        this.jdbc = jdbc;
        this.subResources = subResources;
    }

    // ── Sub-resource specs (table/column mapping + validation as data) ─────────

    private static final SubResourceService.SubResourceSpec AMOUNTS =
        new SubResourceService.SubResourceSpec(
            "DA_DONATION_PROJECTS_AMOUNT", "PROJECT_ID", "ID",
            rows -> { for (Map<String,Object> a : rows) validateAmount(a); },
            (input, id, order, parentKey) -> {
                LinkedHashMap<String,Object> m = new LinkedHashMap<>();
                m.put("ID", id);
                m.put("PROJECT_ID", parentKey);
                m.put("AMOUNT", new BigDecimal(input.get("AMOUNT").toString().trim()));
                return m;
            },
            null);

    private static final SubResourceService.SubResourceSpec DETAILS =
        new SubResourceService.SubResourceSpec(
            "DA_DONATION_PROJECTS_DETAILS", "PRJ_ID", "REC_ID",
            rows -> { for (Map<String,Object> d : rows) {
                checkLength(d.get("LABEL_AR"), "LABEL_AR", 1600);
                checkLength(d.get("LABEL_EN"), "LABEL_EN", 1600);
                checkLength(d.get("DESC_AR"),  "DESC_AR",  16000);
                checkLength(d.get("DESC_EN"),  "DESC_EN",  16000);
            } },
            (input, id, order, parentKey) -> {
                LinkedHashMap<String,Object> m = new LinkedHashMap<>();
                m.put("REC_ID", id);
                m.put("PRJ_ID", parentKey);
                m.put("LABEL_AR", nvl(input.get("LABEL_AR")));
                m.put("LABEL_EN", nvl(input.get("LABEL_EN")));
                m.put("DESC_AR",  nvl(input.get("DESC_AR")));
                m.put("DESC_EN",  nvl(input.get("DESC_EN")));
                m.put("ORDER_C", order);
                return m;
            },
            literal("CREATION_DATE", "SYSDATE"));

    // ── Amounts ──────────────────────────────────────────────────────────────

    public List<Map<String,Object>> listAmounts(long projectId) {
        requireProject(projectId);
        return jdbc.queryForList(
            "SELECT ID, AMOUNT FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ? ORDER BY AMOUNT",
            projectId);
    }

    @Transactional
    public void saveAmounts(long projectId, List<Map<String,Object>> amounts) {
        requireProject(projectId);
        subResources.replaceAll(AMOUNTS, projectId, amounts);
    }

    // ── Details ──────────────────────────────────────────────────────────────

    public List<Map<String,Object>> listDetails(long projectId) {
        requireProject(projectId);
        return jdbc.queryForList(
            "SELECT REC_ID, LABEL_AR, LABEL_EN, DESC_AR, DESC_EN, ORDER_C " +
            "FROM DA_DONATION_PROJECTS_DETAILS WHERE PRJ_ID = ? ORDER BY ORDER_C NULLS LAST, REC_ID",
            projectId);
    }

    @Transactional
    public void saveDetails(long projectId, List<Map<String,Object>> details) {
        requireProject(projectId);
        subResources.replaceAll(DETAILS, projectId, details);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void validateAmount(Map<String,Object> a) {
        Object rawAmount = a.get("AMOUNT");
        if (rawAmount == null || rawAmount.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        BigDecimal amount;
        try { amount = new BigDecimal(rawAmount.toString().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid amount: " + rawAmount); }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private static LinkedHashMap<String,String> literal(String col, String sql) {
        LinkedHashMap<String,String> m = new LinkedHashMap<>();
        m.put(col, sql);
        return m;
    }

    private void requireProject(long projectId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM DA_DONATION_PROJECTS WHERE PRJ_ID = ?", Long.class, projectId);
        if (count == null || count == 0) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
    }

    private static void checkLength(Object v, String col, int max) {
        if (v != null && v.toString().length() > max) {
            throw new IllegalArgumentException(col + " exceeds maximum length of " + max);
        }
    }

    private static String nvl(Object v) { return v == null ? null : v.toString(); }
}
