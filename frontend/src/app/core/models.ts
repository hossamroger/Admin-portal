// Shared API types mirroring the Spring Boot DTOs.

// ── Service Configuration ─────────────────────────────────────────────────────

export interface ServiceSummary {
  processCode: string;
  bpmnProcessName: string | null;
  processDisplayLabelAr: string | null;
  processDisplayLabelEn: string | null;
  processStatus: string | null;
  processType: string | null;
  processOrder: number | null;
}

export interface ServiceListResponse {
  items: ServiceSummary[];
  total: number;
  page: number;
  pageSize: number;
}

export interface StepLinkDto {
  stepLinkId?: number | null;
  requiredStatusCode?: string | null;
  requiredStepId?: string | number | null;
  webLinkUrl?: string | null;
  mobLinkFlag?: string | null;
  bpmnProcessName?: string | null;
  actionCode?: string | null;
  iconUrl?: string | null;
  labelAr?: string | null;
  labelEn?: string | null;
  iosActionCode?: string | null;
  androidActionCode?: string | null;
  platformCode?: string | null;
}

export interface StepDto {
  bpmProStepsSeq?: string | null;
  requiredStepId: string;
  requiredStepDescAr: string;
  requiredStepDescEn: string;
  orderC?: number | null;
  stepType?: string | null;
  bpmnProcessName?: string | null;
  linkDescAr?: string | null;
  linkDescEn?: string | null;
  attentionMsgAr?: string | null;
  attentionMsgEn?: string | null;
  stepSla?: number | null;
  showLinkFlag?: string | null;
  entityCode?: string | null;
  statusLinks: StepLinkDto[];
}

export interface FeeDto {
  bpmProcessesFeesSeq?: string | null;
  feesDescAr: string;
  feesDescEn?: string | null;
  feesAmount?: string | null;
  currencyAr?: string | null;
  currencyEn?: string | null;
  orderC?: number | null;
}

export interface RequiredDocDto {
  bpmProRequiredDocsSeq?: string | null;
  requiredDocsDescAr: string;
  requiredDocsDescEn: string;
  orderC?: number | null;
}

export interface RelatedDeptDto {
  bpmProRelatedDeptsSeq?: string | null;
  relatedDepartmentsDescAr: string;
  relatedDepartmentsDescEn: string;
  orderC?: number | null;
  entityCode?: string | null;
  entityCodeSrc?: string | null;
}

export interface TargetAudienceDto {
  bpmProTargetAudienceSeq?: string | null;
  targetAudienceDescAr: string;
  targetAudienceDescEn: string;
  orderC?: number | null;
  mainTargetAudienceSeq?: number | null;
}

export interface ConfirmationComponentConfigDto {
  dsConfirmationConfigId?: number | null;
  dsConfirmationComponentInfoId: number;
}

export interface ConfirmationScreenConfigDto {
  dsConfirmationId?: number | null;
  dsConfirmationScreenInfoId?: number | null;
  requiredStepId?: string | null;
  requiredStatusCode?: string | null;
  hasPayment?: string | null;
  hasRtRecord?: string | null;
  serviceCode?: string | null;
  showRefNo?: string | null;
  guest?: string | null;
  action?: string | null;
  components: ConfirmationComponentConfigDto[];
}

export interface ProcessInfoDto {
  processCode: string;
  bpmnProcessName?: string | null;
  processDisplayLabelAr?: string | null;
  processDisplayLabelEn?: string | null;
  processStatus: string;
  processInitiateSecurity?: string | null;
  processOrder?: number | null;
  sendMail?: string | null;
  sendSms?: string | null;
  showOnInbox?: string | null;
  inboxCustomColumn1?: string | null; inboxCustomColumn1Ar?: string | null;
  inboxCustomColumn2?: string | null; inboxCustomColumn2Ar?: string | null;
  inboxCustomColumn3?: string | null; inboxCustomColumn3Ar?: string | null;
  inboxCustomColumn4?: string | null; inboxCustomColumn4Ar?: string | null;
  inboxCustomColumn5?: string | null; inboxCustomColumn5Ar?: string | null;
  processType?: string | null;
  processDescAr?: string | null;
  processDescEn?: string | null;
  processTimeAr?: string | null;
  processTimeEn?: string | null;
  bpmProServiceCatalog?: string | null;
  serviceIcon?: string | null;
  serviceId?: string | null;
  serviceUnicodeIcon?: string | null;
  directAccess?: string | null;
  allowTerms?: string | null;
  allowRating?: string | null;
  cardRedirectionRequired?: string | null;
  walletRequireOtp?: string | null;
  askBeforePayWallet?: string | null;
  testFlag?: string | null;
  surveyEntityId?: number | null;
  surveyServiceId?: number | null;
  actionType?: string | null;
  actionUrl?: string | null;
  preAuth?: string | null;
  requireOtp?: string | null;
  iosShow?: string | null;
  androidShow?: string | null;
  webShow?: string | null;
  testUserShow?: string | null;
  releaseUserShow?: string | null;
  activityProcessActionType?: string | null; activityProcessActionUrl?: string | null;
  internalActivityActionType?: string | null; internalActivityActionUrl?: string | null;
  groupCode?: string | null;
  actionUrl2?: string | null;
  adminPayment?: string | null;
  iosActivityProcessActionType?: string | null; iosInternalActivityActionType?: string | null;
  androidActivityProcessActionType?: string | null; androidInternalActivityActionType?: string | null;
  iosActionType?: string | null;
  androidActionType?: string | null;
  platformCode?: string | null;
  platformIcpUserId?: string | null;
  isServiceCatalogOneTime?: string | null;
}

