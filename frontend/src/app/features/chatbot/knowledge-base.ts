export interface KbEntry {
  id: string;
  title: string;
  keywords: string[];
  answer: string;
  related?: string[];
}

export const KB: KbEntry[] = [
  // ── Overview / navigation ────────────────────────────────────────────────
  {
    id: 'overview',
    title: 'What can I do in this portal?',
    keywords: ['what', 'overview', 'features', 'help', 'do', 'portal', 'start', 'home', 'menu', 'sidebar'],
    answer:
      'The **DS Admin Portal** lets you manage:\n' +
      '• **Service Config** — BPM service/process configuration\n' +
      '• **Payment Codes** — pay codes and their service detail mappings\n' +
      '• **Entity Management** — entities, logos, status, and Android/iOS availability\n' +
      '• **Process Statuses** — lookup list of process statuses\n' +
      '• **Home Banner Config** — home screen banners\n' +
      '• **Donation Projects** — projects, categories, and organizations\n' +
      '• **Attachments** — upload files and get public URLs\n' +
      '• **User Management** (admins) — users, roles, and privileges\n\n' +
      'Use the **left sidebar** to switch sections, and the **chatbot** (bottom-right) for help any time.',
    related: ['service-config', 'pay-codes', 'entities', 'donations', 'attachments', 'manage-lists'],
  },
  {
    id: 'sync',
    title: 'What does Sync do?',
    keywords: ['sync', 'refresh', 'reload', 'update', 'schema', 'changed'],
    answer:
      'The **Sync** button (top-right toolbar) refreshes data from the database. ' +
      'Use it after changes have been made on the backend. ' +
      'If something has changed since your last sync, the Sync button highlights to alert you.',
    related: ['overview'],
  },

  // ── Login / session ──────────────────────────────────────────────────────
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
    keywords: ['logout', 'log out', 'sign out', 'exit', 'session', 'avatar'],
    answer:
      'Click your **user avatar** (top-right) to open the menu, then click **Sign out**. ' +
      'Your session ends and you are returned to the login page.',
    related: ['login', 'dark-mode'],
  },

  // ── Service Configuration ────────────────────────────────────────────────
  {
    id: 'service-config',
    title: 'Configuring services',
    keywords: ['service', 'services', 'service config', 'configuration', 'process', 'bpm', 'configure'],
    answer:
      'Open **Service Config** from the sidebar to manage services (BPM processes). ' +
      'The list lets you search, filter by status/type, and paginate. ' +
      'Click a service to edit it, or use **+ New Service** to create one. ' +
      'Each service is organised into tabs: **Basic Info**, **Steps**, **Fees**, **Documents**, **Providers**, **Audience**, and **Confirmation**.',
    related: ['service-create', 'service-tabs', 'service-flags'],
  },
  {
    id: 'service-create',
    title: 'Creating a new service',
    keywords: ['new service', 'create service', 'add service', 'create process'],
    answer:
      'On the Service Config list, click **+ New Service**. Fill in the **Basic Info** tab (the process code and label are required) and click **Save Basic Info**. ' +
      'Required database fields that have no form input (e.g. PROCESS_INITIATE_SECURITY) are defaulted automatically. ' +
      'Once the service is created, the other tabs (Steps, Fees, etc.) unlock so you can add sub-sections.',
    related: ['service-config', 'service-tabs'],
  },
  {
    id: 'service-tabs',
    title: 'Saving service sub-sections (per-tab save)',
    keywords: ['steps', 'fees', 'documents', 'providers', 'audience', 'confirmation', 'tab', 'sub-section', 'save tab'],
    answer:
      'Each service tab saves **independently**. After editing a tab (Steps, Fees, Documents, Providers, Audience, or Confirmation), click that tab\'s own **Save** button. ' +
      'A banner warns you of any tab with unsaved changes. ' +
      'You must save **Basic Info** first (to create the service record) before the sub-section tabs can be edited.',
    related: ['service-config', 'service-create'],
  },
  {
    id: 'service-flags',
    title: 'Service flags explained',
    keywords: ['flag', 'flags', 'direct access', 'otp', 'pre auth', 'ios show', 'android show', 'web show', 'test user', 'release user', 'admin payment', 'one time', 'visibility'],
    answer:
      'The **Flags** section in Basic Info controls service behaviour and visibility:\n' +
      '• **Direct Access**, **Allow FE Credit Card Redirection**, **Require OTP**, **Pre Auth**, **Admin Payment**\n' +
      '• Visibility: **iOS Show**, **Android Show**, **Web Show**, **Test User Show**, **Release User Show**\n' +
      '• **One Time** — the service can only be initiated once per user.\n\n' +
      'Each flag is a checkbox; tick it to enable, then click **Save Basic Info**.',
    related: ['service-config', 'service-create'],
  },

  // ── Payment Codes ────────────────────────────────────────────────────────
  {
    id: 'pay-codes',
    title: 'Managing payment codes',
    keywords: ['pay', 'payment', 'pay code', 'payment code', 'pay codes', 'billing', 'process code', 'fee code'],
    answer:
      'Open **Payment Codes** from the sidebar to manage pay codes and their service detail mappings.\n\n' +
      'The list is searchable and paginated. Click a pay code to edit it, or use **+ New** to create one. ' +
      'Each pay code has a master record (process/entity/service codes) and a one-to-one **Details** section ' +
      '(service and entity descriptions in Arabic and English). ' +
      'The process code is the unique key and is read-only when editing.',
    related: ['service-config', 'entities'],
  },

  // ── Entity Management ────────────────────────────────────────────────────
  {
    id: 'entities',
    title: 'Managing entities',
    keywords: ['entity', 'entities', 'entity management', 'entity code', 'logo', 'android', 'ios', 'entity status', 'action url', 'action type'],
    answer:
      'Open **Entities** from the sidebar to manage entity records.\n\n' +
      'The list shows entity code, English/Arabic names, and status — and is searchable and paginated. ' +
      'Click an entity to edit it, or use **+ New Entity** to create one. Each entity has:\n' +
      '• **Entity Code** (unique key, read-only when editing), **Name (EN)**, **Name (AR)**, **Logo URL**\n' +
      '• **Status** — Active (T) or Inactive (F)\n' +
      '• **Android** / **iOS** availability (Y/N)\n' +
      '• **Action Type** and **Action URL**\n\n' +
      'You can also **delete** an entity from its editor (with a confirmation prompt).',
    related: ['pay-codes', 'overview'],
  },

  // ── Donations ────────────────────────────────────────────────────────────
  {
    id: 'donations',
    title: 'Managing donations',
    keywords: ['donation', 'donations', 'project', 'projects', 'category', 'categories', 'organization', 'organizations', 'charity'],
    answer:
      'Open **Donation Projects** from the sidebar to reach the Donations hub, which has three tabs:\n' +
      '• **Projects** — donation projects (click one to open its full editor)\n' +
      '• **Categories** — donation categories\n' +
      '• **Organizations** — donation organizations\n\n' +
      'Each tab is a searchable, paginated list with add/edit/delete.',
    related: ['donation-project', 'manage-lists'],
  },
  {
    id: 'donation-project',
    title: 'Editing a donation project',
    keywords: ['donation project', 'project form', 'amount', 'amounts', 'target', 'paid', 'remaining', 'schedule', 'details'],
    answer:
      'Click a project in the Donations hub to open its editor. It has tabs:\n' +
      '• **Basic Info** — name, category, organization, plus an **Amounts** section (Min / Target / Paid / Remaining) and Schedule\n' +
      '• **Amounts** — predefined donation amounts\n' +
      '• **Details** — additional project detail rows\n\n' +
      'Each tab saves independently; a banner flags any tab with unsaved changes.',
    related: ['donations'],
  },

  // ── Manage / dynamic lists ───────────────────────────────────────────────
  {
    id: 'manage-lists',
    title: 'Managing configuration lists',
    keywords: ['manage', 'list', 'lists', 'process status', 'home banner', 'lookup', 'crud', 'entity', 'records', 'add', 'edit', 'delete'],
    answer:
      'Configuration entities — **Process Statuses**, **Home Banner Config**, and the donation lists — appear as links in the sidebar. ' +
      'Each opens a searchable, sortable, paginated list where you can **add** and **edit** records through a generated form. ' +
      '**Delete** is available where it is permitted (e.g. **Home Banner Config** and the donation lists), shown as a button in the editor with a confirmation prompt. ' +
      'Forms warn you before leaving with unsaved changes.',
    related: ['donations', 'service-config'],
  },

  // ── Attachments ──────────────────────────────────────────────────────────
  {
    id: 'attachments',
    title: 'Uploading files (Attachments)',
    keywords: ['attachment', 'attachments', 'upload', 'file', 'storage', 'public url', 'link', 'share', 'document'],
    answer:
      'Open **Attachments** from the sidebar to upload a file to cloud storage and get a permanent shareable public URL.\n\n' +
      '1. Click the drop zone to choose a file (any type, max 50 MB).\n' +
      '2. Optionally set a **Folder name** (reused if it exists, created if new) and a **Document name** (defaults to the original filename).\n' +
      '3. Click **Upload** — a progress bar shows status.\n\n' +
      'When done, copy the generated **Public URL** with the Copy button.',
    related: ['overview'],
  },

  // ── User Management ──────────────────────────────────────────────────────
  {
    id: 'user-management',
    title: 'Managing users (Admin only)',
    keywords: ['user', 'users', 'admin', 'manage', 'add user', 'create user', 'user management', 'role', 'roles'],
    answer:
      'Admins open **User Management** from the avatar menu (top-right). Here you can:\n' +
      '• View all users and their roles (**USER**, **EDITOR**, **ANALYST**, **ADMIN**)\n' +
      '• Create new users\n' +
      '• Edit existing users (role, privileges, allowed tables, filters)\n' +
      '• Delete users (the primary *admin* account cannot be deleted)',
    related: ['user-privileges'],
  },
  {
    id: 'user-privileges',
    title: 'User privileges explained',
    keywords: ['privilege', 'privileges', 'permission', 'permissions', 'select', 'insert', 'update', 'delete', 'execute sql'],
    answer:
      'Each user can be granted one or more privileges:\n' +
      '• **SELECT** — view data\n' +
      '• **INSERT** — add new records\n' +
      '• **UPDATE** — edit records\n' +
      '• **DELETE** — delete records\n' +
      '• **EXECUTE_SQL** — run raw SQL (where enabled)\n\n' +
      'Privileges are assigned by an admin in User Management.',
    related: ['user-management'],
  },

  // ── Dark mode ────────────────────────────────────────────────────────────
  {
    id: 'dark-mode',
    title: 'Switching between light and dark mode',
    keywords: ['dark', 'dark mode', 'light', 'light mode', 'theme', 'appearance', 'night'],
    answer:
      'Click your **user avatar** (top-right) to open the menu, then choose **Dark mode** (or **Light mode** to switch back). ' +
      'Your preference is remembered for next time, and applies across all forms, lists, and drop-downs.',
    related: ['logout'],
  },
];

export const SUGGESTIONS = [
  'What can I do in this portal?',
  'How do I create a new service?',
  'How do I manage payment codes?',
  'How do I manage entities?',
  'What do the service flags mean?',
  'How do I manage donations?',
  'How do I upload a file?',
  'How do I manage users?',
  'How do I switch to dark mode?',
  'What does Sync do?',
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
