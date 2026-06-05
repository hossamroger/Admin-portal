# Oracle Schema Explorer

أداة web (زي نسخة مبسطة من SQL Developer) بتعمل:
- **scan** لـ Oracle schema وعرض الـ Tables / Views / Procedures / Functions / Packages / Triggers / Sequences.
- عرض **تفاصيل الجداول**: الأعمدة، الـ data types، الـ PK/FK/Unique/Check constraints، والـ indexes، وكمان تصفّح البيانات (Data) بـ pagination.
- عرض **الـ source code** للـ procedures / functions / packages / triggers / views.
- **تشغيل SQL scripts** (statement واحد أو أكتر) وعرض النتيجة في grid، مع دعم DML/DDL وكتل PL/SQL.
- **زرار Sync**: لما تضغط عليه بيعمل refresh لكل حاجة (الـ sidebar + التابات المفتوحة) ويجيب آخر تعديلات على الـ DB. وكمان بيراقب الـ schema في الخلفية (كل 20 ثانية) وبيضوّي الزرار لو حصل أي تعديل (create/alter/drop).

مبني بـ **Spring Boot 2.7.18** على **Java 8** + JDBC + Oracle driver.

---

## 1) المتطلبات
- JDK 8
- Maven 3.6+
- Oracle DB (11g أو أحدث)

## 2) الإعداد
عدّل ملف `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//HOST:1521/SERVICE_NAME
    username: YOUR_USER
    password: YOUR_PASSWORD

dbexplorer:
  default-schema: ""          # سيبه فاضي عشان يستخدم schema اليوزر الحالي
  read-only: false            # خليه true لو عايز تمنع أي تعديل (SELECT فقط)
  max-rows: 1000              # أقصى عدد صفوف للـ query الواحد
  query-timeout-seconds: 60
```

> **ملاحظة على نسخة الـ driver:** الـ `pom.xml` بيستخدم `ojdbc8` نسخة `21.11.0.0`.
> لو النسخة دي مش موجودة في الـ repository بتاعك، غيّرها لأي نسخة `ojdbc8` متاحة
> (مثلاً `21.5.0.0` أو `19.21.0.0`).

## 3) التشغيل
```bash
mvn spring-boot:run
```
بعدها افتح المتصفح على:
```
http://localhost:8080
```

لبناء jar قابل للتشغيل:
```bash
mvn clean package
java -jar target/oracle-schema-explorer-1.0.0.jar
```

---

## 4) الـ API (REST)

### تسجيل الدخول والصلاحيات
| Method | Path | الوظيفة |
|---|---|---|
| POST | `/api/login` | تسجيل الدخول — body: `{"username":"...","password":"..."}` |
| POST | `/api/logout` | تسجيل الخروج (يلغي الـ session) |
| GET  | `/api/me` | بيانات المستخدم الحالي وصلاحياته (الواجهة بتعتمد عليها) |

### الـ Schema (متأثرة بصلاحيات المستخدم)
| Method | Path | الوظيفة |
|---|---|---|
| GET  | `/api/schema` | معلومات عامة + عدّاد كل نوع object (مفلتر حسب المستخدم) + بصمة التغيير |
| GET  | `/api/schema/fingerprint` | بصمة خفيفة لاكتشاف التعديلات (للـ Sync/polling) |
| GET  | `/api/schema/objects?type=TABLE` | أسماء objects من نوع معيّن (مفلترة) |
| GET  | `/api/schema/table/{name}` | تفاصيل جدول/view (لازم يكون مسموح للمستخدم) |
| GET  | `/api/schema/source/{name}?type=PROCEDURE` | الـ source code |

### البيانات والاستعلام
| Method | Path | الصلاحية المطلوبة | الوظيفة |
|---|---|---|---|
| POST   | `/api/query/run` | `EXECUTE_SQL` | تشغيل SQL script حر — body: `{"sql":"...","maxRows":1000}` |
| GET    | `/api/query/data/{table}?page=0&pageSize=50&sort=COL&dir=ASC` | `SELECT` | تصفّح + ترتيب البيانات (يرجّع كمان الـ PK وصلاحيات الجدول) |
| POST   | `/api/data/{table}` | `INSERT` | إضافة صف — body: `{"values":{"COL":val,...}}` |
| PUT    | `/api/data/{table}` | `UPDATE` | تعديل صف — body: `{"key":{pk...},"values":{...}}` |
| DELETE | `/api/data/{table}` | `DELETE` | حذف صف — body: `{"key":{pk...}}` |