export interface ServiceConfigPayload {
  processInfo: ProcessInfoDto;
  steps: StepDto[];
  fees: FeeDto[];
  requiredDocs: RequiredDocDto[];
  relatedDepts: RelatedDeptDto[];
  targetAudiences: TargetAudienceDto[];
  confirmationScreens: ConfirmationScreenConfigDto[];
}

// Lookups
export interface TargetAudienceLookup { seq: number; descAr: string; descEn: string; logoUrl: string | null; }
export interface ScreenInfoLookup     { id: number; headerAr: string; headerEn: string; descAr: string; descEn: string; icon: string; }
export interface ComponentInfoLookup  { id: number; labelAr: string; labelEn: string; action: string; iconUrl: string | null; orderC: number | null; }

export interface Capabilities {
  select: boolean;
  insert: boolean;
  update: boolean;
  delete: boolean;
  runSql: boolean;
}

export interface Me {
  username: string | null;
  role?: string;
  privileges?: string[];
  allowedTables?: string[];
  restricted?: boolean;
  authEnabled?: boolean;
  can?: Capabilities;
}

export type FilterOperator =
  | 'EQ' | 'NEQ' | 'GT' | 'GTE' | 'LT' | 'LTE'
  | 'CONTAINS' | 'STARTS' | 'ENDS' | 'NULL' | 'NOTNULL';

export interface ColumnFilter {
  column: string;
  operator: FilterOperator;
  value: string;
}

export type ObjectType =
  | 'TABLE' | 'VIEW' | 'PROCEDURE' | 'FUNCTION' | 'PACKAGE' | 'TRIGGER' | 'SEQUENCE';

export interface Fingerprint {
  fingerprint: string;
  lastChange?: string | null;
  lastChangeMillis?: number;
  objectCount?: number;
  checkedAt?: number;
}

export interface SchemaOverview {
  schema: string;
  readOnly: boolean;
  counts: Record<string, number>;
  fingerprint: Fingerprint;
}

export interface DbObject {
  name: string;
  type: ObjectType;
}

export interface ColumnInfo {
  name: string;
  dataType: string;
  dataLength?: number;
  dataPrecision?: number;
  dataScale?: number;
  nullable: boolean;
  defaultValue?: string;
  primaryKey: boolean;
  comments?: string;
}

export interface ConstraintInfo {
  name: string;
  type: string; // P / R / U / C
  columns?: string;
  refTable?: string;
  refColumns?: string;
  searchCondition?: string;
}

export interface IndexInfo {
  name: string;
  unique: boolean;
  columns?: string;
}

export interface TableDetail {
  owner: string;
  name: string;
  type: string;
  comments?: string;
  columns: ColumnInfo[];
  constraints: ConstraintInfo[];
  indexes: IndexInfo[];
  rowCount?: number;
}

export interface QueryResult {
  resultSet: boolean;
  columns?: string[];
  rows?: any[][];
  updateCount?: number;
  truncated: boolean;
  elapsedMs: number;
  statement?: string;
  error?: string;
}

export interface DataPage {
  result: QueryResult;
  total: number;
  page: number;
  pageSize: number;
  sort?: string;
  dir?: string;
  pkColumns: string[];
  can: { insert: boolean; update: boolean; delete: boolean };
  error?: string;
}

export interface ColumnStat {
  column: string;
  type: string;
  totalRows?: number;
  nullCount?: number;
  uniqueCount?: number;
  timeAnalysis?: Record<string, number>;
  min?: any;
  max?: any;
  avg?: number;
}

export interface Insights {
  columnStats?: ColumnStat[];
  error?: string;
  partialError?: string;
}

export interface SourceCode {
  name: string;
  type: string;
  source: string;
}

export interface RowFilter {
  TABLE_NAME: string;
  FILTER_CONDITION: string;
}

export interface AdminUserSummary {
  USERNAME: string;
  ROLE: string;
  ENABLED: string;
}

export interface AdminUserDetail extends AdminUserSummary {
  privileges: string[];
  allowedTables: string[];
  filters: RowFilter[];
}

export interface UploadResult {
  url: string;
  folder: string;
  documentName: string;
  folderCreated: boolean;
  originalName: string;
  sizeBytes: number;
}

export interface SaveUserRequest {
  username: string;
  password?: string;
  role: string;
  privileges: string[];
  allowedTables: string[];
  filters: RowFilter[];
}
