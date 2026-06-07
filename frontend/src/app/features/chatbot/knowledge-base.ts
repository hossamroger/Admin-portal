export interface KbEntry {
  id: string;
  title: string;
  keywords: string[];
  answer: string;
  related?: string[];
}

export const KB: KbEntry[] = [
  // ── Navigation ──────────────────────────────────────────────────────────────
  {
    id: 'nav-tables',
    title: 'How to open a table',
    keywords: ['open', 'table', 'navigate', 'click', 'sidebar', 'list', 'view', 'browse'],
    answer:
      'In the left sidebar, expand the **Tables** group. Click any table name to open it. ' +
      'The main area will show the table with three tabs: **Structure**, **Data**, and **Insights**.',
    related: ['data-browse', 'structure', 'insights'],
  },
  {
    id: 'search-tables',
    title: 'How to search for a table',
    keywords: ['search', 'find', 'filter', 'tables', 'look', 'locate'],
    answer:
      'Use the **search box** at the top of the sidebar (inside the Tables group). ' +
      'Type part of the table name and the list filters instantly. The count next to the search icon shows how many tables match.',
    related: ['nav-tables'],
  },
  {
    id: 'sync',
    title: 'What does Sync do?',
    keywords: ['sync', 'refresh', 'reload', 'update', 'schema', 'changed', 'new table'],
    answer:
      'The **Sync** button (top-right toolbar) refreshes the schema from the database. ' +
      'Use it when tables have been added, dropped, or modified. ' +
      'If the schema has changed since your last sync, the Sync button will pulse green to alert you.',
    related: ['nav-tables'],
  },

  // ── Structure tab ─────────────────────────────────────────────────────────
  {
    id: 'structure',
    title: 'Understanding the Structure tab',
    keywords: ['structure', 'columns', 'column', 'type', 'data type', 'nullable', 'default', 'constraint', 'index', 'primary key', 'pk', 'foreign key'],
    answer:
      'The **Structure** tab shows:\n' +
      '• **Columns** — name, data type, nullable, default value, and comments\n' +
      '• **Constraints** — primary keys, foreign keys, unique, and check constraints\n' +
      '• **Indexes** — all indexes on the table\n\n' +
      'Use the search box at the top of the column list to filter by column name.',
    related: ['nav-tables', 'data-browse'],
  },

  // ── Data tab ─────────────────────────────────────────────────────────────
  {
    id: 'data-browse',
    title: 'Browsing table data',
    keywords: ['data', 'rows', 'browse', 'view data', 'records', 'see', 'show'],
    answer:
      'Click the **Data** tab on any table. Rows are loaded 50 at a time. ' +
      'Use the **◀ ▶** arrows at the top to page through results. ' +
      'The pager shows the current range, e.g. *rows 1–50 of 179*.',
    related: ['sort', 'pagination', 'add-row', 'edit-row', 'delete-row'],
  },
  {
    id: 'sort',
    title: 'Sorting table data',
    keywords: ['sort', 'order', 'ascending', 'descending', 'asc', 'desc', 'column header'],
    answer:
      'Click any **column header** in the Data tab to sort by that column (ascending). ' +
      'Click the same header again to toggle to descending order. ' +
      'An **arrow icon** next to the column name shows the current sort direction.',
    related: ['data-browse', 'pagination'],
  },
  {
    id: 'pagination',
    title: 'Navigating pages of data',
    keywords: ['page', 'next', 'previous', 'pagination', 'rows', 'navigate pages'],
    answer:
      'Use the **◀** and **▶** buttons in the toolbar to go to the previous or next page. ' +
      'Each page shows up to 50 rows. The label shows how many total rows exist (e.g. *rows 51–100 of 179*).',
    related: ['data-browse', 'sort'],
  },

  // ── Add Row ───────────────────────────────────────────────────────────────
  {
    id: 'add-row',
    title: 'Adding a new row',
    keywords: ['add', 'insert', 'new', 'row', 'record', 'create'],
    answer:
      'Click **+ Add Row** in the Data tab toolbar. A blank row appears at the top of the grid.\n\n' +
      '• **Auto-generated primary keys** (sequence/trigger) show *auto* — leave them blank.\n' +
      '• **User-assigned primary keys** show the suggested next value (read-only, in green) — it is pre-filled automatically.\n\n' +
      'Fill in the other fields and click **Save changes** to insert the row.',
    related: ['save-changes', 'pk-info', 'edit-row'],
  },
  {
    id: 'pk-info',
    title: 'Primary key behaviour when inserting',
    keywords: ['primary key', 'pk', 'auto', 'sequence', 'trigger', 'next value', 'auto increment', 'identity'],
    answer:
      'When adding a row:\n' +
      '• If the PK is backed by an Oracle **sequence or trigger**, the field shows *auto* (greyed, disabled) — the database assigns the value automatically.\n' +
      '• If the PK is **user-assigned** (no sequence/trigger), the field is pre-filled with the suggested next value (MAX+1) and is read-only — it will be included in the insert automatically.',
    related: ['add-row'],
  },
  {
    id: 'save-changes',
    title: 'Saving changes',
    keywords: ['save', 'commit', 'apply', 'submit', 'changes', 'persist'],
    answer:
      'After making edits (add, update, or delete), click **Save changes** in the Data tab toolbar. ' +
      'All pending operations are sent to the database at once. ' +
      'If any operation fails, you will see a message identifying exactly which row/operation failed.',
    related: ['add-row', 'edit-row', 'delete-row'],
  },

  // ── Edit Row ──────────────────────────────────────────────────────────────
  {
    id: 'edit-row',
    title: 'Editing an existing row',
    keywords: ['edit', 'update', 'modify', 'change', 'cell', 'inline'],
    answer:
      'Click directly on any non-primary-key cell in the Data tab. The cell becomes an editable input. ' +
      'Modified rows are highlighted in light green. ' +
      'When done, click **Save changes** to persist all edits.',
    related: ['save-changes', 'delete-row'],
  },

  // ── Delete Row ────────────────────────────────────────────────────────────
  {
    id: 'delete-row',
    title: 'Deleting a row',
    keywords: ['delete', 'remove', 'drop row', 'erase'],
    answer:
      'Click the **🗑 delete icon** on the left of any row to mark it for deletion (the row turns red with strikethrough). ' +
      'You can mark multiple rows. Click **Save changes** to confirm — a confirmation dialog will appear before any rows are deleted.',
    related: ['save-changes', 'edit-row'],
  },

  // ── Insights ──────────────────────────────────────────────────────────────
  {
    id: 'insights',
    title: 'Understanding the Insights tab',
    keywords: ['insights', 'statistics', 'stats', 'count', 'null', 'unique', 'min', 'max', 'avg', 'average', 'analysis'],
    answer:
      'The **Insights** tab shows column-level statistics for the table:\n' +
      '• **Total rows**, **null count**, **unique count** per column\n' +
      '• **Min / Max / Avg** for numeric columns\n' +
      '• **Time analysis** (records in last 1/6/12/24 months) for date columns\n\n' +
      'Insights can take a moment to load for large tables.',
    related: ['structure', 'data-browse'],
  },

  // ── SQL Editor ────────────────────────────────────────────────────────────
  {
    id: 'sql-editor',
    title: 'Running SQL queries',
    keywords: ['sql', 'query', 'execute', 'run', 'script', 'editor', 'statement', 'select', 'custom'],
    answer:
      'Go to **SQL Editor** from the sidebar (if you have the EXECUTE_SQL privilege). ' +
      'Type any SQL statement or script and click **Run** (or press Ctrl+Enter). ' +
      'You can run multiple statements separated by semicolons — each result is shown separately. ' +
      'Use the **Max rows** setting to limit how many rows are returned.',
    related: ['sql-readonly', 'sql-results'],
  },
  {
    id: 'sql-readonly',
    title: 'Read-only mode in SQL editor',
    keywords: ['read only', 'readonly', 'select only', 'no insert', 'no update', 'no delete', 'restricted sql'],
    answer:
      'If **Read-only mode** is enabled (shown as a red pill in the toolbar), only SELECT, WITH, EXPLAIN, and DESC statements are allowed. ' +
      'Any INSERT, UPDATE, or DELETE will be blocked with an error message.',
    related: ['sql-editor'],
  },
  {
    id: 'sql-results',
    title: 'Reading SQL results',
    keywords: ['result', 'output', 'rows returned', 'update count', 'affected', 'elapsed'],
    answer:
      'Each executed statement shows:\n' +
      '• **Result set** (rows and columns) for SELECT queries\n' +
      '• **Update count** for INSERT/UPDATE/DELETE\n' +
      '• **Elapsed time** in milliseconds\n' +
      '• **Error message** if the statement failed\n\n' +
      'If results are truncated (more rows exist than the limit), a warning is shown.',
    related: ['sql-editor'],
  },

  // ── User Management ───────────────────────────────────────────────────────
  {
    id: 'user-management',
    title: 'Managing users (Admin only)',
    keywords: ['user', 'users', 'admin', 'manage', 'add user', 'create user', 'user management'],
    answer:
      'Admins can access **User Management** from the sidebar. Here you can:\n' +
      '• View all users and their roles\n' +
      '• Create new users\n' +
      '• Edit existing users (role, privileges, allowed tables, filters)\n' +
      '• Delete users (the primary *admin* account cannot be deleted)',
    related: ['user-privileges', 'user-tables', 'user-filters'],
  },
  {
    id: 'user-privileges',
    title: 'User privileges explained',
    keywords: ['privilege', 'privileges', 'permission', 'permissions', 'select', 'insert', 'update', 'delete', 'execute sql'],
    answer:
      'Each user can be granted one or more privileges:\n' +
      '• **SELECT** — view table data and run the Data/Insights tabs\n' +
      '• **INSERT** — add new rows\n' +
      '• **UPDATE** — edit existing rows\n' +
      '• **DELETE** — delete rows\n' +
      '• **EXECUTE_SQL** — use the SQL Editor\n\n' +
      'Privileges are assigned by an admin in User Management.',
    related: ['user-management', 'user-tables'],
  },
  {
    id: 'user-tables',
    title: 'Restricting which tables a user can see',
    keywords: ['allowed tables', 'restrict', 'table access', 'whitelist', 'which tables'],
    answer:
      'In User Management, the **Allowed Tables** field lets you restrict which tables a user can see. ' +
      'Leave it empty to grant access to all tables. ' +
      'Add specific table names (one per line) to restrict the user to only those tables.',
    related: ['user-management', 'user-filters'],
  },
  {
    id: 'user-filters',
    title: 'Row-level filters for users',
    keywords: ['filter', 'row filter', 'row level', 'condition', 'where clause', 'rls'],
    answer:
      'Table filters add a hidden WHERE clause to all queries for a specific user on a specific table. ' +
      'For example, a filter of `REGION = \'EMEA\'` on the ORDERS table means that user will only ever see orders from EMEA — in the Data tab, Insights, and counts.',
    related: ['user-management', 'user-tables'],
  },

  // ── Login / Session ───────────────────────────────────────────────────────
  {
    id: 'login',
    title: 'Logging in',
    keywords: ['login', 'log in', 'sign in', 'username', 'password', 'credentials'],
    answer:
      'Enter your **username** and **password** on the login page and click **Sign In**. ' +
      'If you forget your password, ask your administrator to reset it from User Management.',
    related: ['logout'],
  },
  {
    id: 'logout',
    title: 'Logging out',
    keywords: ['logout', 'log out', 'sign out', 'exit', 'session'],
    answer:
      'Click your **user avatar** (top-right) to open the menu, then click **Logout**. ' +
      'Your session will be ended and you will be redirected to the login page.',
    related: ['login'],
  },

  // ── Source view ───────────────────────────────────────────────────────────
  {
    id: 'source-view',
    title: 'Viewing object source code',
    keywords: ['source', 'ddl', 'procedure', 'function', 'view source', 'package', 'trigger source', 'code'],
    answer:
      'For stored procedures, functions, packages, views, and triggers, click the object in the sidebar to open the **Source View**. ' +
      'The source code is displayed read-only with a **Copy** button to copy it to your clipboard.',
    related: ['nav-tables'],
  },
];

export const SUGGESTIONS = [
  'How do I open a table?',
  'How do I add a new row?',
  'How do I edit a row?',
  'How do I run SQL?',
  'What does Sync do?',
  'How do I manage users?',
  'What are user privileges?',
  'How do I sort data?',
];

export function findAnswer(query: string): KbEntry[] {
  const q = query.toLowerCase().replace(/[?!.,]/g, '');
  const words = q.split(/\s+/).filter(w => w.length > 2);
  if (!words.length) return [];

  const scored = KB.map(entry => {
    let score = 0;
    for (const word of words) {
      for (const kw of entry.keywords) {
        if (kw.includes(word) || word.includes(kw)) score += kw === word ? 3 : 1;
      }
      if (entry.title.toLowerCase().includes(word)) score += 2;
      if (entry.answer.toLowerCase().includes(word)) score += 0.5;
    }
    return { entry, score };
  });

  return scored
    .filter(s => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 3)
    .map(s => s.entry);
}
