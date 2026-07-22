# Kronos ORM Android 示例

该仓库通过一个完整的 CRUD 应用演示 **Kronos ORM 0.3.0 在 Android 上的使用方式**。示例以 Markdown 笔记本作为业务场景，使用 Android 原生 View、SQLite 和 Markwon 实现。

## 功能

- 由编译器生成的 `KPojo` 模型和 `syncTable(...)` 表结构同步
- 基于 Android SQLite 的 Kronos 类型安全 insert、select、update 和 delete
- 带搜索、收藏、更新时间和字符统计的文档库
- 支持标题、粗体、斜体、列表、引用、代码和链接的 Markdown 编辑器
- 支持 CommonMark、删除线和表格的渲染预览
- 未保存修改确认和删除确认
- 纯本地 SQLite 存储，不申请网络权限

## Kronos 用法

`MarkdownDocument` 是 `KPojo` 数据模型，通过以下代码同步 SQLite 表结构：

```kotlin
database.table.syncTable(MarkdownDocument())
```

`DocumentRepository` 展示了：

- 使用 `insert().withId().execute()` 获取 SQLite 自增 ID
- 类型安全的 `select()` 和显式主键条件查询
- `update().set { ... }.by { it.id }.execute()` 更新文档
- `delete().by { it.id }.execute()` 删除文档

项目内的 `AndroidSQLiteDataSourceWrapper` 将 Android `SQLiteDatabase` 接入 Kronos，所有数据库操作在单线程后台执行器上运行。[Android SQLite 指南](https://kotlinorm.com/#/documentation/zh-CN/database/android-sqlite) 说明 Android/JVM 配置、wrapper 职责、事务和日志。

## 运行

可以用 Android Studio 打开此目录，也可以执行：

```bash
./gradlew :app:assembleDebug
```

安装到模拟器或设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

示例使用 Kotlin `2.4.0`、Android Gradle Plugin `8.13.2`、Kronos `0.3.0` 和 Markwon `4.6.2`。

SQLite 适配器支持 Android 事务块，具体事务行为见 Android SQLite 指南。
