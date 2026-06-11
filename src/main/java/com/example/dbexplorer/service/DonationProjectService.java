package com.example.dbexplorer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DonationProjectService {

    private final JdbcTemplate jdbc;

    public DonationProjectService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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

        // Validate the entire payload before touching any rows.
        List<BigDecimal> parsed = new ArrayList<>(amounts.size());
        for (Map<String,Object> a : amounts) {
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
            parsed.add(amount);
        }

        jdbc.update("DELETE FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ?", projectId);
        long base = Optional.ofNullable(jdbc.queryForObject(
            "SELECT NVL(MAX(ID),0) FROM DA_DONATION_PROJECTS_AMOUNT", Long.class)).orElse(0L);
        for (int i = 0; i < parsed.size(); i++) {
            jdbc.update("INSERT INTO DA_DONATION_PROJECTS_AMOUNT (ID, PROJECT_ID, AMOUNT) VALUES (?,?,?)",
                base + i + 1, projectId, parsed.get(i));
        }
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

        // Validate the entire payload before touching any rows.
        for (Map<String,Object> d : details) {
            checkLength(d.get("LABEL_AR"), "LABEL_AR", 1600);
            checkLength(d.get("LABEL_EN"), "LABEL_EN", 1600);
            checkLength(d.get("DESC_AR"),  "DESC_AR",  16000);
            checkLength(d.get("DESC_EN"),  "DESC_EN",  16000);
        }

        jdbc.update("DELETE FROM DA_DONATION_PROJECTS_DETAILS WHERE PRJ_ID = ?", projectId);
        long base = Optional.ofNullable(jdbc.queryForObject(
            "SELECT NVL(MAX(REC_ID),0) FROM DA_DONATION_PROJECTS_DETAILS", Long.class)).orElse(0L);
        int order = 1;
        for (Map<String,Object> d : details) {
            jdbc.update(
                "INSERT INTO DA_DONATION_PROJECTS_DETAILS " +
                "(REC_ID, PRJ_ID, LABEL_AR, LABEL_EN, DESC_AR, DESC_EN, ORDER_C, CREATION_DATE) " +
                "VALUES (?,?,?,?,?,?,?,SYSDATE)",
                base + order, projectId,
                nvl(d.get("LABEL_AR")), nvl(d.get("LABEL_EN")),
                nvl(d.get("DESC_AR")),  nvl(d.get("DESC_EN")),
                order++);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
