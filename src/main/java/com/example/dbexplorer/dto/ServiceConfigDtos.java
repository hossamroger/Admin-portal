package com.example.dbexplorer.dto;

import java.util.List;
import java.util.Map;

/** DTOs for the Service Configuration feature (BPM_PROCESSES_INFO and its children). */
public class ServiceConfigDtos {

    // ── Lookup (dropdown-only) responses ─────────────────────────────────────

    public static class TargetAudienceLookup {
        public Object seq;
        public String descAr;
        public String descEn;
        public String logoUrl;
    }

    public static class ScreenInfoLookup {
        public Object id;
        public String headerAr;
        public String headerEn;
        public String descAr;
        public String descEn;
        public String icon;
    }

    public static class ComponentInfoLookup {
        public Object id;
        public String labelAr;
        public String labelEn;
        public String action;
        public String iconUrl;
        public Object orderC;
    }

    // ── Child entities (enable_insertion) ────────────────────────────────────

    public static class StepLinkDto {
        public Object  stepLinkId;           // STEP_LINK_ID (PK, generated on insert)
        public String  requiredStatusCode;   // REQUIRED_STATUS_CODE
        public Object  requiredStepId;       // REQUIRED_STEP_ID (FK to BPM_LKP_STEPS)
        public String  webLinkUrl;           // WEB_LINK_URL
        public String  mobLinkFlag;          // MOB_LINK_FLAG
        public String  bpmnProcessName;      // BPMN_PROCESS_NAME
        public String  actionCode;           // ACTION_CODE
        public String  iconUrl;              // ICON_URL
        public String  labelAr;              // LABEL_AR
        public String  labelEn;              // LABEL_EN
        public String  iosActionCode;        // IOS_ACTION_CODE
        public String  androidActionCode;    // ANDROID_ACTION_CODE
        public String  platformCode;         // PLATFORM_CODE
    }

    public static class StepDto {
        public String  bpmProStepsSeq;       // BPM_PRO_STEPS_SEQ (PK)
        public String  requiredStepId;       // REQUIRED_STEP_ID (NOT NULL)
        public String  requiredStepDescAr;   // REQUIRED_STEP_DESC_AR (NOT NULL)
        public String  requiredStepDescEn;   // REQUIRED_STEP_DESC_EN (NOT NULL)
        public Object  orderC;              // ORDER_C
        public String  stepType;             // STEP_TYPE
        public String  bpmnProcessName;      // BPMN_PROCESS_NAME
        public String  linkDescAr;           // LINK_DESC_AR
        public String  linkDescEn;           // LINK_DESC_EN
        public String  attentionMsgAr;       // ATTENTION_MSG_AR
        public String  attentionMsgEn;       // ATTENTION_MSG_EN
        public Object  stepSla;             // STEP_SLA
        public String  showLinkFlag;         // SHOW_LINK_FLAG
        public String  entityCode;           // ENTITY_CODE
        public List<StepLinkDto> statusLinks;
    }

    public static class FeeDto {
        public String  bpmProcessesFeesSeq; // BPM_PROCESSES_FEES_SEQ (PK)
        public String  feesDescAr;          // FEES_DESC_AR (NOT NULL)
        public String  feesDescEn;          // FEES_DESC_EN
        public String  feesAmount;          // FEES_AMOUNT
        public String  currencyAr;          // CURRENCY_AR
        public String  currencyEn;          // CURRENCY_EN
        public Object  orderC;             // ORDER_C
    }

    public static class RequiredDocDto {
        public String  bpmProRequiredDocsSeq; // BPM_PRO_REQUIRED_DOCS_SEQ (PK)
        public String  requiredDocsDescAr;    // REQUIRED_DOCS_DESC_AR (NOT NULL)
        public String  requiredDocsDescEn;    // REQUIRED_DOCS_DESC_EN (NOT NULL)
        public Object  orderC;               // ORDER_C
    }

    public static class RelatedDeptDto {
        public String  bpmProRelatedDeptsSeq;        // BPM_PRO_RELATED_DEPTS_SEQ (PK)
        public String  relatedDepartmentsDescAr;      // RELATED_DEPARTMENTS_DESC_AR (NOT NULL)
        public String  relatedDepartmentsDescEn;      // RELATED_DEPARTMENTS_DESC_EN (NOT NULL)
        public Object  orderC;                        // ORDER_C
        public String  entityCode;                    // ENTITY_CODE
        public String  entityCodeSrc;                 // ENTITY_CODE_SRC
    }

    public static class TargetAudienceDto {
        public String  bpmProTargetAudienceSeq;  // BPM_PRO_TARGET_AUDIENCE_SEQ (PK)
        public String  targetAudienceDescAr;     // TARGET_AUDIENCE_DESC_AR (NOT NULL)
        public String  targetAudienceDescEn;     // TARGET_AUDIENCE_DESC_EN (NOT NULL)
        public Object  orderC;                  // ORDER_C
        public Object  mainTargetAudienceSeq;   // MAIN_TARGET_AUDIENCE_SEQ (FK dropdown)
    }

