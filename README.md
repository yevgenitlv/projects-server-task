# Employee Server

A Java web server exposing employee and salary services over HTTP/JSON.

Plain Jakarta Servlet application — **no Spring, no Spring Boot, no ORM, no JSON library**. Three
dependencies in total: the Servlet API (supplied by the container), embedded Tomcat (only so the app
can run standalone) and the H2 database driver.

The whole project lives in one module:

```
employees-web/          the server — sources, tests, sample data, full documentation
```

## Quick start

```bash
cd employees-web
mvn compile exec:java            # http://localhost:8080
```

```bash
BASE=http://localhost:8080

TOKEN=$(curl -s -X POST $BASE/api/login \
          -H 'Content-Type: application/json' \
          -d '{"username":"admin","password":"Aa123456!"}' | grep -oP '"token": "\K[^"]+')

curl -s -H "Authorization: Bearer $TOKEN" \
     "$BASE/api/employees?filter=active&sort=annualIncome&order=desc"
```

## What it does

- **Login** — registration and authentication before any service may be used.
- **Employee list** — filtered (all / only active / by record-number range) and sorted ascending or
  descending by employee code, employee name or annual income.
- **Employee data** — general details, address details and an optional salaries list, served from a
  cache with a two-hour retention.
- **Structured JSON** responses and a readable log of every request and its answer.

Employee and salary data is loaded from the `Employees.dat` / `Salaries.dat` ASCII files into an
embedded H2 database (`EMPLOYEES`, `SALARIES`).

## Documentation

[`employees-web/README.md`](employees-web/README.md) covers the services and their parameters, the
JSON shapes, the cache, the database schema, the data-file loader (encoding, delimiter and Hebrew
handling), logging and the full configuration table.
