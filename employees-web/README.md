# employees-web

A Java web server exposing employee and salary services over HTTP/JSON.

Plain Jakarta Servlet application - **no Spring, no Spring Boot, no ORM, no JSON library**. The
whole dependency list is three artifacts: the Servlet API (supplied by the container), embedded
Tomcat (only to run the app standalone) and the H2 database driver. The WAR ships just one of them:
`maven-war-plugin` excludes the embedded container, so a WAR deployed to a real Tomcat never carries
a second copy of Catalina.

---

## Running it

Standalone, on embedded Tomcat - nothing to install:

```bash
cd employees-web
mvn compile exec:java            # http://localhost:8080
```

As a WAR, in any Jakarta EE 10 web container (Tomcat 10.1+, Jetty 12 EE10, ...):

```bash
mvn package                      # target/employees.war
cp target/employees.war $CATALINA_HOME/webapps/
```

Both paths run the same wiring: [`WebApp`](src/main/java/com/ivory/employees/web/WebApp.java)
registers the servlets and filters programmatically, and is picked up either through
`@WebListener` (WAR) or by [`Launcher`](src/main/java/com/ivory/employees/Launcher.java) (embedded).

Running `Launcher` straight from an IDE works too - set the working directory to this module so
`./data` resolves.

```bash
mvn test                         # 23 unit / integration tests
```

## A first call

```bash
BASE=http://localhost:8080

TOKEN=$(curl -s -X POST $BASE/api/login \
          -H 'Content-Type: application/json' \
          -d '{"username":"admin","password":"Aa123456!"}' | grep -oP '"token": "\K[^"]+')

curl -s -H "Authorization: Bearer $TOKEN" \
     "$BASE/api/employees?filter=active&sort=annualIncome&order=desc"

curl -s -H "Authorization: Bearer $TOKEN" \
     "$BASE/api/employees/1003?includeSalaries=true"
```

## Services

| Method | Path                    | Auth | Purpose |
|--------|-------------------------|------|---------|
| `GET`  | `/`                     | no   | Self-describing index of the services |
| `POST` | `/api/login`            | no   | Log in, receive a bearer token |
| `POST` | `/api/logout`           | yes  | End the session |
| `GET`  | `/api/session`          | yes  | Describe the current session |
| `GET`  | `/api/employees`        | yes  | Employee list - filtered and sorted |
| `GET`  | `/api/employees/{code}` | yes  | One employee: general details, address, optionally salaries |
| `GET`  | `/api/health`           | no   | Liveness, cache and session counters |

The token is sent back as `Authorization: Bearer <token>` (`X-Auth-Token: <token>` also works).

### `GET /api/employees`

| Parameter                  | Values                                | Default | Notes |
|----------------------------|---------------------------------------|---------|-------|
| `filter`                   | `all`, `active`, `range`              | `all`   | `active` keeps `IS_ACTIVE = 1` |
| `fromRecord`, `toRecord`   | whole numbers, 1-based, inclusive     | -       | Required by `filter=range` |
| `sort`                     | `code`, `name`, `annualIncome`        | `code`  | |
| `order`                    | `asc`, `desc`                         | `asc`   | |

**Record numbers** are positions inside the requested ordering, so `fromRecord=3&toRecord=6`
returns the 3rd to 6th employee of the sorted list, and each row carries its own `recordNumber`.
Rows tied on the sort key are broken by `CODE` ascending, so paging through ranges is stable.

**Annual income** is the sum of `SALARIES.TOTAL` over the employee's most recent year in the data.
It is defined once, by the `V_ANNUAL_INCOME` view, so sorting the list and reading one employee
always agree. An employee with no salary rows has an annual income of `0`.

```jsonc
{
  "filter": { "type": "range", "fromRecord": 1, "toRecord": 2 },
  "sort":   { "by": "annualIncome", "order": "desc" },
  "returned": 2,
  "totalMatching": 12,
  "employees": [
    { "recordNumber": 1, "code": 1012, "name": "Hadas Ben-Ami", "active": true, "annualIncome": 317623.05 },
    { "recordNumber": 2, "code": 1004, "name": "Noa Friedman",  "active": true, "annualIncome": 289175.11 }
  ]
}
```

### `GET /api/employees/{code}`

`includeSalaries=true` adds the salary list; without it only the general details and the address are
returned.

```jsonc
{
  "employee": {
    "general": { "code": 1003, "name": "Daniel Mizrahi", "active": true, "annualIncome": 173162.70 },
    "address": { "street": "Ha-Nassi", "number": "7", "city": "Haifa", "country": "Israel" },
    "salaries": [
      { "month": "2025-01", "date": "2025-01-01", "gross": 18700.00, "tax": 4138.80, "total": 14561.20 }
    ],
    "salariesCount": 12
  },
  "includeSalaries": true
}
```

### Errors

Every failure answers with the same shape, carrying the request id that also appears in the log:

```json
{ "error": { "status": 401, "message": "Invalid username or password", "requestId": "886c53b1" } }
```

`400` malformed request, `401` missing/expired token or bad credentials, `404` unknown employee or
service, `500` unexpected failure.

## The cache

Employee details are cached for **2 hours**, as required
([`TtlCache`](src/main/java/com/ivory/employees/cache/TtlCache.java)). The cached entry always holds
the salary list, so a request with and a request without salaries share one entry. Expired entries
are dropped lazily on read and in bulk by a housekeeping task every 5 minutes; `/api/health`
reports entries, hits and misses. The employee *list* is not cached - it depends on the filter, the
sort key and the range.

## The database

H2, embedded, reached over plain JDBC. The schema is created on every start
([`SchemaInitializer`](src/main/java/com/ivory/employees/db/SchemaInitializer.java)):