    public static class ConfirmationComponentConfigDto {
        public Object dsConfirmationConfigId;         // DS_CONFIRMATION_CONFIG_ID (PK)
        public Object dsConfirmationComponentInfoId;  // FK → DS_CONFIRMATION_COMPONENT_INFO
    }

    public static class PaymentCallbackDto {
        public Object  id;                // ID (PK, generated on insert)
        public String  url;               // URL
        public String  status;            // STATUS (NOT NULL)
        public Object  paymentStepOrder;  // PAYMENT_STEP_ORDER (NOT NULL)
        public String  sendNotification;  // SEND_NOTIFICATION (NOT NULL)
    }

    public static class ConfirmationScreenConfigDto {
        public Object  dsConfirmationId;          // DS_CONFIRMATION_ID (PK)
        public Object  dsConfirmationScreenInfoId; // FK → DS_CONFIRMATION_SCREEN_INFO (dropdown)
        public String  requiredStepId;            // REQUIRED_STEP_ID
        public String  requiredStatusCode;        // REQUIRED_STATUS_CODE
        public String  hasPayment;               // HAS_PAYMENT
        public String  hasRtRecord;              // HAS_RT_RECORD
        public String  serviceCode;              // SERVICE_CODE
        public String  showRefNo;                // SHOW_REF_NO
        public String  guest;                    // GUEST
        public String  action;                   // ACTION
        public List<ConfirmationComponentConfigDto> components;
    }

    // ── Master service record ─────────────────────────────────────────────────

    public static class ProcessInfoDto {
        public String processCode;
        public String bpmnProcessName;
        public String processDisplayLabelAr;
        public String processDisplayLabelEn;
        public String processStatus;
        public String processInitiateSecurity;
        public Object processOrder;
        public String sendMail;
        public String sendSms;
        public String showOnInbox;
        public String inboxCustomColumn1;
        public String inboxCustomColumn1Ar;
        public String inboxCustomColumn2;
        public String inboxCustomColumn2Ar;
        public String inboxCustomColumn3;
        public String inboxCustomColumn3Ar;
        public String inboxCustomColumn4;
        public String inboxCustomColumn4Ar;
        public String inboxCustomColumn5;
        public String inboxCustomColumn5Ar;
        public String processType;
        public String processDescAr;
        public String processDescEn;
        public String processTimeAr;
        public String processTimeEn;
        public String bpmProServiceCatalog;
        public String serviceIcon;
        public String serviceId;
        public String serviceUnicodeIcon;
        public String directAccess;
        public String allowTerms;
        public String allowRating;
        public String cardRedirectionRequired;
        public String walletRequireOtp;
        public String askBeforePayWallet;
        public String testFlag;
        public Object surveyEntityId;
        public Object surveyServiceId;
        public String actionType;
        public String actionUrl;
        public String preAuth;
        public String requireOtp;
        public String iosShow;
        public String androidShow;
        public String webShow;
        public String testUserShow;
        public String releaseUserShow;
        public String activityProcessActionType;
        public String activityProcessActionUrl;
        public String internalActivityActionType;
        public String internalActivityActionUrl;
        public String groupCode;
        public String actionUrl2;
        public String adminPayment;
        public String iosActivityProcessActionType;
        public String iosInternalActivityActionType;
        public String androidActivityProcessActionType;
        public String androidInternalActivityActionType;
        public String iosActionType;
        public String androidActionType;
        public String platformCode;
        public String platformIcpUserId;
        public String isServiceCatalogOneTime;
    }

    // ── Full service request/response ─────────────────────────────────────────

    public static class ServiceConfigRequest {
        public ProcessInfoDto            processInfo;
        public List<StepDto>             steps;
        public List<FeeDto>              fees;
        public List<RequiredDocDto>      requiredDocs;
        public List<RelatedDeptDto>      relatedDepts;
        public List<TargetAudienceDto>   targetAudiences;
        public List<ConfirmationScreenConfigDto> confirmationScreens;
        public List<PaymentCallbackDto>  paymentCallbacks;
    }

    public static class ServiceConfigResponse extends ServiceConfigRequest {
        // Inherits all request fields; response adds nothing extra
    }

    // ── List item (for the search/paginate list view) ─────────────────────────

    public static class ServiceSummary {
        public String processCode;
        public String bpmnProcessName;
        public String processDisplayLabelAr;
        public String processDisplayLabelEn;
        public String processStatus;
        public String processType;
        public Object processOrder;
    }

    public static class ServiceListResponse {
        public List<ServiceSummary> items;
        public long total;
        public int  page;
        public int  pageSize;
    }
}
