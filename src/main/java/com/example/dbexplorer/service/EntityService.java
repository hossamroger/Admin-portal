package com.example.dbexplorer.service;

import com.example.dbexplorer.dto.EntityDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class EntityService {

    private final JdbcTemplate jdbc;

    public EntityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public EntityListResponse list(String search, int page, int pageSize) {
        String where = "";
        List<Object> args = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            where = " WHERE UPPER(ENTITY_CODE) LIKE ?"
                  + " OR UPPER(ENTITY_NAME_EN) LIKE ?"
                  + " OR UPPER(ENTITY_NAME_AR) LIKE ?";
            String q = "%" + search.toUpperCase() + "%";
            args.add(q); args.add(q); args.add(q);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ENTITY_LKP" + where,
                Long.class, args.toArray());

        String sql = "SELECT ID, ENTITY_CODE, ENTITY_NAME_EN, ENTITY_NAME_AR, ENTITY_STATUS"
                + " FROM ENTITY_LKP" + where
                + " ORDER BY ENTITY_NAME_EN NULLS LAST"
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add(page * pageSize);
        args.add(pageSize);

        List<EntitySummary> items = jdbc.query(sql, args.toArray(), (rs, i) -> {
            EntitySummary s = new EntitySummary();
            s.id           = rs.getLong("ID");
            s.entityCode   = rs.getString("ENTITY_CODE");
            s.entityNameEn = rs.getString("ENTITY_NAME_EN");
            s.entityNameAr = rs.getString("ENTITY_NAME_AR");
            s.entityStatus = rs.getString("ENTITY_STATUS");
            return s;
        });

        EntityListResponse r = new EntityListResponse();
        r.items     = items;
        r.totalCount = total == null ? 0 : total.intValue();
        r.page      = page;
        r.pageSize  = pageSize;
        return r;
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    public EntityDto get(long id) {
        List<EntityDto> rows = jdbc.query(
                "SELECT ID, ENTITY_CODE, ENTITY_NAME_EN, ENTITY_NAME_AR,"
                + " ENTITY_LOGO_URL, ENTITY_STATUS, ANDROID_STATUS, IOS_STATUS,"
                + " ACTION_TYPE, ACTION_URL"
                + " FROM ENTITY_LKP WHERE ID = ?",
                new Object[]{ id },
                (rs, i) -> {
                    EntityDto d = new EntityDto();
                    d.id            = rs.getLong("ID");
                    d.entityCode    = rs.getString("ENTITY_CODE");
                    d.entityNameEn  = rs.getString("ENTITY_NAME_EN");
                    d.entityNameAr  = rs.getString("ENTITY_NAME_AR");
                    d.entityLogoUrl = rs.getString("ENTITY_LOGO_URL");
                    d.entityStatus  = rs.getString("ENTITY_STATUS");
                    String android  = rs.getString("ANDROID_STATUS");
                    d.androidStatus = android != null ? android.trim() : null;
                    String ios      = rs.getString("IOS_STATUS");
                    d.iosStatus     = ios != null ? ios.trim() : null;
                    d.actionType    = rs.getString("ACTION_TYPE");
                    d.actionUrl     = rs.getString("ACTION_URL");
                    return d;
                });
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        return rows.get(0);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public long create(EntityDto dto) {
        requireNoDuplicate(dto.entityCode, null);
        long id = jdbc.queryForObject("SELECT ENTITY_LKP_SEQ.NEXTVAL FROM DUAL", Long.class);
        jdbc.update(
                "INSERT INTO ENTITY_LKP"
                + " (ID, ENTITY_CODE, ENTITY_NAME_EN, ENTITY_NAME_AR,"
                + "  ENTITY_LOGO_URL, ENTITY_STATUS, ANDROID_STATUS, IOS_STATUS,"
                + "  ACTION_TYPE, ACTION_URL)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, dto.entityCode, dto.entityNameEn, dto.entityNameAr,
                dto.entityLogoUrl, dto.entityStatus, dto.androidStatus, dto.iosStatus,
                dto.actionType, dto.actionUrl);
        return id;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update(long id, EntityDto dto) {
        requireNoDuplicate(dto.entityCode, id);
        int rows = jdbc.update(
                "UPDATE ENTITY_LKP SET"
                + " ENTITY_NAME_EN = ?, ENTITY_NAME_AR = ?,"
                + " ENTITY_LOGO_URL = ?, ENTITY_STATUS = ?,"
                + " ANDROID_STATUS = ?, IOS_STATUS = ?,"
                + " ACTION_TYPE = ?, ACTION_URL = ?"
                + " WHERE ID = ?",
                dto.entityNameEn, dto.entityNameAr,
                dto.entityLogoUrl, dto.entityStatus,
                dto.androidStatus, dto.iosStatus,
                dto.actionType, dto.actionUrl, id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void delete(long id) {
        jdbc.update("DELETE FROM ENTITY_LKP WHERE ID = ?", id);
    }

    // ── Duplicate check ───────────────────────────────────────────────────────

    private void requireNoDuplicate(String entityCode, Long excludeId) {
        if (entityCode == null || entityCode.trim().isEmpty()) return;
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM ENTITY_LKP WHERE ENTITY_CODE = ?");
        List<Object> args = new ArrayList<>();
        args.add(entityCode);
        if (excludeId != null) {
            sql.append(" AND ID <> ?");
            args.add(excludeId);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An entity with ENTITY_CODE '" + entityCode + "' already exists.");
        }
    }
}
