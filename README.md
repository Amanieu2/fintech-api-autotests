# Fintech API Autotests

[![API tests](https://github.com/Amanieu2/fintech-api-autotests/actions/workflows/tests.yml/badge.svg)](https://github.com/Amanieu2/fintech-api-autotests/actions/workflows/tests.yml)
[![Allure report](https://img.shields.io/badge/Allure-report-orange)](https://Amanieu2.github.io/fintech-api-autotests/)

A REST API test automation framework in Java. It shows how I build regression suites for banking and fintech backends:
a clean API layer, independent tests, contract checks, integration tests against mocked third-party services,
and a CI pipeline that publishes an Allure report after each run.

**Live report:** https://Amanieu2.github.io/fintech-api-autotests/

## What's inside

| Suite | System under test | What it demonstrates |
|---|---|---|
| `booking` | Public [Restful-Booker](https://restful-booker.herokuapp.com/apidoc/index.html) API | CRUD coverage, token auth, negative cases, JSON Schema contract validation, data-driven tests, search |
| `payments` | `PaymentGatewayClient`, a client for an external bank payment gateway, with the gateway emulated in **WireMock** | Retries on 5xx and timeouts, **idempotency key reuse across retries** (no double charges), mapping of gateway error codes, local validation, malformed responses |

The `payments` suite reflects real core-banking integration work: an outgoing transfer must never be sent twice,
must survive a flaky gateway, and must turn every gateway error into a clear domain error.

## Tech stack

Java 21 · Maven · JUnit 5 · Rest Assured · AssertJ · WireMock 3 · JSON Schema Validator · Allure · GitHub Actions

## Project structure

```
src/main/java/.../payments      PaymentGatewayClient (code under test), DTOs, domain errors
src/test/java/.../booking       BookingApi (API layer), test data factory, CRUD and auth tests
src/test/java/.../payments      WireMock-based integration tests for the gateway client
src/test/java/.../config        Config: overridable via -D properties or env variables
src/test/resources/schemas      JSON Schema contracts
.github/workflows/tests.yml     CI: on push, PRs, nightly; publishes the Allure report to GitHub Pages
```

## Running locally

```bash
mvn test                       # all suites
mvn test -Dgroups=payments     # offline suite, WireMock only
mvn test -Dgroups=booking      # public API suite
mvn allure:serve               # open the Allure report
```

Configuration (system property / env variable):

| Property | Env | Default |
|---|---|---|
| `booking.baseUrl` | `BOOKING_BASE_URL` | `https://restful-booker.herokuapp.com` |
| `booking.user` | `BOOKING_USER` | `admin` |
| `booking.password` | `BOOKING_PASSWORD` | `password123` |

## Design choices

- **API layer separate from tests.** Tests call `api.create(booking)`, not raw HTTP. If an endpoint changes, I fix it in one place.
- **Each test creates its own data.** No shared state and no fixed order, so tests can run in parallel.
- **Records as DTOs.** Equality checks compare whole objects, so no field is silently left unchecked.
- **Allure on every request.** Each HTTP call is attached with its request and response, so a failure can be diagnosed from the report without re-running.
- **Nightly scheduled run.** It catches environment drift between commits.

## About me

Petr Kusevich, QA Lead with a focus on backend and API test automation in banking and fintech
(core banking, ESB integrations, card processing, SWIFT, JMS message queues).
