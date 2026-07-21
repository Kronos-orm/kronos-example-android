# Kronos ORM Android Example

This repository demonstrates **Kronos ORM 0.2.4 on Android** through a complete CRUD application. The example uses a Markdown notebook as its business scenario, with Android platform views, SQLite, and Markwon.

## Features

- A compiler-generated `KPojo` model and `syncTable(...)` schema synchronization
- Typed Kronos insert, select, update, and delete operations backed by Android SQLite
- Document library with search, favorites, timestamps, and character counts
- Markdown editor with heading, bold, italic, list, quote, code, and link actions
- Rendered preview with CommonMark, strikethrough, and table support
- Unsaved-change confirmation and delete confirmation
- Local SQLite storage with no network permission

## Kronos usage

`MarkdownDocument` is a `KPojo` model. The application registers it at startup and keeps the SQLite schema synchronized through:

```kotlin
database.table.syncTable(MarkdownDocument())
```

`DocumentRepository` demonstrates:

- `insert().withId().execute()` for generated SQLite identity IDs
- typed `select()` queries and explicit primary-key conditions
- `update().set { ... }.by { it.id }.execute()`
- `delete().by { it.id }.execute()`

Android's `SQLiteDatabase` is connected to Kronos through the included `AndroidSQLiteDataSourceWrapper`. Database work runs on a single background executor.

## Run

Open this directory in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

Install the APK on an emulator or device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The sample uses Kotlin `2.4.0`, Android Gradle Plugin `8.13.2`, Kronos `0.2.4`, and Markwon `4.6.2`.

The SQLite adapter supports Android transaction blocks but does not expose JDBC savepoints, isolation levels, or timeouts.