```sql
EMPLOYEES (CODE, NAME, IS_ACTIVE, ADDRESS_STREET, ADDRESS_NUMBER, ADDRESS_CITY, ADDRESS_COUNTRY)
SALARIES  (EMPLOYEE_CODE, "MONTH", GROSS, TAX, TOTAL)
APP_USERS (USERNAME, SALT, PASSWORD_HASH, CREATED_AT)   -- credentials for LOGIN
V_ANNUAL_INCOME                                          -- view: annual income per employee
```

`MONTH` is quoted because it is a reserved word in H2 2.x; the column keeps exactly the name the
specification gives it.

### Loading the data files

`data/Employees.dat` and `data/Salaries.dat` are semicolon-delimited ASCII files whose first record
holds the field names. They are imported on the first start, when `EMPLOYEES` is empty:

```
CODE;NAME;IS_ACTIVE;ADDRESS_STREET;ADDRESS_NUMBER;ADDRESS_CITY;ADDRESS_COUNTRY
1001;Avi Cohen;1;Herzl;12;Tel Aviv;Israel
```

To import the real files, drop them into `data/` and start with `-Demployees.data.reload=true`.
The reader ([`DatFile`](src/main/java/com/ivory/employees/db/DatFile.java)) is deliberately tolerant:
it trims values, skips blank lines, accepts short records, and accepts both the correct field names
and the spellings printed in the specification (`ADRESS_CITY`, `EMPLYEE_CODE`).

**Encoding** is worked out per file rather than assumed: a byte-order mark decides when there is
one (UTF-8, UTF-16LE, UTF-16BE), otherwise the file is read as UTF-8, and only if that fails is it
re-read as `windows-1255` - the Hebrew ANSI code page - with a warning naming the file. Set
`-Demployees.data.charset=windows-1252` (or any encoding) to override the guess. `MONTH` is accepted
as `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `dd.MM.yyyy`, `yyyy-MM`, `MM/yyyy` or `yyyyMM`;
`IS_ACTIVE` as `1/0`, `true/false` or `Y/N`. A record that cannot be read is logged and skipped
rather than aborting the whole import.

## Logging

Every request and its answer are logged, correlated by a short request id that also travels into the
error bodies (`java.util.logging`, one line per event,
[`LogFormatter`](src/main/java/com/ivory/employees/util/LogFormatter.java)):

```
2026-09-09 07:35:56.331 INFO  [d269b1ed] [admin] RequestLogFilter - --> GET /api/employees/1003 from 127.0.0.1
2026-09-09 07:35:56.339 INFO  [d269b1ed] [admin] EmployeeService  - Employee 1003 loaded from the database in 7 ms and cached for PT2H
2026-09-09 07:35:56.341 INFO  [d269b1ed] [admin] RequestLogFilter - <-- 200 GET /api/employees/1003 (10 ms)
```

`password` and `token` parameters are masked. Start with `-Demployees.log.level=FINE` to also log
the generated SQL and every answer body.

## Configuration

Each setting is read from a system property, then from the environment variable of the same name
upper-cased with `.` replaced by `_` (`EMPLOYEES_CACHE_TTL_MINUTES`), then from the default.

| Property                         | Default                                          | Meaning |
|----------------------------------|--------------------------------------------------|---------|
| `employees.port`                 | `8080`                                           | Listen port (embedded only) |
| `employees.db.url`               | `jdbc:h2:file:./data/db/employees;AUTO_SERVER=TRUE` | JDBC URL |
| `employees.db.user` / `.password`| `sa` / *(empty)*                                 | Credentials |
| `employees.data.dir`             | `./data`                                         | Where the `.dat` files live |
| `employees.data.charset`         | *(auto)*                                         | Encoding of the `.dat` files; empty means detect |
| `employees.data.reload`          | `false`                                          | Re-import the `.dat` files even when the tables hold rows |
| `employees.cache.ttl.minutes`    | `120`                                            | Cache retention time |
| `employees.session.ttl.minutes`  | `60`                                             | Session lifetime |
| `employees.auth.users`           | `admin:Aa123456!`                                | Seed users, `user:password[,user:password]` |
| `employees.json.pretty`          | `true`                                           | Pretty-print the answers |
| `employees.log.level`            | `INFO`                                           | `FINE` adds SQL and answer bodies |

Seed users are hashed with PBKDF2-HMAC-SHA256 (120k iterations, per-user random salt) and stored in
`APP_USERS` on the first start; changing the property later does not rewrite an existing user.

## Layout

```
src/main/java/com/ivory/employees/
├── Launcher.java          embedded-Tomcat entry point
├── cache/                 TtlCache - the 2-hour employee cache
├── config/                AppConfig - settings resolution
├── dao/                   EmployeeDao, UserDao - plain JDBC
├── db/                    Database, SchemaInitializer, DatFile, DataImporter
├── json/                  Json, JsonParser - dependency-free serialization/parsing
├── model/                 Address, Salary, EmployeeSummary, EmployeeDetails, EmployeeQuery
├── service/               EmployeeService (caching), AuthService (LOGIN)
├── util/                  ApiException, RequestContext, LogFormatter
└── web/                   WebApp + servlets and filters
```

## Notes on the implementation

- **SQL injection**: sort keys and directions are enums, so only whitelisted identifiers ever reach
  an `ORDER BY`; every value the caller supplies is a bound parameter.
- **Deterministic ordering**: a secondary sort on `CODE` makes ties stable, which is what makes the
  record-number range meaningful.
- **`BigDecimal` throughout** for money, and `NUMERIC` columns - no binary floating point.
- **Answers are ordered**: `LinkedHashMap` keeps the JSON fields in a fixed, readable order.