> كل عمليات الـ CRUD بتتبني بـ **parameterized SQL** وبيتم التحقق من أسماء الأعمدة والجداول مقابل
> الـ metadata الحقيقي للـ schema قبل التنفيذ (حماية من SQL injection). الطلبات اللي بتعدّل البيانات
> لازم تحمل header اسمه `X-Requested-With` (الواجهة بتضيفه تلقائياً) كحماية أساسية ضد CSRF.

أنواع الـ objects المدعومة: `TABLE, VIEW, PROCEDURE, FUNCTION, PACKAGE, TRIGGER, SEQUENCE`.
الصلاحيات المتاحة: `SELECT, INSERT, UPDATE, DELETE, EXECUTE_SQL`.

---

## 4.1) المستخدمين والصلاحيات (RBAC)
المستخدمين بيتعرّفوا في `application.yml` تحت `dbexplorer.auth`. كل مستخدم له:
- `privileges`: أي عمليات مسموح بيها (`SELECT/INSERT/UPDATE/DELETE/EXECUTE_SQL`).
- `allowed-tables`: قائمة الجداول/الـ views اللي المستخدم يقدر يشوفها. **لو سيبتها فاضية = يشوف كل حاجة.**
  المستخدم المقيّد (عنده قائمة) بيشوف بس الجداول دي، ومبيشوفش الـ procedures/functions/...

```yaml
dbexplorer:
  auth:
    enabled: true                 # false = تعطيل الـ login بالكامل
    users:
      - username: admin
        password: "{noop}admin123" # للتجربة. للإنتاج استخدم BCrypt: "{bcrypt}$2a$..."
        role: ADMIN
        privileges: [SELECT, INSERT, UPDATE, DELETE, EXECUTE_SQL]
        allowed-tables: []         # كل الـ objects + محرر SQL كامل
      - username: editor
        password: "{noop}editor123"
        role: EDITOR
        privileges: [SELECT, INSERT, UPDATE, DELETE]
        allowed-tables: [EMPLOYEES, DEPARTMENTS]  # CRUD على الجدولين دول بس، من غير SQL حر
      - username: analyst
        password: "{noop}analyst123"
        role: ANALYST
        privileges: [SELECT]
        allowed-tables: [EMPLOYEES, DEPARTMENTS, JOBS]  # قراءة فقط
```

**ملاحظة على الباسوردات:** `{noop}...` معناها نص صريح (للتجربة بس). للإنتاج ولّد BCrypt hash
وحطّه كـ `{bcrypt}$2a$...` أو `$2a$...` مباشرة — التطبيق بيتحقق منه بـ `BCryptPasswordEncoder`.

---

## 5) البنية
```
src/main/java/com/example/dbexplorer/
├── DbExplorerApplication.java     # نقطة البداية
├── config/AppProperties.java      # إعدادات (read-only, max-rows, auth/users...)
├── dto/Dtos.java                  # DTOs للـ metadata
├── dto/QueryDtos.java             # DTOs للـ query
├── service/SchemaService.java     # قراءة الـ data dictionary (ALL_TABLES...)
├── service/QueryService.java      # تنفيذ الـ SQL + تصفّح + ترتيب البيانات
├── service/DataService.java       # INSERT/UPDATE/DELETE آمنة (parameterized)
├── service/AuthService.java       # تسجيل الدخول، الصلاحيات، فلترة الجداول
├── service/SqlSplitter.java       # تقسيم الـ script لـ statements
├── security/AuthFilter.java       # حماية /api/** وطلب تسجيل الدخول
└── controller/                    # الـ REST endpoints (Schema/Query/Data/Auth)
src/main/resources/
├── application.yml                # فيه إعدادات الاتصال + المستخدمين
└── static/index.html              # الواجهة (SPA): login + شجرة + grid قابل للتعديل
```

الـ introspection بيعتمد على الـ data dictionary views بتاعة أوراكل:
`ALL_TABLES`, `ALL_VIEWS`, `ALL_TAB_COLUMNS`, `ALL_CONSTRAINTS`, `ALL_CONS_COLUMNS`,
`ALL_INDEXES`, `ALL_IND_COLUMNS`, `ALL_OBJECTS`, `ALL_SOURCE`, `ALL_SEQUENCES`,
`ALL_TAB_COMMENTS`, `ALL_COL_COMMENTS`.

---

## 6) ملاحظات أمنية مهمة (اقرأها قبل ما تنشر الأداة)
الأداة دي قوية جداً لأنها بتنفّذ SQL مباشرة على الـ DB، فلازم تأمّنها قبل أي استخدام حقيقي:

