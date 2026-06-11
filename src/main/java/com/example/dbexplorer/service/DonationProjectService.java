package com.example.dbexplorer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DonationProjectService {

    private final JdbcTemplate jdbc;

    public DonationProjectService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ── Amounts ──────────────────────────────────────────────────────────────

    public List<Map<String,Object>> listAmounts(long projectId) {
        return jdbc.queryForList(
            "SELECT ID, AMOUNT FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ? ORDER BY AMOUNT",
            projectId);
    }

    public void saveAmounts(long projectId, List<Map<String,Object>> amounts) {
        jdbc.update("DELETE FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ?", projectId);
        for (Map<String,Object> a : amounts) {
            Object rawAmount = a.get("AMOUNT");
            if (rawAmount == null) continue;
            java.math.BigDecimal amount;
            try { amount = new java.math.BigDecimal(rawAmount.toString()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid amount: " + rawAmount); }
            long id = Optional.ofNullable(jdbc.queryForObject(
                "SELECT NVL(MAX(ID),0)+1 FROM DA_DONATION_PROJECTS_AMOUNT", Long.class)).orElse(1L);
            jdbc.update("INSERT INTO DA_DONATION_PROJECTS_AMOUNT (ID, PROJECT_ID, AMOUNT) VALUES (?,?,?)",
                id, projectId, amount);
        }
    }

    // ── Details ──────────────────────────────────────────────────────────────

    public List<Map<String,Object>> listDetails(long projectId) {
        return jdbc.queryForList(
            "SELECT REC_ID, LABEL_AR, LABEL_EN, DESC_AR, DESC_EN, ORDER_C " +
            "FROM DA_DONATION_PROJECTS_DETAILS WHERE PRJ_ID = ? ORDER BY ORDER_C NULLS LAST, REC_ID",
            projectId);
    }

    public void saveDetails(long projectId, List<Map<String,Object>> details) {
        jdbc.update("DELETE FROM DA_DONATION_PROJECTS_DETAILS WHERE PRJ_ID = ?", projectId);
        int order = 1;
        for (Map<String,Object> d : details) {
            long id = Optional.ofNullable(jdbc.queryForObject(
                "SELECT NVL(MAX(REC_ID),0)+1 FROM DA_DONATION_PROJECTS_DETAILS", Long.class)).orElse(1L);
            jdbc.update(
                "INSERT INTO DA_DONATION_PROJECTS_DETAILS " +
                "(REC_ID, PRJ_ID, LABEL_AR, LABEL_EN, DESC_AR, DESC_EN, ORDER_C, CREATION_DATE) " +
                "VALUES (?,?,?,?,?,?,?,SYSDATE)",
                id, projectId,
                nvl(d.get("LABEL_AR")), nvl(d.get("LABEL_EN")),
                nvl(d.get("DESC_AR")),  nvl(d.get("DESC_EN")),
                order++);
        }
    }

    private static String nvl(Object v) { return v == null ? null : v.toString(); }
}
