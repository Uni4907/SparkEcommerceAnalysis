# 数据库设计

## 存储分工

- MySQL：平台元数据、字段映射、任务状态和结果索引。
- Hive：行为与订单标准大表，按 `dataset_id` 分区。
- 文件系统：任务日志、CSV/JSON 分析结果和 manifest。

正式验收禁止以内存 Repository 代替 MySQL。连接失败会直接报错，不会静默降级。

## MySQL 表

### dataset

记录数据集名称、类型、原文件绝对路径、文件大小、行数、状态、格式和时间。正式淘宝文件只登记路径，不存入数据库或复制到 B。

### dataset_field_mapping

复合主键为 `(dataset_id, source_index)`。保存源字段、标准字段、推断类型、置信度、确认状态和样例 JSON，并通过外键关联 `dataset`。

### analysis_job

保存 `PENDING/RUNNING/SUCCESS/FAILED`、请求模块、日志路径、PID、退出码、错误信息及生命周期时间。通过 `dataset_id` 关联数据集，并建立数据集/状态索引。

### result_version

每个成功任务的每个模块对应一条版本记录，保存 `dataset_id`、`job_id`、模块、结果路径和时间。latest SUCCESS 查询会关联成功任务并按版本 id 倒序取第一条。

完整 DDL 位于 `sql/v3_mysql.sql`。应用启动时使用 `CREATE TABLE IF NOT EXISTS` 幂等初始化四张表；目标 database 本身必须预先存在。

## Hive 表

- `ecommerce_v3.dwd_behavior_event`
- `ecommerce_v3.dwd_order`

DDL 位于 `sql/v3_hive.sql`。Worker 也会幂等创建相同 schema，随后使用动态分区 `INSERT OVERWRITE` 写入当前 `dataset_id`，实现：

- 同一数据集重试不重复追加；
- 不同数据集分区互不覆盖；
- SQL 查询必须显式带 `dataset_id` 条件。

## 一致性边界

MySQL 与文件/Hive 之间没有分布式事务。Worker 先完成 Hive 和结果文件，生成 manifest 后，主服务再登记 `result_version` 并把 job 标记为 `SUCCESS`。没有 manifest 或子进程非零退出时，不登记成功版本。

## 正式验收记录

2026-09-14 直接查询 MySQL 得到：

- 四张表全部存在；
- 样本数据集 `dataset_id=1`：3 个任务、2 个成功结果版本；
- 正式数据集 `dataset_id=2`：3 个任务，其中 job 4 FAILED、job 5/6 SUCCESS；
- 正式数据结果版本 10 条，overview 历史版本分别来自 job 5 和 job 6。

