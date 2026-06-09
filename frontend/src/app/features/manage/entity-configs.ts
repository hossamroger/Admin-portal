/**
 * Registry for the dynamic CRUD engine (/manage/:entity).
 *
 * Adding a new admin screen = one entry here + one entry in the backend
 * CrudEntities whitelist. No new components, routes or API methods needed.
 *
 * Field types:
 *  - text / number / textarea / date / datetime / color : plain inputs
 *  - checkbox01 : checkbox stored as NUMBER 1/0
 *  - checkboxTF : checkbox stored as 'T'/'F'
 *  - checkboxYN : checkbox stored as 'Y'/'N'
 *  - lookup     : dropdown fed by the entity's named backend lookup
 *  - readonly   : display-only (hidden on the create form)
 */

export type FieldType =
  | 'text' | 'number' | 'textarea' | 'date' | 'datetime' | 'color'
  | 'checkbox01' | 'checkboxTF' | 'checkboxYN' | 'lookup' | 'readonly';

export interface FieldDef {
  col: string;            // COLUMN_NAME (uppercase, as in Oracle)
  label: string;
  type: FieldType;
  rtl?: boolean;
  mono?: boolean;
  span2?: boolean;        // occupy both grid columns
  placeholder?: string;
  lookup?: string;        // lookup name (for type 'lookup')
  default?: unknown;      // initial value on the create form
}

export interface ListColDef {
  col: string;
  label: string;
  mono?: boolean;
  rtl?: boolean;
  /** Render as Active/Inactive badge driven by NUMBER 1/0. */
  badge01?: boolean;
  /** For temporal columns: chars of the ISO string to keep (10 = date only). */
  truncate?: number;
}

export interface EntityConfig {
  name: string;            // URL segment + backend entity name
  title: string;           // list page heading
  titleSingular: string;   // "New X" button / form heading
  icon: string;            // material icon for the nav link
  pk: string;              // PK column name
  searchPlaceholder: string;
  listColumns: ListColDef[];
  fields: FieldDef[];
}

