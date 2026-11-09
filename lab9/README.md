# Лабораторна робота №9 — CI/CD and Continuous Testing

Група 1, варіант 04. Для Java SUT B налаштовано GitHub Actions pipeline:
`.github/workflows/lab9-ci.yml`.

## Автоматичні triggers

Pipeline запускається після:

- `push`, який змінює `lab9/**` або сам workflow;
- `pull_request` із такими змінами;
- ручного `workflow_dispatch` для контрольованої демонстрації failed pipeline.

Отже, workflow не залежить лише від ручного запуску.

## Структура pipeline

1. **Build application** — `mvn -B -DskipTests package`.
2. **Unit tests** — `EnrollmentServiceUnitTest` та керований
   `IntentionalFailureDemoTest`.
3. **Integration tests** — стабільний `EnrollmentRegressionIT` із реальною
   PostgreSQL у Testcontainers та Flyway migration.

Jobs пов'язані через `needs`: integration запускається лише після успішних unit
tests. Немає `continue-on-error`, `|| true` або іншого приховування failure.

## Artifacts

Після pipeline зберігаються:

- зібраний application JAR;
- XML/TXT unit test reports із `target/surefire-reports`;
- XML/TXT integration test reports із `target/surefire-reports`.

Test reports завантажуються з `if: always()`, тому звіт failing unit test також
доступний для визначення причини помилки.

## Успішний pipeline

Звичайний push або pull request запускає pipeline з
`DEMO_FAILURE=false`. Усі jobs мають завершитися зеленим status.

Локальний еквівалент:

```bash
./scripts/run-ci-locally.sh
```

## Failed pipeline

Без редагування production-коду:

1. у GitHub відкрити вкладку **Actions**;
2. вибрати **Lab 9 CI and Continuous Testing**;
3. натиснути **Run workflow**;
4. встановити `demonstrate_failure = true`;
5. запустити workflow.

Тоді системна властивість `demo.failure=true` активує
`IntentionalFailureDemoTest`. Unit job стає червоним, а лог і test report містять
повідомлення:

```text
Навмисна помилка для демонстрації failed CI pipeline
```

Це контрольована демонстрація. При автоматичних push/PR test вимкнений і не
впливає на нормальний pipeline.

Локально той самий failure можна побачити командою:

```bash
mvn -B \
  -Dtest=EnrollmentServiceUnitTest,IntentionalFailureDemoTest \
  -Ddemo.failure=true \
  test
```

Команда навмисно поверне ненульовий exit code.

Докладний порядок захисту знаходиться у `DEFENSE_GUIDE.md`.
