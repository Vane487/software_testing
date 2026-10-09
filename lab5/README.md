# Лабораторна робота №5 — Testcontainers і WireMock

Група 1, варіант 04. Цей модуль використовує незмінений SUT B із лабораторної №4 та
перевіряє його в повністю контейнеризованому integration test environment.

## Що реалізовано

- PostgreSQL 17 автоматично запускається через Testcontainers;
- WireMock автоматично запускається окремим контейнером;
- обидва контейнери використовують випадкові runtime-порти;
- параметри JDBC беруться через `POSTGRES.getJdbcUrl()`, `getUsername()` і `getPassword()`;
- migration `migrations/V1__create_tables.sql` застосовується контейнером автоматично;
- `HttpEnrollmentPolicy` виконує реальний `POST /enrollment-policy` до WireMock;
- перевіряються method, endpoint, кількість запитів і JSON body;
- після Service-операцій стан повторно читається з реальної PostgreSQL;
- PostgreSQL та WireMock очищаються до і після кожного тесту.

## Сценарії

`EnrollmentServiceContainerIntegrationTest` містить 5 тестів:

1. контейнери запущені, runtime-конфігурація доступна, migration застосована;
2. HTTP policy дозволяє зарахування — знижка 12% і запис перевіряються в PostgreSQL;
3. HTTP policy повертає `allowed=false` — запис не створюється, HTTP request є;
4. HTTP policy повертає `503` — виникає помилка, запис не створюється, HTTP request є;
5. курс заповнений — локальна відмова, новий запис відсутній, HTTP request відсутній.

## Запуск

Потрібні Java 21, Maven і запущений Docker Desktop. Локальна PostgreSQL не потрібна.

```sh
cd lab5
mvn test
```

Testcontainers сам запускає і видаляє контейнери. Повторний запуск повинен давати той самий
результат без ручного створення БД, таблиць або WireMock mappings.
