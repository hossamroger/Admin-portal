// Shared API types mirroring the Spring Boot DTOs.

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

export interface SaveUserRequest {
  username: string;
  password?: string;
  role: string;
  privileges: string[];
  allowedTables: string[];
  filters: RowFilter[];
}
