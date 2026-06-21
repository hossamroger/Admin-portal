package com.example.dbexplorer.dto;

import java.util.List;

public class PayCodeDtos {

    // ── Lookup ────────────────────────────────────────────────────────────────

    public static class EntityLookup {
        public String entityCode;
        public String entityNameEn;
        public String entityNameAr;
    }

    // ── Master: LKP_PAY_CODE ──────────────────────────────────────────────────

    public static class PayCodeDto {
        public Object id;                       // ID — auto-generated, hidden
        public String entityCode;               // ENTITY_CODE (NOT NULL)
        public String entityDepartmentCode;     // ENTITY_DEPARTMENT_CODE (NOT NULL)
        public String entityServiceCategoryCode;// ENTITY_SERVICE_CATEGORY_CODE (NOT NULL)
        public String entityServiceCode;        // ENTITY_SERVICE_CODE (NOT NULL)
        // QUANTITY is always '1', never sent from UI
        public String processCode;              // PROCESS_CODE
        public String processIdentifier;        // PROCESS_IDENTIFIER — editable
    }

    // ── Child: LKP_PAY_CODE_DETAILS ───────────────────────────────────────────

    public static class PayCodeDetailsDto {
        public Object id;                       // ID — auto-generated, hidden
        // FK columns (set from parent, not from UI):
        public String processCode;              // PROCESS_CODE → LKP_PAY_CODE.PROCESS_CODE
        public String entityServiceCode;        // ENTITY_SERVICE_CODE → LKP_PAY_CODE.ENTITY_SERVICE_CODE
        public String entityServiceCategoryCode;// ENTITY_SERVICE_CATEGORY_CODE → LKP_PAY_CODE.ENTITY_SERVICE_CATEGORY_CODE
        public String payEntityCode;            // PAY_ENTITY_CODE → LKP_PAY_CODE.ENTITY_CODE
        public String payDepartmentCode;        // PAY_DEPARTMENT_CODE → LKP_PAY_CODE.ENTITY_DEPARTMENT_CODE
        // SHOW_TOTAL is always 'F', never sent from UI
        // User-editable fields:
        public String serviceDescAr;            // SERVICE_DESC_AR
        public String serviceDescEn;            // SERVICE_DESC_EN
        public String entityNameAr;             // ENTITY_NAME_AR
        public String entityNameEn;             // ENTITY_NAME_EN
        public String entityCode;               // ENTITY_CODE — dropdown from ENTITY_LKP
    }

    // ── Full payload (request + response) ────────────────────────────────────

    public static class PayCodePayload {
        public PayCodeDto            payCode;
        public List<PayCodeDetailsDto> details;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public static class PayCodeSummary {
        public Object id;
        public String entityCode;
        public String entityServiceCode;
        public String processCode;
        public String processIdentifier;
    }

    public static class PayCodeListResponse {
        public List<PayCodeSummary> items;
        public long   total;
        public int    page;
        public int    pageSize;
    }
}
