# V3.2 测试报告

测试日期：2026-09-14

## 测试结论

V3.1 核心验收没有退化；V3.2 的单元测试、双数据集 UI、正式 MySQL 持久化、Hive 隔离、结果版本、REST API 和浏览器验收均通过。五项 Legacy 指标定义未改变且精确一致，不需要生成 `METRIC_DIFF_REPORT.md`。

## 环境

- Windows
- Java 17
- Spark Maven 依赖 3.5.0
- 本机 spark-sql 3.5.1（仅用于验收查询）
- Hadoop 3.3.6 Windows 原生库
- MySQL 8.x 服务
- 全量 Worker Java 堆：12GB
- 分支：`v3.2-ui`

## V3.2 增量验收

- 订单样例执行 Job #7，8 个支持模块全部 SUCCESS 并生成 8 个结果版本。
- 正式 3.67GB 数据未重跑，继续使用 Job #5/#6 的 latest SUCCESS。
- 所有双数据集模块通过 REST 返回，返回行 `dataset_id` 检查无串数据。
- 服务重启后仍读取 2 个数据集、7 个任务、Job #7 的 8 个版本和 Job #4 的失败日志。
- Chrome 1366×768 验收 12 个页面状态，无横向溢出。
- Browser Console：0 error；Uncaught Exception：0。
- 截图和机器可读结果见 `docs/screenshots/v3.2/`，完整清单见 `UI_ACCEPTANCE_V3_2.md`。

## 自动化测试

执行 Maven/JUnit 测试，4 项全部通过：

1. 字段别名、类型和样例推荐。
2. 订单数据能力检测及 funnel 禁用。
3. 淘宝行为与订单双适配器、可用模块和结果隔离。
4. 实际执行 `sql/v3_hive.sql`，写入两个标准模型并验证 `dataset_id`。

前端 `platform.js` 通过 Node 语法检查，Git diff whitespace 检查通过。

## 双数据集测试

### 淘宝行为样本

- 无表头五列适配成功。
- 启用行为、漏斗、商品、类目、时间、分群、复购和概览能力。
- 9 个适用模块成功生成版本结果。

### 订单样本

- 有表头八列映射成功。
- 启用订单、销售、收入、商品、类目、时间、分群、复购和概览能力。
- 8 个适用模块成功。
- funnel 正确禁用，强制请求返回 HTTP 400。

## MySQL 持久化测试

应用以 `repository=mysql` 启动并自动创建：

- `dataset`
- `dataset_field_mapping`
- `analysis_job`
- `result_version`

样本 `dataset_id=1` 的 job 1 因验收环境 Maven 仓库路径错误保留为 FAILED；修复子进程继承 `maven.repo.local` 后，job 2/3 均 SUCCESS。两次 `sales` 结果均保留，latest SUCCESS 从 job 2 切换到 job 3。服务重启后数据集、8 条映射、任务和版本仍可读取。

## 正式全量回归

源文件：外部原始 `UserBehavior.csv`（未纳入仓库）

- 文件大小：3,672,347,465 字节
- 注册方式：原地绝对路径登记，无复制
- 正式数据集：`dataset_id=2`
- 字段映射：5/5 已确认
- 预扫描行数：`-1`，证明 Dataset Manager 未为注册而全表扫描

任务记录：

| Job | 状态 | 时间 | 说明 |
|---:|---|---|---|
| 4 | FAILED | 18:22:55–18:24:07 | 首次生产 Hive schema 未初始化；失败、退出码和日志正确落库 |
| 5 | SUCCESS | 18:26:14–18:29:26 | 修复后执行 9 个正式行为模块，100,150,807 行 |
| 6 | SUCCESS | 18:31:18–18:33:14 | 重跑 overview，验证 Hive 幂等覆盖和版本切换 |

job 5 manifest 包含 9 个模块；job 6 生成新的 overview 版本。正式数据共有 10 条结果版本，overview 历史版本来自 job 5 和 job 6，latest SUCCESS 为 job 6。

## 指标对比

| 指标 | Legacy | V3 | 差值 | 结论 |
|---|---:|---:|---:|---|
| 总记录数 | 100,150,807 | 100,150,807 | 0 | PASS |
| 用户数 | 987,994 | 987,994 | 0 | PASS |
| PV | 89,716,264 | 89,716,264 | 0 | PASS |
| 购买数 | 2,015,839 | 2,015,839 | 0 | PASS |
| 购买用户数 | 672,404 | 672,404 | 0 | PASS |

## Hive 隔离与幂等性

首次成功和重跑后分别使用 spark-sql 查询：

```text
SHOW PARTITIONS dwd_behavior_event -> dataset_id=2
dataset_id  row_count  users  pv  buys  buyers
2           100150807  987994 89716264 2015839 672404
```

重跑后行数未翻倍，且没有其他正式分区，证明动态分区覆盖和 `dataset_id` 隔离生效。

## REST 与 Dashboard

- `/api/health`：UP，repository=mysql。
- Dataset、Mapping、Capability、Job、Log、latest Analytics 均通过实际调用。
- `platform.html`、`platform.js`、`platform.css`、Legacy `index.html` 均返回 HTTP 200。
- latest overview 经 REST 返回 `dataset_id=2/job_id=6` 和 100,150,807 行。

## 日志与警告

- FAILED job 保留非零退出码、错误信息和独立日志。
- SUCCESS job 退出码为 0，结果文件与 manifest 完整。
- Windows 本地 Spark 出现 SLF4J 无绑定和 Hadoop 权限命令提示；没有影响任务状态、Hive 行数或指标。

## 最终判定

所有必须验收项通过。无静默内存降级、无原始大文件复制、无指标差异、无未说明的 Legacy 核心功能丢失。