export const ENTITY_CONFIGS: Record<string, EntityConfig> = {

  'process-status': {
    name: 'process-status',
    title: 'Process Statuses',
    titleSingular: 'Process Status',
    icon: 'rule',
    pk: 'ID',
    searchPlaceholder: 'Search by process code or name…',
    listColumns: [
      { col: 'ID',              label: 'ID', mono: true },
      { col: 'PROCESS_CODE',    label: 'Process Code', mono: true },
      { col: 'PROCESS_NAME',    label: 'Process Name' },
      { col: 'IOS_VERSION',     label: 'iOS Version', mono: true },
      { col: 'ANDROID_VERSION', label: 'Android Version', mono: true },
      { col: 'TIME_TO_BE_AVAILABLE', label: 'Available From', mono: true, truncate: 16 },
    ],
    fields: [
      { col: 'PROCESS_CODE',      label: 'Process Code *', type: 'number', mono: true, placeholder: 'e.g. 1001' },
      { col: 'PROCESS_NAME',      label: 'Process Name *', type: 'text', placeholder: 'e.g. MY_PROCESS' },
      { col: 'STATUS_CODE',       label: 'Status Code',       type: 'lookup', lookup: 'msgs' },
      { col: 'STATUS_ON_WEB',     label: 'Status on Web',     type: 'lookup', lookup: 'msgs' },
      { col: 'STATUS_ON_IOS',     label: 'Status on iOS',     type: 'lookup', lookup: 'msgs' },
      { col: 'STATUS_ON_ANDROID', label: 'Status on Android', type: 'lookup', lookup: 'msgs' },
      { col: 'IOS_VERSION',       label: 'iOS Version',     type: 'text', mono: true, placeholder: 'e.g. 2.0.0' },
      { col: 'ANDROID_VERSION',   label: 'Android Version', type: 'text', mono: true, placeholder: 'e.g. 2.0.0' },
      { col: 'TIME_TO_BE_AVAILABLE', label: 'Available From', type: 'datetime', mono: true },
      { col: 'MSG_AR', label: 'Message (AR)', type: 'textarea', rtl: true, span2: true },
      { col: 'MSG_EN', label: 'Message (EN)', type: 'textarea', span2: true },
    ],
  },

  'home-banner': {
    name: 'home-banner',
    title: 'Home Banner Config',
    titleSingular: 'Banner',
    icon: 'view_carousel',
    pk: 'ID',
    searchPlaceholder: 'Search by URL or platform…',
    listColumns: [
      { col: 'ID',           label: 'ID', mono: true },
      { col: 'BANNER_ORDER', label: 'Order', mono: true },
      { col: 'PLATFORM',     label: 'Platform' },
      { col: 'LANGUAGE',     label: 'Language' },
      { col: 'URL',          label: 'URL' },
      { col: 'START_DT',     label: 'Start',  mono: true, truncate: 10 },
      { col: 'EXPIRY_DT',    label: 'Expiry', mono: true, truncate: 10 },
      { col: 'IS_ACTIVE',    label: 'Active', badge01: true },
    ],
    fields: [
      { col: 'URL',    label: 'URL',         type: 'text', span2: true, placeholder: 'https://…' },
      { col: 'URL_SM', label: 'URL (Small)', type: 'text', span2: true, placeholder: 'https://… (small variant)' },
      { col: 'PLATFORM',     label: 'Platform',     type: 'text', placeholder: 'e.g. IOS, ANDROID, WEB' },
      { col: 'LANGUAGE',     label: 'Language',     type: 'text', placeholder: 'e.g. AR, EN' },
      { col: 'MIN_VERSION',  label: 'Min Version',  type: 'text', mono: true, placeholder: 'e.g. 2.0.0' },
      { col: 'BANNER_ORDER', label: 'Banner Order', type: 'number', mono: true, placeholder: 'auto (MAX+1)' },
      { col: 'START_DT',  label: 'Start Date',  type: 'date', mono: true },
      { col: 'EXPIRY_DT', label: 'Expiry Date', type: 'date', mono: true },
      { col: 'FORE_COLOR',       label: 'Foreground Color', type: 'text', mono: true, placeholder: '#FFFFFF' },
      { col: 'BG_COLOR',         label: 'Background Color', type: 'text', mono: true, placeholder: '#000000' },
      { col: 'MAIN_TITLE_COLOR', label: 'Main Title Color', type: 'text', mono: true, placeholder: '#000000' },
      { col: 'EXTENSION_TYPE',   label: 'Extension Type',   type: 'text' },
      { col: 'ACTION_TYPE', label: 'Action Type', type: 'text' },
      { col: 'ACTION_CODE', label: 'Action Code', type: 'text', mono: true },
      { col: 'ACTION_URL',  label: 'Action URL',  type: 'text', span2: true, placeholder: 'https://…' },
      { col: 'CATALOG_ID',  label: 'Catalog ID',  type: 'text', mono: true },
      { col: 'IS_ACTIVE',    label: 'Active',     type: 'checkbox01', default: 1 },
      { col: 'HAS_ACTION',   label: 'Has Action', type: 'checkbox01', default: 0 },
      { col: 'IS_HEADLINE',  label: 'Headline',   type: 'checkbox01', default: 0 },
      { col: 'IS_DARK_MODE', label: 'Dark Mode',  type: 'checkbox01', default: 0 },
      { col: 'CREATED_AT', label: 'Created At', type: 'readonly', mono: true },
      { col: 'UPDATED_AT', label: 'Updated At', type: 'readonly', mono: true },
    ],
  },
};

/** Entities surfaced as sidebar links, in display order. */
export const NAV_ENTITIES: EntityConfig[] = [
  ENTITY_CONFIGS['process-status'],
  ENTITY_CONFIGS['home-banner'],
];
