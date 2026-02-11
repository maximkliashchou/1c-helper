# Помощник в изучении языка 1С

Веб-приложение для изучения языка 1С: темы с теорией и иллюстрациями, задачи с проверкой кода через тесты, регистрация и личный кабинет.

## Стек технологий

- **Backend:** Java 17, Spring Boot 3.2, Spring Security (JWT), Spring Data JPA
- **База данных:** PostgreSQL
- **Frontend:** HTML, CSS, JavaScript (без фреймворков)
- **Контейнеризация:** Docker, docker-compose

## Функциональность

1. **Главная страница** — список тем, поиск по названию и описанию, переход к теме.
2. **Страница темы** — текст с иллюстрациями и примерами кода; ссылка на список задач (доступ к задачам только для авторизованных).
3. **Страница задач** — список задач по теме; переход к задаче.
4. **Страница задачи** — условие, поле для ввода кода (или загрузка файла), отправка на проверку. Код выполняется в OneScript; результат сохраняется. Список попыток отображается сразу. После **успешной сдачи** повторная отправка по этой задаче недоступна.
5. **Регистрация и авторизация** — JWT; страница профиля: данные пользователя, все попытки по всем задачам, смена почты, пароля и аватара.
6. **Роли:** пользователь (USER) и администратор (ADMIN). Админ может создавать и редактировать темы, добавлять задачи к темам и тесты к задачам (минимум 4 теста на задачу).

## Запуск проекта

### Вариант 1: Docker Compose (рекомендуется)

Требования: установленные Docker и Docker Compose.

```bash
# В корне проекта
docker compose up -d --build

# Приложение: http://localhost:8080
# PostgreSQL: localhost:5432 (пользователь 1chelper, пароль 1chelper, БД 1chelper)
```

Остановка:

```bash
docker compose down
```

Данные БД сохраняются в volume `pgdata`. Чтобы **сбросить базу** и заново инициализировать данные (админ, темы, задачи):

```bash
docker compose down -v
docker volume rm 1c-helper_pgdata   # если том остался
docker compose up -d
```

### Вариант 2: Локально (без Docker)

Требования: Java 17, Maven, установленный и запущенный PostgreSQL.

1. Создайте БД и пользователя:

```sql
CREATE USER 1chelper WITH PASSWORD '1chelper';
CREATE DATABASE 1chelper OWNER 1chelper;
```

2. Запуск приложения:

```bash
mvn spring-boot:run
```

Либо соберите JAR и запустите:

```bash
mvn package -DskipTests
java -jar target/1c-helper-1.0.0.jar
```

Если Maven выдаёт ошибку про `*.pom.part.lock`, выполните `mvn -U package` или удалите папку `~/.m2/repository/org/springframework/boot/spring-boot-starter-parent` и повторите сборку.

По умолчанию приложение ожидает PostgreSQL на `localhost:5432`. Параметры можно переопределить переменными окружения:

- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `SERVER_PORT` (по умолчанию 8080)
- `JWT_SECRET` — секрет для JWT (в продакшене обязательно задать свой)

После первого запуска (при пустой БД) создаётся пользователь **admin** / **admin** (логин/пароль) с ролью ADMIN и начальные данные: три темы («Введение в язык 1С», «Условия и циклы», «Процедуры и функции») с задачами и тестами.

## Документация по API

Базовый URL: `http://localhost:8080/api`

### Публичные (без авторизации)

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/topics` | Список тем |
| GET | `/topics/search?q=...` | Поиск тем |
| GET | `/topics/{id}` | Тема по id |
| GET | `/topics/{topicId}/tasks` | Задачи темы |
| GET | `/tasks/{id}` | Задача по id |
| POST | `/auth/register` | Регистрация (body: username, email, password) |
| POST | `/auth/login` | Вход (body: username, password) → в ответе `token` |

### С авторизацией (заголовок `Authorization: Bearer <token>`)

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/profile/me` | Текущий пользователь |
| GET | `/profile/user/{username}` | Профиль по имени |
| PUT | `/profile/me` | Обновить почту/пароль (body: email?, newPassword?) |
| POST | `/profile/me/avatar` | Загрузить аватар (multipart, поле `file`) |
| POST | `/tasks/{taskId}/submit` | Отправить решение (body: taskId, code) |
| GET | `/attempts/my` | Мои попытки по всем задачам |
| GET | `/attempts/my/task/{taskId}` | Мои попытки по задаче |
| GET | `/attempts/{id}` | Одна попытка (свой код) |

### Админ (роль ADMIN)

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/admin/topics` | Создать тему (body: title, description?, content?, imagePath?, sortOrder?) |
| PUT | `/admin/topics/{id}` | Обновить тему |
| DELETE | `/admin/topics/{id}` | Удалить тему |
| POST | `/admin/topics/{topicId}/tasks` | Создать задачу (body: title, condition, sortOrder?) |
| PUT | `/admin/tasks/{id}` | Обновить задачу |
| DELETE | `/admin/tasks/{id}` | Удалить задачу |
| POST | `/admin/tasks/{taskId}/tests` | Добавить тест (body: input?, expectedOutput) |

У каждой задачи должно быть **не менее 4 тестов**; иначе отправка решения вернёт ошибку.

## Проверка кода

Код пользователя **реально выполняется** в среде [OneScript](https://oscript.io) (интерпретатор языка 1С без платформы 1С:Предприятие). Для каждого теста скрипт запускается как процесс: на stdin подаётся вход теста, stdout сравнивается с ожидаемым выводом. В коде нужно использовать **Сообщить()** для вывода в консоль.

- В Docker-образ встроен OneScript (Linux x64). На Apple Silicon (M1/M2) при необходимости укажите для сервиса `app` в `docker-compose.yml`: `platform: linux/amd64`.
- Локально без Docker: установите [OneScript](https://oscript.io) и добавьте `oscript` в PATH, либо отключите реальный запуск в `application.yml`: `code-runner.use-real-runner: false` (тогда используется заглушка по вхождению строки в код).
- Параметры в `application.yml`: `code-runner.timeout-seconds` (таймаут в секундах), `code-runner.oscript-command` (команда запуска, по умолчанию `oscript`).

## Структура проекта

```
1c-helper/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
└── src/
    ├── main/
    │   ├── java/ru/chelper/
    │   │   ├── OneCHelperApplication.java
    │   │   ├── config/          # Security, Web, DataInitializer
    │   │   ├── controller/     # REST: Auth, User, Topic, Task, Admin
    │   │   ├── dto/
    │   │   ├── entity/          # User, Topic, Task, TestCase, Attempt
    │   │   ├── repository/
    │   │   ├── security/        # JWT, UserPrincipal, фильтр
    │   │   └── service/         # Auth, User, Topic, Task, Attempt, CodeExecution
    │   └── resources/
    │       ├── application.yml
    │       └── static/          # index.html, css/, js/
    └── test/
```

## Репозиторий

https://github.com/maximkliashchou/1c-helper

## Лицензия

Учебный проект.
