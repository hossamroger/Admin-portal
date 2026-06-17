package com.example.dbexplorer.service;

import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ServiceConfigService {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final SubResourceService subResources;

    public ServiceConfigService(JdbcTemplate jdbc, SubResourceService subResources) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc.getDataSource());
        this.subResources = subResources;
    }

    // ── List / search ─────────────────────────────────────────────────────────

    public long nextProcessOrder() {
        Long max = jdbc.queryForObject(
            "SELECT NVL(MAX(PROCESS_ORDER), 0) + 1 FROM BPM_PROCESSES_INFO", Long.class);
        return max == null ? 1L : max;
    }

    public ServiceListResponse list(String search, String status, String type, int page, int pageSize) {
        String baseWhere = buildListWhere(search, status, type);
        String countSql  = "SELECT COUNT(*) FROM BPM_PROCESSES_INFO" + baseWhere;
        String dataSql   =
            "SELECT * FROM (" +
            "  SELECT a.*, ROWNUM rnum_ FROM (" +
            "    SELECT PROCESS_CODE, BPMN_PROCESS_NAME, PROCESS_DISPLAY_LABEL_AR, " +
            "           PROCESS_DISPLAY_LABEL_EN, PROCESS_STATUS, PROCESS_TYPE, PROCESS_ORDER " +
            "    FROM BPM_PROCESSES_INFO" + baseWhere + " ORDER BY PROCESS_ORDER NULLS LAST, PROCESS_CODE" +
            "  ) a WHERE ROWNUM <= " + ((page + 1) * pageSize) +
            ") WHERE rnum_ > " + (page * pageSize);

        long total = Optional.ofNullable(jdbc.queryForObject(countSql, Long.class)).orElse(0L);
        List<ServiceSummary> items = jdbc.query(dataSql, (rs, i) -> {
            ServiceSummary s = new ServiceSummary();
            s.processCode             = rs.getString("PROCESS_CODE");
            s.bpmnProcessName         = rs.getString("BPMN_PROCESS_NAME");
            s.processDisplayLabelAr   = rs.getString("PROCESS_DISPLAY_LABEL_AR");
            s.processDisplayLabelEn   = rs.getString("PROCESS_DISPLAY_LABEL_EN");
            s.processStatus           = rs.getString("PROCESS_STATUS");
            s.processType             = rs.getString("PROCESS_TYPE");
            s.processOrder            = rs.getObject("PROCESS_ORDER");
            return s;
        });

        ServiceListResponse r = new ServiceListResponse();
        r.items    = items;
        r.total    = total;
        r.page     = page;
        r.pageSize = pageSize;
        return r;
    }

    private String buildListWhere(String search, String status, String type) {
        List<String> conds = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toUpperCase().replace("'", "''");
            conds.add("(UPPER(PROCESS_CODE) LIKE '%" + q + "%' OR " +
                      " UPPER(PROCESS_DISPLAY_LABEL_EN) LIKE '%" + q + "%' OR " +
                      " UPPER(PROCESS_DISPLAY_LABEL_AR) LIKE '%" + q + "%')");
        }
        if (status != null && !status.trim().isEmpty())
            conds.add("UPPER(PROCESS_STATUS) = '" + status.trim().toUpperCase().replace("'", "''") + "'");
        if (type != null && !type.trim().isEmpty())
            conds.add("UPPER(PROCESS_TYPE) = '" + type.trim().toUpperCase().replace("'", "''") + "'");
        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
    }

    // ── Get full service ──────────────────────────────────────────────────────

    public ServiceConfigResponse get(String processCode) {
        String code = sanitize(processCode);

        ProcessInfoDto info = jdbc.queryForObject(
            "SELECT * FROM BPM_PROCESSES_INFO WHERE PROCESS_CODE = ?",
            (rs, i) -> mapProcessInfo(rs), code);

        if (info == null) return null;

        ServiceConfigResponse r = new ServiceConfigResponse();
        r.processInfo       = info;
        r.steps             = fetchSteps(code);
        r.fees              = fetchFees(code);
        r.requiredDocs      = fetchDocs(code);
        r.relatedDepts      = fetchDepts(code);
        r.targetAudiences   = fetchAudiences(code);
        r.confirmationScreens = fetchConfirmationScreens(code);
        r.paymentCallbacks  = fetchPaymentCallbacks(code);
        return r;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public void create(ServiceConfigRequest req) {
        insertProcessInfo(req.processInfo);
        String code = req.processInfo.processCode;
        if (req.steps            != null) for (StepDto s : req.steps)                      insertStep(code, s);
        if (req.fees             != null) for (FeeDto f : req.fees)                         insertFee(code, f);
        if (req.requiredDocs     != null) for (RequiredDocDto d : req.requiredDocs)          insertDoc(code, d);
        if (req.relatedDepts     != null) for (RelatedDeptDto d : req.relatedDepts)          insertDept(code, d);
        if (req.targetAudiences  != null) for (TargetAudienceDto a : req.targetAudiences)    insertAudience(code, a);
        if (req.confirmationScreens != null) for (ConfirmationScreenConfigDto c : req.confirmationScreens) insertConfirmationScreen(code, c);
        if (req.paymentCallbacks != null) for (PaymentCallbackDto pc : req.paymentCallbacks)               insertPaymentCallback(pc);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public void update(String processCode, ServiceConfigRequest req) {
        updateProcessInfo(processCode, req.processInfo);
        if (req.steps            != null) replaceSteps(processCode, req.steps);
        if (req.fees             != null) replaceFees(processCode, req.fees);
        if (req.requiredDocs     != null) replaceDocs(processCode, req.requiredDocs);
        if (req.relatedDepts     != null) replaceDepts(processCode, req.relatedDepts);
        if (req.targetAudiences  != null) replaceAudiences(processCode, req.targetAudiences);
        if (req.confirmationScreens != null) replaceConfirmationScreens(processCode, req.confirmationScreens);
        if (req.paymentCallbacks != null) replacePaymentCallbacks(processCode, req.paymentCallbacks);
    }

    // Per-tab partial saves
    @Transactional public void saveSteps(String code, List<StepDto> steps)                           { replaceSteps(code, steps); }
    @Transactional public void saveFees(String code, List<FeeDto> fees)                              { replaceFees(code, fees); }
    @Transactional public void saveDocs(String code, List<RequiredDocDto> docs)                      { replaceDocs(code, docs); }
    @Transactional public void saveDepts(String code, List<RelatedDeptDto> depts)                    { replaceDepts(code, depts); }
    @Transactional public void saveAudiences(String code, List<TargetAudienceDto> audiences)         { replaceAudiences(code, audiences); }
    @Transactional public void saveConfirmation(String code, List<ConfirmationScreenConfigDto> cfgs) { replaceConfirmationScreens(code, cfgs); }
    @Transactional public void savePaymentCallbacks(String code, List<PaymentCallbackDto> callbacks)  { replacePaymentCallbacks(code, callbacks); }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String processCode) {
        replacePaymentCallbacks(processCode, Collections.emptyList());
        replaceConfirmationScreens(processCode, Collections.emptyList());
        replaceSteps(processCode, Collections.emptyList());
        replaceFees(processCode, Collections.emptyList());
        replaceDocs(processCode, Collections.emptyList());
        replaceDepts(processCode, Collections.emptyList());
        replaceAudiences(processCode, Collections.emptyList());
        jdbc.update("DELETE FROM BPM_PROCESSES_INFO WHERE PROCESS_CODE = ?", processCode);
    }

    // ── Lookup endpoints ──────────────────────────────────────────────────────

    public List<TargetAudienceLookup> lookupTargetAudience() {
        return jdbc.query(
            "SELECT TARGET_AUDIENCE_SEQ, TARGET_DESC_AR, TARGET_DESC_EN, TARGET_AUDIENCE_LOGO_URL " +
            "FROM MAIN_LKP_TARGET_AUDIENCE ORDER BY ORDER_C NULLS LAST, TARGET_AUDIENCE_SEQ",
            (rs, i) -> {
                TargetAudienceLookup l = new TargetAudienceLookup();
                l.seq     = rs.getObject("TARGET_AUDIENCE_SEQ");
                l.descAr  = rs.getString("TARGET_DESC_AR");
                l.descEn  = rs.getString("TARGET_DESC_EN");
                l.logoUrl = rs.getString("TARGET_AUDIENCE_LOGO_URL");
                return l;
            });
    }

    public List<ScreenInfoLookup> lookupScreenInfo() {
        return jdbc.query(
            "SELECT DS_CONFIRMATION_SCREEN_INFO_ID, HEADER_AR, HEADER_EN, DESC_AR, DESC_EN, ICON " +
            "FROM DS_CONFIRMATION_SCREEN_INFO ORDER BY DS_CONFIRMATION_SCREEN_INFO_ID",
            (rs, i) -> {
                ScreenInfoLookup l = new ScreenInfoLookup();
                l.id       = rs.getObject("DS_CONFIRMATION_SCREEN_INFO_ID");
                l.headerAr = rs.getString("HEADER_AR");
                l.headerEn = rs.getString("HEADER_EN");
                l.descAr   = rs.getString("DESC_AR");
                l.descEn   = rs.getString("DESC_EN");
                l.icon     = rs.getString("ICON");
                return l;
            });
    }

    public List<ComponentInfoLookup> lookupComponents() {
        return jdbc.query(
            "SELECT DS_CONFIRMATION_COMPONENT_INFO_ID, LABEL_AR, LABEL_EN, ACTION, ICON_URL, ORDER_C " +
            "FROM DS_CONFIRMATION_COMPONENT_INFO ORDER BY ORDER_C NULLS LAST, DS_CONFIRMATION_COMPONENT_INFO_ID",
            (rs, i) -> {
                ComponentInfoLookup l = new ComponentInfoLookup();
                l.id      = rs.getObject("DS_CONFIRMATION_COMPONENT_INFO_ID");
                l.labelAr = rs.getString("LABEL_AR");
                l.labelEn = rs.getString("LABEL_EN");
                l.action  = rs.getString("ACTION");
                l.iconUrl = rs.getString("ICON_URL");
                l.orderC  = rs.getObject("ORDER_C");
                return l;
            });
    }

    public List<String> listDistinctStatuses() {
        return jdbc.queryForList(
            "SELECT DISTINCT PROCESS_STATUS FROM BPM_PROCESSES_INFO WHERE PROCESS_STATUS IS NOT NULL ORDER BY PROCESS_STATUS",
            String.class);
    }

    public List<String> listDistinctTypes() {
        return jdbc.queryForList(
            "SELECT DISTINCT PROCESS_TYPE FROM BPM_PROCESSES_INFO WHERE PROCESS_TYPE IS NOT NULL AND UPPER(PROCESS_TYPE) != 'USER LEVEL' ORDER BY PROCESS_TYPE",
            String.class);
    }

    // ── Fetch helpers ─────────────────────────────────────────────────────────

    private List<StepDto> fetchSteps(String code) {
        List<StepDto> steps = jdbc.query(
            "SELECT * FROM BPM_LKP_STEPS WHERE PROCESS_CODE = ? ORDER BY ORDER_C NULLS LAST, BPM_PRO_STEPS_SEQ",
            (rs, i) -> {
                StepDto s = new StepDto();
                s.bpmProStepsSeq      = rs.getString("BPM_PRO_STEPS_SEQ");
                s.requiredStepId      = rs.getString("REQUIRED_STEP_ID");
                s.requiredStepDescAr  = rs.getString("REQUIRED_STEP_DESC_AR");
                s.requiredStepDescEn  = rs.getString("REQUIRED_STEP_DESC_EN");
                s.orderC             = rs.getObject("ORDER_C");
                s.stepType           = rs.getString("STEP_TYPE");
                s.bpmnProcessName    = rs.getString("BPMN_PROCESS_NAME");
                s.linkDescAr         = rs.getString("LINK_DESC_AR");
                s.linkDescEn         = rs.getString("LINK_DESC_EN");
                s.attentionMsgAr     = rs.getString("ATTENTION_MSG_AR");
                s.attentionMsgEn     = rs.getString("ATTENTION_MSG_EN");
                s.stepSla            = rs.getObject("STEP_SLA");
                s.showLinkFlag       = rs.getString("SHOW_LINK_FLAG");
                s.entityCode         = rs.getString("ENTITY_CODE");
                s.statusLinks        = new ArrayList<>();
                return s;
            }, code);

        // Fetch status links for all steps in one query, then distribute
        if (!steps.isEmpty()) {
            List<String> seqs = steps.stream().map(s -> s.bpmProStepsSeq).filter(Objects::nonNull).collect(Collectors.toList());
            Map<String, List<StepLinkDto>> linksBySeq = new HashMap<>();
            if (!seqs.isEmpty()) {
                String inClause = seqs.stream().map(s -> "'" + s.replace("'", "''") + "'").collect(Collectors.joining(","));
                jdbc.query(
                    "SELECT L.*, S.BPM_PRO_STEPS_SEQ AS PARENT_STEP_SEQ FROM BPM_LKP_SERV_STEP_STATUS_LINKS L " +
                    "JOIN BPM_LKP_STEPS S ON S.REQUIRED_STEP_ID = TO_CHAR(L.REQUIRED_STEP_ID) " +
                    "  AND S.BPMN_PROCESS_NAME = L.BPMN_PROCESS_NAME " +
                    "  AND S.PROCESS_CODE = ? " +
                    "WHERE S.BPM_PRO_STEPS_SEQ IN (" + inClause + ")",
                    rs -> {
                        StepLinkDto l = new StepLinkDto();
                        l.stepLinkId       = rs.getObject("STEP_LINK_ID");
                        l.requiredStatusCode = rs.getString("REQUIRED_STATUS_CODE");
                        l.requiredStepId    = rs.getObject("REQUIRED_STEP_ID");
                        l.webLinkUrl        = rs.getString("WEB_LINK_URL");
                        l.mobLinkFlag       = rs.getString("MOB_LINK_FLAG");
                        l.bpmnProcessName   = rs.getString("BPMN_PROCESS_NAME");
                        l.actionCode        = rs.getString("ACTION_CODE");
                        l.iconUrl           = rs.getString("ICON_URL");
                        l.labelAr           = rs.getString("LABEL_AR");
                        l.labelEn           = rs.getString("LABEL_EN");
                        l.iosActionCode     = rs.getString("IOS_ACTION_CODE");
                        l.androidActionCode = rs.getString("ANDROID_ACTION_CODE");
                        l.platformCode      = rs.getString("PLATFORM_CODE");
                        String parentSeq = rs.getString("PARENT_STEP_SEQ");
                        linksBySeq.computeIfAbsent(parentSeq, k -> new ArrayList<>()).add(l);
                    }, code);
            }
            steps.forEach(s -> s.statusLinks = linksBySeq.getOrDefault(s.bpmProStepsSeq, Collections.emptyList()));
        }
        return steps;
    }

    private List<FeeDto> fetchFees(String code) {
        return jdbc.query(
            "SELECT * FROM BPM_LKP_FEES WHERE PROCESS_CODE = ? ORDER BY ORDER_C NULLS LAST, BPM_PROCESSES_FEES_SEQ",
            (rs, i) -> {
                FeeDto f = new FeeDto();
                f.bpmProcessesFeesSeq = rs.getString("BPM_PROCESSES_FEES_SEQ");
                f.feesDescAr          = rs.getString("FEES_DESC_AR");
                f.feesDescEn          = rs.getString("FEES_DESC_EN");
                f.feesAmount          = rs.getString("FEES_AMOUNT");
                f.currencyAr          = rs.getString("CURRENCY_AR");
                f.currencyEn          = rs.getString("CURRENCY_EN");
                f.orderC             = rs.getObject("ORDER_C");
                return f;
            }, code);
    }

    private List<PaymentCallbackDto> fetchPaymentCallbacks(String code) {
        // SERVICE_CODE → BPM_LKP_STEPS.BPMN_PROCESS_NAME; fetch via the steps for this process
        return jdbc.query(
            "SELECT * FROM DS_PAYMENT_CONFIRMATION_CONFIG " +
            "WHERE SERVICE_CODE IN (SELECT DISTINCT BPMN_PROCESS_NAME FROM BPM_LKP_STEPS WHERE PROCESS_CODE = ?) " +
            "ORDER BY PAYMENT_STEP_ORDER NULLS LAST, ID",
            (rs, i) -> {
                PaymentCallbackDto pc = new PaymentCallbackDto();
                pc.id               = rs.getObject("ID");
                pc.serviceCode      = rs.getString("SERVICE_CODE");
                pc.url              = rs.getString("URL");
                pc.status           = rs.getString("STATUS");
                pc.paymentStepOrder = rs.getObject("PAYMENT_STEP_ORDER");
                pc.sendNotification = rs.getString("SEND_NOTIFICATION");
                return pc;
            }, code);
    }

    private List<RequiredDocDto> fetchDocs(String code) {
        return jdbc.query(
            "SELECT * FROM BPM_LKP_REQUIRED_DOCS WHERE PROCESS_CODE = ? ORDER BY ORDER_C NULLS LAST, BPM_PRO_REQUIRED_DOCS_SEQ",
            (rs, i) -> {
                RequiredDocDto d = new RequiredDocDto();
                d.bpmProRequiredDocsSeq = rs.getString("BPM_PRO_REQUIRED_DOCS_SEQ");
                d.requiredDocsDescAr    = rs.getString("REQUIRED_DOCS_DESC_AR");
                d.requiredDocsDescEn    = rs.getString("REQUIRED_DOCS_DESC_EN");
                d.orderC               = rs.getObject("ORDER_C");
                return d;
            }, code);
    }

    private List<RelatedDeptDto> fetchDepts(String code) {
        return jdbc.query(
            "SELECT * FROM BPM_LKP_RELATED_DEPTS WHERE PROCESS_CODE = ? ORDER BY ORDER_C NULLS LAST, BPM_PRO_RELATED_DEPTS_SEQ",
            (rs, i) -> {
                RelatedDeptDto d = new RelatedDeptDto();
                d.bpmProRelatedDeptsSeq       = rs.getString("BPM_PRO_RELATED_DEPTS_SEQ");
                d.relatedDepartmentsDescAr    = rs.getString("RELATED_DEPARTMENTS_DESC_AR");
                d.relatedDepartmentsDescEn    = rs.getString("RELATED_DEPARTMENTS_DESC_EN");
                d.orderC                      = rs.getObject("ORDER_C");
                d.entityCode                  = rs.getString("ENTITY_CODE");
                d.entityCodeSrc               = rs.getString("ENTITY_CODE_SRC");
                return d;
            }, code);
    }

    private List<TargetAudienceDto> fetchAudiences(String code) {
        return jdbc.query(
            "SELECT * FROM BPM_LKP_TARGET_AUDIENCE WHERE PROCESS_CODE = ? ORDER BY ORDER_C NULLS LAST, BPM_PRO_TARGET_AUDIENCE_SEQ",
            (rs, i) -> {
                TargetAudienceDto a = new TargetAudienceDto();
                a.bpmProTargetAudienceSeq = rs.getString("BPM_PRO_TARGET_AUDIENCE_SEQ");
                a.targetAudienceDescAr    = rs.getString("TARGET_AUDIENCE_DESC_AR");
                a.targetAudienceDescEn    = rs.getString("TARGET_AUDIENCE_DESC_EN");
                a.orderC                 = rs.getObject("ORDER_C");
                a.mainTargetAudienceSeq  = rs.getObject("MAIN_TARGET_AUDIENCE_SEQ");
                return a;
            }, code);
    }

    private List<ConfirmationScreenConfigDto> fetchConfirmationScreens(String code) {
        List<ConfirmationScreenConfigDto> screens = jdbc.query(
            "SELECT * FROM DS_CONFIRMATION_SCREEN_CONFIG WHERE SERVICE_CODE = ? ORDER BY DS_CONFIRMATION_ID",
            (rs, i) -> {
                ConfirmationScreenConfigDto c = new ConfirmationScreenConfigDto();
                c.dsConfirmationId          = rs.getObject("DS_CONFIRMATION_ID");
                c.dsConfirmationScreenInfoId = rs.getObject("DS_CONFIRMATION_SCREEN_INFO_ID");
                c.requiredStepId            = rs.getString("REQUIRED_STEP_ID");
                c.requiredStatusCode        = rs.getString("REQUIRED_STATUS_CODE");
                c.hasPayment                = rs.getString("HAS_PAYMENT");
                c.hasRtRecord               = rs.getString("HAS_RT_RECORD");
                c.serviceCode               = rs.getString("SERVICE_CODE");
                c.showRefNo                 = rs.getString("SHOW_REF_NO");
                c.guest                     = rs.getString("GUEST");
                c.action                    = rs.getString("ACTION");
                c.components                = new ArrayList<>();
                return c;
            }, code);

        if (!screens.isEmpty()) {
            List<Object> ids = screens.stream().map(s -> s.dsConfirmationId).collect(Collectors.toList());
            Map<Object, List<ConfirmationComponentConfigDto>> compMap = new HashMap<>();
            String inClause = ids.stream().map(Object::toString).collect(Collectors.joining(","));
            jdbc.query(
                "SELECT DS_CONFIRMATION_CONFIG_ID, DS_CONFIRMATION_COMPONENT_INFO_ID, DS_CONFIRMATION_ID " +
                "FROM DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG WHERE DS_CONFIRMATION_ID IN (" + inClause + ")",
                rs -> {
                    ConfirmationComponentConfigDto cc = new ConfirmationComponentConfigDto();
                    cc.dsConfirmationConfigId        = rs.getObject("DS_CONFIRMATION_CONFIG_ID");
                    cc.dsConfirmationComponentInfoId = rs.getObject("DS_CONFIRMATION_COMPONENT_INFO_ID");
                    Object parentId = rs.getObject("DS_CONFIRMATION_ID");
                    compMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(cc);
                });
            screens.forEach(s -> s.components = compMap.getOrDefault(s.dsConfirmationId, Collections.emptyList()));
        }
        return screens;
    }

    // ── Replace (delete + insert) helpers ─────────────────────────────────────

    private void replaceSteps(String code, List<StepDto> steps) {
        // Cascade-delete status links first, then steps, then re-insert
        subResources.replace(Arrays.asList(
            new SubResourceService.DeleteStep(
                "DELETE FROM BPM_LKP_SERV_STEP_STATUS_LINKS WHERE (TO_CHAR(REQUIRED_STEP_ID), BPMN_PROCESS_NAME) IN " +
                "(SELECT REQUIRED_STEP_ID, BPMN_PROCESS_NAME FROM BPM_LKP_STEPS WHERE PROCESS_CODE = ?)", code),
            new SubResourceService.DeleteStep("DELETE FROM BPM_LKP_STEPS WHERE PROCESS_CODE = ?", code)),
            steps, s -> insertStep(code, s));
    }

    private void replaceFees(String code, List<FeeDto> fees) {
        subResources.replace(Collections.singletonList(
            new SubResourceService.DeleteStep("DELETE FROM BPM_LKP_FEES WHERE PROCESS_CODE = ?", code)),
            fees, f -> insertFee(code, f));
    }

    private void replacePaymentCallbacks(String code, List<PaymentCallbackDto> callbacks) {
        // Delete by BPMN_PROCESS_NAME join, then re-insert using per-callback serviceCode
        subResources.replace(Collections.singletonList(
            new SubResourceService.DeleteStep(
                "DELETE FROM DS_PAYMENT_CONFIRMATION_CONFIG WHERE SERVICE_CODE IN " +
                "(SELECT DISTINCT BPMN_PROCESS_NAME FROM BPM_LKP_STEPS WHERE PROCESS_CODE = ?)", code)),
            callbacks, pc -> insertPaymentCallback(pc));
    }

    private void replaceDocs(String code, List<RequiredDocDto> docs) {
        subResources.replace(Collections.singletonList(
            new SubResourceService.DeleteStep("DELETE FROM BPM_LKP_REQUIRED_DOCS WHERE PROCESS_CODE = ?", code)),
            docs, d -> insertDoc(code, d));
    }

    private void replaceDepts(String code, List<RelatedDeptDto> depts) {
        subResources.replace(Collections.singletonList(
            new SubResourceService.DeleteStep("DELETE FROM BPM_LKP_RELATED_DEPTS WHERE PROCESS_CODE = ?", code)),
            depts, d -> insertDept(code, d));
    }

    private void replaceAudiences(String code, List<TargetAudienceDto> audiences) {
        subResources.replace(Collections.singletonList(
            new SubResourceService.DeleteStep("DELETE FROM BPM_LKP_TARGET_AUDIENCE WHERE PROCESS_CODE = ?", code)),
            audiences, a -> insertAudience(code, a));
    }

    private void replaceConfirmationScreens(String code, List<ConfirmationScreenConfigDto> screens) {
        // Delete components first (FK), then screens, then re-insert
        subResources.replace(Arrays.asList(
            new SubResourceService.DeleteStep(
                "DELETE FROM DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG WHERE DS_CONFIRMATION_ID IN " +
                "(SELECT DS_CONFIRMATION_ID FROM DS_CONFIRMATION_SCREEN_CONFIG WHERE SERVICE_CODE = ?)", code),
            new SubResourceService.DeleteStep("DELETE FROM DS_CONFIRMATION_SCREEN_CONFIG WHERE SERVICE_CODE = ?", code)),
            screens, c -> insertConfirmationScreen(code, c));
    }

    // ── Insert helpers ────────────────────────────────────────────────────────

    private void insertProcessInfo(ProcessInfoDto p) {
        jdbc.update(
            "INSERT INTO BPM_PROCESSES_INFO (" +
            "  PROCESS_CODE, BPMN_PROCESS_NAME, PROCESS_DISPLAY_LABEL_AR, PROCESS_DISPLAY_LABEL_EN," +
            "  PROCESS_STATUS, PROCESS_INITIATE_SECURITY, PROCESS_ORDER," +
            "  SEND_MAIL, SEND_SMS, SHOW_ON_INBOX," +
            "  INBOX_CUSTOM_COLUMN1, INBOX_CUSTOM_COLUMN1_AR, INBOX_CUSTOM_COLUMN2, INBOX_CUSTOM_COLUMN2_AR," +
            "  INBOX_CUSTOM_COLUMN3, INBOX_CUSTOM_COLUMN3_AR, INBOX_CUSTOM_COLUMN4, INBOX_CUSTOM_COLUMN4_AR," +
            "  INBOX_CUSTOM_COLUMN5, INBOX_CUSTOM_COLUMN5_AR," +
            "  PROCESS_TYPE, PROCESS_DESC_AR, PROCESS_DESC_EN, PROCESS_TIME_AR, PROCESS_TIME_EN," +
            "  BPM_PRO_SERVICE_CATALOG, SERVICE_ICON, SERVICE_ID, SERVICE_UNICODE_ICON," +
            "  DIRECT_ACCESS, ALLOW_TERMS, ALLOW_RATING, CARD_REDIRECTION_REQUIRED," +
            "  WALLET_REQUIRE_OTP, ASK_BEFORE_PAY_WALLET, TEST_FLAG," +
            "  SURVEY_ENTITY_ID, SURVEY_SERVICE_ID," +
            "  ACTION_TYPE, ACTION_URL, PRE_AUTH, REQUIRE_OTP," +
            "  IOS_SHOW, ANDROID_SHOW, WEB_SHOW, TEST_USER_SHOW, RELEASE_USER_SHOW," +
            "  ACTIVITY_PROCESS_ACTION_TYPE, ACTIVITY_PROCESS_ACTION_URL," +
            "  INTERNAL_ACTIVITY_ACTION_TYPE, INTERNAL_ACTIVITY_ACTION_URL," +
            "  GROUP_CODE, ACTION_URL2, ADMIN_PAYMENT," +
            "  IOS_ACTIVITY_PROCESS_ACTION_TYPE, IOS_INTERNAL_ACTIVITY_ACTION_TYPE," +
            "  ANDROID_ACTIVITY_PROCESS_ACTION_TYPE, ANDROID_INTERNAL_ACTIVITY_ACTION_TYPE," +
            "  IOS_ACTION_TYPE, ANDROID_ACTION_TYPE, PLATFORM_CODE, PLATFORM_ICP_USER_ID, IS_SERVICE_CATALOG_ONE_TIME" +
            ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            p.processCode, p.bpmnProcessName, p.processDisplayLabelAr, p.processDisplayLabelEn,
            p.processStatus, nn(p.processInitiateSecurity), p.processOrder,
            p.sendMail, p.sendSms, p.showOnInbox,
            p.inboxCustomColumn1, p.inboxCustomColumn1Ar, p.inboxCustomColumn2, p.inboxCustomColumn2Ar,
            p.inboxCustomColumn3, p.inboxCustomColumn3Ar, p.inboxCustomColumn4, p.inboxCustomColumn4Ar,
            p.inboxCustomColumn5, p.inboxCustomColumn5Ar,
            p.processType, p.processDescAr, p.processDescEn, p.processTimeAr, p.processTimeEn,
            p.bpmProServiceCatalog, p.serviceIcon, p.serviceId, p.serviceUnicodeIcon,
            p.directAccess, p.allowTerms, p.allowRating, p.cardRedirectionRequired,
            p.walletRequireOtp, p.askBeforePayWallet, p.testFlag,
            p.surveyEntityId, p.surveyServiceId,
            p.actionType, p.actionUrl, p.preAuth, p.requireOtp,
            p.iosShow, p.androidShow, p.webShow, p.testUserShow, p.releaseUserShow,
            p.activityProcessActionType, p.activityProcessActionUrl,
            p.internalActivityActionType, p.internalActivityActionUrl,
            p.groupCode, p.actionUrl2, p.adminPayment,
            p.iosActivityProcessActionType, p.iosInternalActivityActionType,
            p.androidActivityProcessActionType, p.androidInternalActivityActionType,
            p.iosActionType, p.androidActionType, p.platformCode, p.platformIcpUserId, p.isServiceCatalogOneTime
        );
    }

    private void updateProcessInfo(String code, ProcessInfoDto p) {
        jdbc.update(
            "UPDATE BPM_PROCESSES_INFO SET " +
            "  BPMN_PROCESS_NAME=?, PROCESS_DISPLAY_LABEL_AR=?, PROCESS_DISPLAY_LABEL_EN=?," +
            "  PROCESS_STATUS=?, PROCESS_INITIATE_SECURITY=?, PROCESS_ORDER=?," +
            "  SEND_MAIL=?, SEND_SMS=?, SHOW_ON_INBOX=?," +
            "  INBOX_CUSTOM_COLUMN1=?, INBOX_CUSTOM_COLUMN1_AR=?, INBOX_CUSTOM_COLUMN2=?, INBOX_CUSTOM_COLUMN2_AR=?," +
            "  INBOX_CUSTOM_COLUMN3=?, INBOX_CUSTOM_COLUMN3_AR=?, INBOX_CUSTOM_COLUMN4=?, INBOX_CUSTOM_COLUMN4_AR=?," +
            "  INBOX_CUSTOM_COLUMN5=?, INBOX_CUSTOM_COLUMN5_AR=?," +
            "  PROCESS_TYPE=?, PROCESS_DESC_AR=?, PROCESS_DESC_EN=?, PROCESS_TIME_AR=?, PROCESS_TIME_EN=?," +
            "  BPM_PRO_SERVICE_CATALOG=?, SERVICE_ICON=?, SERVICE_ID=?, SERVICE_UNICODE_ICON=?," +
            "  DIRECT_ACCESS=?, ALLOW_TERMS=?, ALLOW_RATING=?, CARD_REDIRECTION_REQUIRED=?," +
            "  WALLET_REQUIRE_OTP=?, ASK_BEFORE_PAY_WALLET=?, TEST_FLAG=?," +
            "  SURVEY_ENTITY_ID=?, SURVEY_SERVICE_ID=?," +
            "  ACTION_TYPE=?, ACTION_URL=?, PRE_AUTH=?, REQUIRE_OTP=?," +
            "  IOS_SHOW=?, ANDROID_SHOW=?, WEB_SHOW=?, TEST_USER_SHOW=?, RELEASE_USER_SHOW=?," +
            "  ACTIVITY_PROCESS_ACTION_TYPE=?, ACTIVITY_PROCESS_ACTION_URL=?," +
            "  INTERNAL_ACTIVITY_ACTION_TYPE=?, INTERNAL_ACTIVITY_ACTION_URL=?," +
            "  GROUP_CODE=?, ACTION_URL2=?, ADMIN_PAYMENT=?," +
            "  IOS_ACTIVITY_PROCESS_ACTION_TYPE=?, IOS_INTERNAL_ACTIVITY_ACTION_TYPE=?," +
            "  ANDROID_ACTIVITY_PROCESS_ACTION_TYPE=?, ANDROID_INTERNAL_ACTIVITY_ACTION_TYPE=?," +
            "  IOS_ACTION_TYPE=?, ANDROID_ACTION_TYPE=?, PLATFORM_CODE=?, PLATFORM_ICP_USER_ID=?, IS_SERVICE_CATALOG_ONE_TIME=?" +
            " WHERE PROCESS_CODE=?",
            p.bpmnProcessName, p.processDisplayLabelAr, p.processDisplayLabelEn,
            p.processStatus, nn(p.processInitiateSecurity), p.processOrder,
            p.sendMail, p.sendSms, p.showOnInbox,
            p.inboxCustomColumn1, p.inboxCustomColumn1Ar, p.inboxCustomColumn2, p.inboxCustomColumn2Ar,
            p.inboxCustomColumn3, p.inboxCustomColumn3Ar, p.inboxCustomColumn4, p.inboxCustomColumn4Ar,
            p.inboxCustomColumn5, p.inboxCustomColumn5Ar,
            p.processType, p.processDescAr, p.processDescEn, p.processTimeAr, p.processTimeEn,
            p.bpmProServiceCatalog, p.serviceIcon, p.serviceId, p.serviceUnicodeIcon,
            p.directAccess, p.allowTerms, p.allowRating, p.cardRedirectionRequired,
            p.walletRequireOtp, p.askBeforePayWallet, p.testFlag,
            p.surveyEntityId, p.surveyServiceId,
            p.actionType, p.actionUrl, p.preAuth, p.requireOtp,
            p.iosShow, p.androidShow, p.webShow, p.testUserShow, p.releaseUserShow,
            p.activityProcessActionType, p.activityProcessActionUrl,
            p.internalActivityActionType, p.internalActivityActionUrl,
            p.groupCode, p.actionUrl2, p.adminPayment,
            p.iosActivityProcessActionType, p.iosInternalActivityActionType,
            p.androidActivityProcessActionType, p.androidInternalActivityActionType,
            p.iosActionType, p.androidActionType, p.platformCode, p.platformIcpUserId, p.isServiceCatalogOneTime,
            code
        );
    }

    private void insertStep(String code, StepDto s) {
        Object seq = subResources.seqOrUuid(s.bpmProStepsSeq);
        jdbc.update(
            "INSERT INTO BPM_LKP_STEPS (BPM_PRO_STEPS_SEQ, PROCESS_CODE, REQUIRED_STEP_ID, " +
            "REQUIRED_STEP_DESC_AR, REQUIRED_STEP_DESC_EN, ORDER_C, STEP_TYPE, BPMN_PROCESS_NAME, " +
            "LINK_DESC_AR, LINK_DESC_EN, ATTENTION_MSG_AR, ATTENTION_MSG_EN, STEP_SLA, SHOW_LINK_FLAG, ENTITY_CODE) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            seq, code, s.requiredStepId, s.requiredStepDescAr, s.requiredStepDescEn,
            s.orderC, s.stepType, s.bpmnProcessName,
            s.linkDescAr, s.linkDescEn, s.attentionMsgAr, s.attentionMsgEn,
            s.stepSla, s.showLinkFlag, s.entityCode);

        if (s.statusLinks != null) {
            for (StepLinkDto l : s.statusLinks) insertStepLink(s.requiredStepId, s.bpmnProcessName, l);
        }
    }

    // FK: (REQUIRED_STEP_ID + BPMN_PROCESS_NAME) → BPM_LKP_STEPS.(REQUIRED_STEP_ID + BPMN_PROCESS_NAME)
    // Both values are inherited from the parent step — they are not user-editable on the link itself.
    private void insertStepLink(String stepRequiredStepId, String stepBpmnProcessName, StepLinkDto l) {
        Object linkId = subResources.idOrMaxPlusOne(
            l.stepLinkId, "BPM_LKP_SERV_STEP_STATUS_LINKS", "STEP_LINK_ID");
        jdbc.update(
            "INSERT INTO BPM_LKP_SERV_STEP_STATUS_LINKS (STEP_LINK_ID, REQUIRED_STATUS_CODE, REQUIRED_STEP_ID, " +
            "WEB_LINK_URL, MOB_LINK_FLAG, BPMN_PROCESS_NAME, ACTION_CODE, ICON_URL, " +
            "LABEL_AR, LABEL_EN, IOS_ACTION_CODE, ANDROID_ACTION_CODE, PLATFORM_CODE) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            linkId, l.requiredStatusCode, stepRequiredStepId,
            l.webLinkUrl, l.mobLinkFlag, stepBpmnProcessName, l.actionCode, l.iconUrl,
            l.labelAr, l.labelEn, l.iosActionCode, l.androidActionCode, l.platformCode);
    }

    private void insertFee(String code, FeeDto f) {
        Object seq = subResources.seqOrUuid(f.bpmProcessesFeesSeq);
        jdbc.update(
            "INSERT INTO BPM_LKP_FEES (BPM_PROCESSES_FEES_SEQ, PROCESS_CODE, FEES_DESC_AR, FEES_DESC_EN, " +
            "FEES_AMOUNT, CURRENCY_AR, CURRENCY_EN, ORDER_C) VALUES (?,?,?,?,?,?,?,?)",
            seq, code, f.feesDescAr, f.feesDescEn, f.feesAmount, f.currencyAr, f.currencyEn, f.orderC);
    }

    private void insertPaymentCallback(PaymentCallbackDto pc) {
        Object id = subResources.idOrMaxPlusOne(pc.id, "DS_PAYMENT_CONFIRMATION_CONFIG", "ID");
        // SERVICE_CODE = step's BPMN_PROCESS_NAME (FK target); PAYMENT_STEP_ORDER = step's REQUIRED_STEP_ID
        jdbc.update(
            "INSERT INTO DS_PAYMENT_CONFIRMATION_CONFIG (ID, SERVICE_CODE, URL, STATUS, " +
            "ADDITION_DATE, PAYMENT_STEP_ORDER, SEND_NOTIFICATION) VALUES (?,?,?,?,SYSDATE,?,?)",
            id, pc.serviceCode, pc.url, flag(pc.status), pc.paymentStepOrder, flag(pc.sendNotification));
    }

    private void insertDoc(String code, RequiredDocDto d) {
        Object seq = subResources.seqOrUuid(d.bpmProRequiredDocsSeq);
        jdbc.update(
            "INSERT INTO BPM_LKP_REQUIRED_DOCS (BPM_PRO_REQUIRED_DOCS_SEQ, PROCESS_CODE, " +
            "REQUIRED_DOCS_DESC_AR, REQUIRED_DOCS_DESC_EN, ORDER_C) VALUES (?,?,?,?,?)",
            seq, code, d.requiredDocsDescAr, d.requiredDocsDescEn, d.orderC);
    }

    private void insertDept(String code, RelatedDeptDto d) {
        Object seq = subResources.seqOrUuid(d.bpmProRelatedDeptsSeq);
        jdbc.update(
            "INSERT INTO BPM_LKP_RELATED_DEPTS (BPM_PRO_RELATED_DEPTS_SEQ, PROCESS_CODE, " +
            "RELATED_DEPARTMENTS_DESC_AR, RELATED_DEPARTMENTS_DESC_EN, ORDER_C, ENTITY_CODE, ENTITY_CODE_SRC) " +
            "VALUES (?,?,?,?,?,?,?)",
            seq, code, d.relatedDepartmentsDescAr, d.relatedDepartmentsDescEn,
            d.orderC, d.entityCode, d.entityCodeSrc);
    }

    private void insertAudience(String code, TargetAudienceDto a) {
        Object seq = subResources.seqOrUuid(a.bpmProTargetAudienceSeq);
        jdbc.update(
            "INSERT INTO BPM_LKP_TARGET_AUDIENCE (BPM_PRO_TARGET_AUDIENCE_SEQ, PROCESS_CODE, " +
            "TARGET_AUDIENCE_DESC_AR, TARGET_AUDIENCE_DESC_EN, ORDER_C, MAIN_TARGET_AUDIENCE_SEQ) VALUES (?,?,?,?,?,?)",
            seq, code, a.targetAudienceDescAr, a.targetAudienceDescEn, a.orderC, a.mainTargetAudienceSeq);
    }

    private void insertConfirmationScreen(String code, ConfirmationScreenConfigDto c) {
        Object suppliedId = subResources.idOrMaxPlusOne(
            c.dsConfirmationId, "DS_CONFIRMATION_SCREEN_CONFIG", "DS_CONFIRMATION_ID");
        // Capture the actually-persisted DS_CONFIRMATION_ID: a trigger may reassign
        // it, and the component rows below must reference the real parent id.
        long cfgId = subResources.insertReturningId(
            "INSERT INTO DS_CONFIRMATION_SCREEN_CONFIG (DS_CONFIRMATION_ID, DS_CONFIRMATION_SCREEN_INFO_ID, " +
            "REQUIRED_STEP_ID, REQUIRED_STATUS_CODE, HAS_PAYMENT, HAS_RT_RECORD, SERVICE_CODE, " +
            "SHOW_REF_NO, GUEST, ACTION) VALUES (?,?,?,?,?,?,?,?,?,?)",
            "DS_CONFIRMATION_ID",
            suppliedId, c.dsConfirmationScreenInfoId, c.requiredStepId, c.requiredStatusCode,
            c.hasPayment, c.hasRtRecord, code, c.showRefNo, c.guest, c.action);

        if (c.components != null) {
            for (ConfirmationComponentConfigDto cc : c.components) {
                Object compCfgId = subResources.idOrMaxPlusOne(
                    cc.dsConfirmationConfigId, "DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG", "DS_CONFIRMATION_CONFIG_ID");
                jdbc.update(
                    "INSERT INTO DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG " +
                    "(DS_CONFIRMATION_CONFIG_ID, DS_CONFIRMATION_COMPONENT_INFO_ID, DS_CONFIRMATION_ID) VALUES (?,?,?)",
                    compCfgId, cc.dsConfirmationComponentInfoId, cfgId);
            }
        }
    }

    // ── Row mapper helper ─────────────────────────────────────────────────────

    private ProcessInfoDto mapProcessInfo(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProcessInfoDto p = new ProcessInfoDto();
        p.processCode                        = rs.getString("PROCESS_CODE");
        p.bpmnProcessName                    = rs.getString("BPMN_PROCESS_NAME");
        p.processDisplayLabelAr              = rs.getString("PROCESS_DISPLAY_LABEL_AR");
        p.processDisplayLabelEn              = rs.getString("PROCESS_DISPLAY_LABEL_EN");
        p.processStatus                      = rs.getString("PROCESS_STATUS");
        p.processInitiateSecurity            = rs.getString("PROCESS_INITIATE_SECURITY");
        p.processOrder                       = rs.getObject("PROCESS_ORDER");
        p.sendMail                           = rs.getString("SEND_MAIL");
        p.sendSms                            = rs.getString("SEND_SMS");
        p.showOnInbox                        = rs.getString("SHOW_ON_INBOX");
        p.inboxCustomColumn1                 = rs.getString("INBOX_CUSTOM_COLUMN1");
        p.inboxCustomColumn1Ar               = rs.getString("INBOX_CUSTOM_COLUMN1_AR");
        p.inboxCustomColumn2                 = rs.getString("INBOX_CUSTOM_COLUMN2");
        p.inboxCustomColumn2Ar               = rs.getString("INBOX_CUSTOM_COLUMN2_AR");
        p.inboxCustomColumn3                 = rs.getString("INBOX_CUSTOM_COLUMN3");
        p.inboxCustomColumn3Ar               = rs.getString("INBOX_CUSTOM_COLUMN3_AR");
        p.inboxCustomColumn4                 = rs.getString("INBOX_CUSTOM_COLUMN4");
        p.inboxCustomColumn4Ar               = rs.getString("INBOX_CUSTOM_COLUMN4_AR");
        p.inboxCustomColumn5                 = rs.getString("INBOX_CUSTOM_COLUMN5");
        p.inboxCustomColumn5Ar               = rs.getString("INBOX_CUSTOM_COLUMN5_AR");
        p.processType                        = rs.getString("PROCESS_TYPE");
        p.processDescAr                      = rs.getString("PROCESS_DESC_AR");
        p.processDescEn                      = rs.getString("PROCESS_DESC_EN");
        p.processTimeAr                      = rs.getString("PROCESS_TIME_AR");
        p.processTimeEn                      = rs.getString("PROCESS_TIME_EN");
        p.bpmProServiceCatalog               = rs.getString("BPM_PRO_SERVICE_CATALOG");
        p.serviceIcon                        = rs.getString("SERVICE_ICON");
        p.serviceId                          = rs.getString("SERVICE_ID");
        p.serviceUnicodeIcon                 = rs.getString("SERVICE_UNICODE_ICON");
        p.directAccess                       = rs.getString("DIRECT_ACCESS");
        p.allowTerms                         = rs.getString("ALLOW_TERMS");
        p.allowRating                        = rs.getString("ALLOW_RATING");
        p.cardRedirectionRequired            = rs.getString("CARD_REDIRECTION_REQUIRED");
        p.walletRequireOtp                   = rs.getString("WALLET_REQUIRE_OTP");
        p.askBeforePayWallet                 = rs.getString("ASK_BEFORE_PAY_WALLET");
        p.testFlag                           = rs.getString("TEST_FLAG");
        p.surveyEntityId                     = rs.getObject("SURVEY_ENTITY_ID");
        p.surveyServiceId                    = rs.getObject("SURVEY_SERVICE_ID");
        p.actionType                         = rs.getString("ACTION_TYPE");
        p.actionUrl                          = rs.getString("ACTION_URL");
        p.preAuth                            = rs.getString("PRE_AUTH");
        p.requireOtp                         = rs.getString("REQUIRE_OTP");
        p.iosShow                            = rs.getString("IOS_SHOW");
        p.androidShow                        = rs.getString("ANDROID_SHOW");
        p.webShow                            = rs.getString("WEB_SHOW");
        p.testUserShow                       = rs.getString("TEST_USER_SHOW");
        p.releaseUserShow                    = rs.getString("RELEASE_USER_SHOW");
        p.activityProcessActionType          = rs.getString("ACTIVITY_PROCESS_ACTION_TYPE");
        p.activityProcessActionUrl           = rs.getString("ACTIVITY_PROCESS_ACTION_URL");
        p.internalActivityActionType         = rs.getString("INTERNAL_ACTIVITY_ACTION_TYPE");
        p.internalActivityActionUrl          = rs.getString("INTERNAL_ACTIVITY_ACTION_URL");
        p.groupCode                          = rs.getString("GROUP_CODE");
        p.actionUrl2                         = rs.getString("ACTION_URL2");
        p.adminPayment                       = rs.getString("ADMIN_PAYMENT");
        p.iosActivityProcessActionType       = rs.getString("IOS_ACTIVITY_PROCESS_ACTION_TYPE");
        p.iosInternalActivityActionType      = rs.getString("IOS_INTERNAL_ACTIVITY_ACTION_TYPE");
        p.androidActivityProcessActionType   = rs.getString("ANDROID_ACTIVITY_PROCESS_ACTION_TYPE");
        p.androidInternalActivityActionType  = rs.getString("ANDROID_INTERNAL_ACTIVITY_ACTION_TYPE");
        p.iosActionType                      = rs.getString("IOS_ACTION_TYPE");
        p.androidActionType                  = rs.getString("ANDROID_ACTION_TYPE");
        p.platformCode                       = rs.getString("PLATFORM_CODE");
        p.platformIcpUserId                  = rs.getString("PLATFORM_ICP_USER_ID");
        p.isServiceCatalogOneTime            = rs.getString("IS_SERVICE_CATALOG_ONE_TIME");
        return p;
    }

    /**
     * NOT NULL columns that have no field on the form (e.g. PROCESS_INITIATE_SECURITY)
     * must still satisfy the constraint — default a null/blank value to "-".
     */
    private static String nn(String value) {
        return (value == null || value.trim().isEmpty()) ? "-" : value;
    }

    /** Default a NOT NULL T/F flag column to 'T' when no value is supplied. */
    private static String flag(String value) {
        return (value == null || value.trim().isEmpty()) ? "T" : value;
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Process code is required");
        return name.trim();
    }
}