1. **Authentication / Authorization:** فيه دلوقتي طبقة login بـ session + صلاحiات لكل مستخدم
   (RBAC) + تقييد الجداول المرئية. **بس** المستخدمين والباسوردات في `application.yml` —
   متسيبهاش `{noop}` في الإنتاج، استخدم BCrypt، ويفضّل تنقل المستخدمين لجدول DB أو LDAP/SSO.
   لو عايز حماية أقوى ممكن تستبدل الطبقة دي بـ `spring-boot-starter-security` كامل.
2. **DB user بصلاحيات محدودة:** استخدم user للقراءة فقط (READ-ONLY) لو الهدف الأساسي هو الاستعراض،
   أو فعّل `read-only: true` في الإعدادات.
3. **Audit logging:** فيه دلوقتي تسجيل تلقائي لكل action (مين/إيه/إمتى/نجح ولا فشل) في جدول
   `DBX_AUDIT_LOG` — التفاصيل في القسم تحت. التسجيل **async** فمابيأثرش على سرعة الطلبات.
4. **HTTPS + كلمات السر:** متخزّنش الباسورد plaintext في الإنتاج — استخدم
   environment variables أو Vault.
5. **Network:** خليها على شبكة داخلية أو خلف VPN، مش معرّضة على الإنترنت.

---

## أفكار للتطوير (features جاهزة تتبني عليها)
- Export للنتائج (CSV / Excel).
- دعم أكتر من connection / أكتر من schema في نفس الوقت.
- Explain plan وأدوات tuning.
- History للـ queries اللي اتنفّذت.
- نقل المستخدمين من `application.yml` لجدول DB + شاشة إدارة مستخدمين.

---

## سجل المراجعة (Audit Log)
كل action بيعمله أي مستخدم بيتسجّل تلقائياً في جدول `DBX_AUDIT_LOG`. كل سطر فيه:
`username` (مين)، `action` (عمل إيه: LOGIN / RUN_SQL / INSERT_ROW / UPDATE_ROW / DELETE_ROW / VIEW_TABLE ...)،
`target` (الجدول/الـ object)، `detail` (نص الـ SQL أو الـ key المتأثر)، `success` (Y/N)،
`http_status`، `client_ip`، `duration_ms`، و`event_time` (إمتى).

**ليه مش بيبطّأ التطبيق:** الـ request thread بيرمي الحدث بس في **queue في الذاكرة** (عملية لحظية)،
وفيه thread واحد في الخلفية بيكتب على دفعات (**batch insert**). لو الـ queue اتملت — يعني الكتابة
مش لاحقة لأي سبب — الأحداث الزيادة **بتتسقط** بدل ما تأخّر أي طلب. يعني مهما زاد الحِمل، الـ logging
**عمره ما هيخلّي مستخدم يستنى**. (عدد الأحداث المُسقطة متاح داخلياً عبر `AuditService.getDroppedCount()`.)

الإعدادات في `application.yml` تحت `dbexplorer.audit`:
```yaml
audit:
  enabled: true
  auto-create-table: true   # بيعمل الجدول تلقائياً أول تشغيل لو مش موجود
  log-reads: true           # false = يسجّل بس العمليات اللي بتعدّل الداتا
  queue-capacity: 10000     # حجم الـ buffer؛ الزيادة بتتسقط (مبتستناش)
  batch-size: 500
  table: DBX_AUDIT_LOG
```
لو بتستخدم Oracle 11g (مفيش IDENTITY) أو عايز تعمل الجدول يدوياً بصلاحيات مظبوطة،
في `src/main/resources/db/audit_log.sql` الـ DDL الجاهز (نسخة 12c+ ونسخة 11g بـ sequence+trigger).
تقدر تشوف السجل نفسه من داخل الأداة (مستخدم admin) بـ: `SELECT * FROM dbx_audit_log ORDER BY event_time DESC;`

---

## تجربة سريعة من غير Oracle (Demo)
في ملف `oracle-schema-explorer-demo.html` نسخة شغّالة بالكامل بنفس الواجهة بس ببيانات وهمية
(من غير Java ولا DB) — افتحه في المتصفح وجرّب:
- **admin / admin123** — كل الصلاحيات + محرر SQL.
- **editor / editor123** — تعديل/إضافة/حذف على `EMPLOYEES` و`DEPARTMENTS` بس، من غير SQL حر.
- **analyst / analyst123** — قراءة فقط على 3 جداول.

التعديلات في الـ demo بتفضل في الذاكرة طول ما الصفحة مفتوحة بس.
