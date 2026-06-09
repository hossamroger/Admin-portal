package com.example.dbexplorer.service;

import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProcessStatusService {

    private final JdbcTemplate jdbc;

    public ProcessStatusService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Next ID ───────────────────────────────────────────────────────────────

    public long nextId() {
        Long v = jdbc.queryForObject(
            "SELECT NVL(MAX(ID), 0) + 1 FROM BPM_PROCESS_STATUS", Long.class);
        return v == null ? 1L : v;
    }

    // ── List / search ─────────────────────────────────────────────────────────

    public ProcessStatusListResponse list(String search, int page, int pageSize) {
        String where = buildWhere(search);
        long total = Optional.ofNullable(
            jdbc.queryForObject("SELECT COUNT(*) FROM BPM_PROCESS_STATUS" + where, Long.class)
        ).orElse(0L);

        String sql =
            "SELECT * FROM (" +
            "  SELECT a.*, ROWNUM rnum_ FROM (" +
            "    SELECT * FROM BPM_PROCESS_STATUS" + where + " ORDER BY ID" +
            "  ) a WHERE ROWNUM <= " + ((page + 1) * pageSize) +
            ") WHERE rnum_ > " + (page * pageSize);

        List<ProcessStatusDto> items = jdbc.query(sql, (rs, i) -> mapRow(rs));

        ProcessStatusListResponse r = new ProcessStatusListResponse();
        r.items    = items;
        r.total    = total;
        r.page     = page;
        r.pageSize = pageSize;
        return r;
    }

    private String buildWhere(String search) {
        if (search == null || search.trim().isEmpty()) return "";
        String q = search.trim().toUpperCase().replace("'", "''");
        return " WHERE UPPER(PROCESS_NAME) LIKE '%" + q + "%'" +
               " OR TO_CHAR(PROCESS_CODE) LIKE '%" + q + "%'";
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    public ProcessStatusDto get(long id) {
        List<ProcessStatusDto> rows = jdbc.query(
            "SELECT * FROM BPM_PROCESS_STATUS WHERE ID = ?",
            (rs, i) -> mapRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public void create(ProcessStatusDto dto) {
        long newId = nextId();
        jdbc.update(
            "INSERT INTO BPM_PROCESS_STATUS " +
            "(ID, PROCESS_CODE, PROCESS_NAME, STATUS_CODE, STATUS_ON_WEB, STATUS_ON_IOS, " +
            " STATUS_ON_ANDROID, IOS_VERSION, TIME_TO_BE_AVAILABLE, ANDROID_VERSION, MSG_AR, MSG_EN) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            newId,
            toLong(dto.processCode),
            dto.processName,
            toLong(dto.statusCode),
            toLong(dto.statusOnWeb),
            toLong(dto.statusOnIos),
            toLong(dto.statusOnAndroid),
            dto.iosVersion,
            parseDate(dto.timeToBeAvailable),
            dto.androidVersion,
            dto.msgAr,
            dto.msgEn
        );
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update(long id, ProcessStatusDto dto) {
        jdbc.update(
            "UPDATE BPM_PROCESS_STATUS SET " +
            "PROCESS_CODE = ?, PROCESS_NAME = ?, STATUS_CODE = ?, STATUS_ON_WEB = ?, " +
            "STATUS_ON_IOS = ?, STATUS_ON_ANDROID = ?, IOS_VERSION = ?, " +
            "TIME_TO_BE_AVAILABLE = ?, ANDROID_VERSION = ?, MSG_AR = ?, MSG_EN = ? " +
            "WHERE ID = ?",
            toLong(dto.processCode),
            dto.processName,
            toLong(dto.statusCode),
            toLong(dto.statusOnWeb),
            toLong(dto.statusOnIos),
            toLong(dto.statusOnAndroid),
            dto.iosVersion,
            parseDate(dto.timeToBeAvailable),
            dto.androidVersion,
            dto.msgAr,
            dto.msgEn,
            id
        );
    }

    // ── Lookup: BPM_PROCESS_STATUS_MSG ────────────────────────────────────────

    public List<ProcessStatusMsgDto> lookupMsgs() {
        return jdbc.query(
            "SELECT ID, MESSAGE_AR, MESSAGE_EN, ACTION_LABEL_AR, ACTION_LABEL_EN, URL " +
            "FROM BPM_PROCESS_STATUS_MSG ORDER BY ID",
            (rs, i) -> {
                ProcessStatusMsgDto m = new ProcessStatusMsgDto();
                m.id            = rs.getObject("ID");
                m.messageAr     = rs.getString("MESSAGE_AR");
                m.messageEn     = rs.getString("MESSAGE_EN");
                m.actionLabelAr = rs.getString("ACTION_LABEL_AR");
                m.actionLabelEn = rs.getString("ACTION_LABEL_EN");
                m.url           = rs.getString("URL");
                return m;
            });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProcessStatusDto mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProcessStatusDto d = new ProcessStatusDto();
        d.id              = rs.getObject("ID");
        d.processCode     = rs.getObject("PROCESS_CODE");
        d.processName     = rs.getString("PROCESS_NAME");
        d.statusCode      = rs.getObject("STATUS_CODE");
        d.statusOnWeb     = rs.getObject("STATUS_ON_WEB");
        d.statusOnIos     = rs.getObject("STATUS_ON_IOS");
        d.statusOnAndroid = rs.getObject("STATUS_ON_ANDROID");
        d.iosVersion      = rs.getString("IOS_VERSION");
        Timestamp ts = rs.getTimestamp("TIME_TO_BE_AVAILABLE");
        d.timeToBeAvailable = ts == null ? null :
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(ts);
        d.androidVersion  = rs.getString("ANDROID_VERSION");
        d.msgAr           = rs.getString("MSG_AR");
        d.msgEn           = rs.getString("MSG_EN");
        return d;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    private Timestamp parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(s).getTime());
        } catch (Exception e) {
            try {
                return new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(s).getTime());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
