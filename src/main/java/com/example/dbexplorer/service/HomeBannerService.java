package com.example.dbexplorer.service;

import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

@Service
public class HomeBannerService {

    private final JdbcTemplate jdbc;

    public HomeBannerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Next ID / Order ───────────────────────────────────────────────────────

    public long nextId() {
        Long v = jdbc.queryForObject(
            "SELECT NVL(MAX(ID), 0) + 1 FROM DS_HOME_BANNER_CONFIG", Long.class);
        return v == null ? 1L : v;
    }

    public long nextBannerOrder() {
        Long v = jdbc.queryForObject(
            "SELECT NVL(MAX(BANNER_ORDER), 0) + 1 FROM DS_HOME_BANNER_CONFIG", Long.class);
        return v == null ? 1L : v;
    }

    // ── List / search ─────────────────────────────────────────────────────────

    public HomeBannerListResponse list(String search, String platform, int page, int pageSize) {
        String where = buildWhere(search, platform);
        long total = Optional.ofNullable(
            jdbc.queryForObject("SELECT COUNT(*) FROM DS_HOME_BANNER_CONFIG" + where, Long.class)
        ).orElse(0L);

        String sql =
            "SELECT * FROM (" +
            "  SELECT a.*, ROWNUM rnum_ FROM (" +
            "    SELECT * FROM DS_HOME_BANNER_CONFIG" + where + " ORDER BY BANNER_ORDER NULLS LAST, ID" +
            "  ) a WHERE ROWNUM <= " + ((page + 1) * pageSize) +
            ") WHERE rnum_ > " + (page * pageSize);

        List<HomeBannerDto> items = jdbc.query(sql, (rs, i) -> mapRow(rs));

        HomeBannerListResponse r = new HomeBannerListResponse();
        r.items    = items;
        r.total    = total;
        r.page     = page;
        r.pageSize = pageSize;
        return r;
    }

    private String buildWhere(String search, String platform) {
        StringBuilder sb = new StringBuilder();
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toUpperCase().replace("'", "''");
            sb.append("(UPPER(URL) LIKE '%").append(q).append("%'")
              .append(" OR UPPER(URL_SM) LIKE '%").append(q).append("%')");
        }
        if (platform != null && !platform.trim().isEmpty()) {
            String p = platform.trim().toUpperCase().replace("'", "''");
            if (sb.length() > 0) sb.append(" AND ");
            sb.append("UPPER(PLATFORM) = '").append(p).append("'");
        }
        return sb.length() == 0 ? "" : " WHERE " + sb;
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    public HomeBannerDto get(long id) {
        List<HomeBannerDto> rows = jdbc.query(
            "SELECT * FROM DS_HOME_BANNER_CONFIG WHERE ID = ?",
            (rs, i) -> mapRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public void create(HomeBannerDto dto) {
        long newId    = nextId();
        long newOrder = dto.bannerOrder != null ? toLong(dto.bannerOrder) : nextBannerOrder();
        jdbc.update(
            "INSERT INTO DS_HOME_BANNER_CONFIG " +
            "(ID, URL, PLATFORM, LANGUAGE, START_DT, EXPIRY_DT, FORE_COLOR, BG_COLOR, " +
            " HAS_ACTION, ACTION_TYPE, ACTION_CODE, ACTION_URL, BANNER_ORDER, IS_ACTIVE, " +
            " CREATED_AT, UPDATED_AT, URL_SM, IS_HEADLINE, EXTENSION_TYPE, CATALOG_ID, " +
            " MAIN_TITLE_COLOR, IS_DARK_MODE, MIN_VERSION) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP, SYSTIMESTAMP, " +
            "        ?, ?, ?, ?, ?, ?, ?)",
            newId,
            dto.url, dto.platform, dto.language,
            parseDate(dto.startDt), parseDate(dto.expiryDt),
            dto.foreColor, dto.bgColor,
            toLong(dto.hasAction), dto.actionType, dto.actionCode, dto.actionUrl,
            newOrder, toLong(dto.isActive),
            dto.urlSm, toLong(dto.isHeadline), dto.extensionType,
            dto.catalogId, dto.mainTitleColor, toLong(dto.isDarkMode), dto.minVersion
        );
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update(long id, HomeBannerDto dto) {
        jdbc.update(
            "UPDATE DS_HOME_BANNER_CONFIG SET " +
            "URL = ?, PLATFORM = ?, LANGUAGE = ?, START_DT = ?, EXPIRY_DT = ?, " +
            "FORE_COLOR = ?, BG_COLOR = ?, HAS_ACTION = ?, ACTION_TYPE = ?, ACTION_CODE = ?, " +
            "ACTION_URL = ?, BANNER_ORDER = ?, IS_ACTIVE = ?, UPDATED_AT = SYSTIMESTAMP, " +
            "URL_SM = ?, IS_HEADLINE = ?, EXTENSION_TYPE = ?, CATALOG_ID = ?, " +
            "MAIN_TITLE_COLOR = ?, IS_DARK_MODE = ?, MIN_VERSION = ? " +
            "WHERE ID = ?",
            dto.url, dto.platform, dto.language,
            parseDate(dto.startDt), parseDate(dto.expiryDt),
            dto.foreColor, dto.bgColor,
            toLong(dto.hasAction), dto.actionType, dto.actionCode, dto.actionUrl,
            toLong(dto.bannerOrder), toLong(dto.isActive),
            dto.urlSm, toLong(dto.isHeadline), dto.extensionType,
            dto.catalogId, dto.mainTitleColor, toLong(dto.isDarkMode), dto.minVersion,
            id
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HomeBannerDto mapRow(ResultSet rs) throws SQLException {
        HomeBannerDto d = new HomeBannerDto();
        d.id           = rs.getObject("ID");
        d.url          = rs.getString("URL");
        d.platform     = rs.getString("PLATFORM");
        d.language     = rs.getString("LANGUAGE");
        d.startDt      = formatDate(rs.getTimestamp("START_DT"));
        d.expiryDt     = formatDate(rs.getTimestamp("EXPIRY_DT"));
        d.foreColor    = rs.getString("FORE_COLOR");
        d.bgColor      = rs.getString("BG_COLOR");
        d.hasAction    = rs.getObject("HAS_ACTION");
        d.actionType   = rs.getString("ACTION_TYPE");
        d.actionCode   = rs.getString("ACTION_CODE");
        d.actionUrl    = rs.getString("ACTION_URL");
        d.bannerOrder  = rs.getObject("BANNER_ORDER");
        d.isActive     = rs.getObject("IS_ACTIVE");
        d.createdAt    = formatTimestamp(rs.getTimestamp("CREATED_AT"));
        d.updatedAt    = formatTimestamp(rs.getTimestamp("UPDATED_AT"));
        d.urlSm        = rs.getString("URL_SM");
        d.isHeadline   = rs.getObject("IS_HEADLINE");
        d.extensionType = rs.getString("EXTENSION_TYPE");
        d.catalogId    = rs.getString("CATALOG_ID");
        d.mainTitleColor = rs.getString("MAIN_TITLE_COLOR");
        d.isDarkMode   = rs.getObject("IS_DARK_MODE");
        d.minVersion   = rs.getString("MIN_VERSION");
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
        try { return new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(s).getTime()); }
        catch (Exception e) { return null; }
    }

    private String formatDate(Timestamp ts) {
        return ts == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(ts);
    }

    private String formatTimestamp(Timestamp ts) {
        return ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
    }
}
