# Захист лабораторної роботи №9

## 1. CI configuration і структура pipeline

Показати `.github/workflows/lab9-ci.yml`.

Pipeline має три послідовні jobs:

```text
build → unit-tests → integration-tests
```

Кожен job виконується на GitHub-hosted Ubuntu runner із Java 21.

## 2. Автоматичний запуск

У секції `on` показати `push` і `pull_request`. `workflow_dispatch` доданий лише
як додатковий спосіб безпечно продемонструвати failure.

## 3. Build, unit та integration tests

- build: Maven компілює та створює JAR;
- unit: `EnrollmentServiceUnitTest` ізолює Service через Mockito та перевіряє
  знижки, валідацію, повернення й допуск;
- integration: `EnrollmentRegressionIT` запускає PostgreSQL через Testcontainers,
  застосовує migration і перевіряє Service + repositories + database.

## 4. Успішний pipeline

Показати зелений run після звичайного push/PR. Усі три jobs мають статус
`Success`.

## 5. Навмисна помилка → failed pipeline

Запустити workflow вручну з `demonstrate_failure=true`. Це передає
`-Ddemo.failure=true` і активує `IntentionalFailureDemoTest`.

У workflow немає `continue-on-error`, тому Maven повертає exit code 1, job і
весь pipeline отримують статус `Failure`.

## 6. Причина failure

Відкрити лог `Run unit tests` або artifact `lab9-unit-test-report-*`. Там видно:

- клас `IntentionalFailureDemoTest`;
- метод `intentionalFailure_makesPipelineFailForDemonstration`;
- повідомлення про навмисну помилку.

## 7. Test report та artifact

Показати секції `actions/upload-artifact@v4`. Зберігаються application JAR,
unit test report та integration test report. `if: always()` дозволяє отримати
звіт навіть після test failure.

## 8. Терміни

- **CI** — автоматичне збирання і тестування після змін у репозиторії.
- **Pipeline** — повний ланцюжок автоматичних перевірок.
- **Job** — окремий крок pipeline, наприклад unit tests.
- **Trigger** — подія запуску: push, pull request або manual dispatch.
- **Runner** — машина/середовище, де GitHub виконує job.
- **Artifact** — збережений результат job: JAR, XML або текстовий test report.

## Короткий спіч

> GitHub Actions автоматично запускає pipeline після push або pull request.
> Спочатку Maven збирає Java-проєкт, потім запускаються unit tests, а після них
> integration tests із PostgreSQL у Testcontainers. Невдалі тести не
> ігноруються. JAR і test reports зберігаються як artifacts. Для демонстрації
> failed pipeline є керований тест, який активується лише параметром ручного
> запуску; у звичайному автоматичному pipeline всі перевірки проходять.
