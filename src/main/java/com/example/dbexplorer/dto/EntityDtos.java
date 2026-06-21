package com.example.dbexplorer.dto;

import java.util.List;

public class EntityDtos {

    public static class EntityDto {
        public Long id;
        public String entityCode;
        public String entityNameEn;
        public String entityNameAr;
        public String entityLogoUrl;
        public String entityStatus;
        public String androidStatus;
        public String iosStatus;
        public String actionType;
        public String actionUrl;
    }

    public static class EntitySummary {
        public Long id;
        public String entityCode;
        public String entityNameEn;
        public String entityNameAr;
        public String entityStatus;
    }

    public static class EntityListResponse {
        public List<EntitySummary> items;
        public int totalCount;
        public int page;
        public int pageSize;
    }
}
