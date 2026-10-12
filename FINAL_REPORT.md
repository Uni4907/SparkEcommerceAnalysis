# V3.2 Final Report

日期：2026-09-14

## Final Decision

V3.2 已按批准方案完成：在不修改 Spark/Hive/MySQL 核心分析逻辑的前提下，将 V3 平台能力与 Legacy 成熟图表整合为一个中文主系统。正式 MySQL、Hive、双数据集、1 亿行既有结果、结果版本、REST 与浏览器验收全部通过。

`System Demo Ready: YES`

## Scope and Baseline

- 主工程：当前 Git 仓库根目录
- 主分支：`v3.2-ui`
- Legacy 可回滚分支：`legacy-completed`
- A：外部 Legacy A 工程（未纳入仓库）
- 正式文件：A 中 `data\UserBehavior.csv`，3,672,347,465 字节
- 数据策略：原地登记绝对路径，不复制、不修改 A。

## Legacy A Reused Assets

- 正式 `UserBehavior.csv`。
- 原始五列数据契约和字段说明。
- 最早开发日志与截图，作为论文演进资料。
- A 的原始 `Main.java` 已由 B 的 `BaselineMain.java` 保存为指标基线。

## Legacy B Reused Assets

- Git 历史、模块化 Java/Spark 工程与 Java 17 Windows 运行经验。
- 已验证的核心指标口径。
- `BaselineMain`、Legacy 分析类和 `spark/output` 历史结果。
- `ResultExporter` 的 CSV/JSON 导出能力。
- DashboardServer、ECharts、CSS 与 Legacy Dashboard。
- 论文、运行、模型口径和开发文档。

## Refactored Components

- `DashboardServer`：从静态文件服务扩展为 loopback REST + V3/Legacy 静态页面服务。
- `Main/AnalysisContext` 的平台职责：拆分为 Dataset、Mapping、Capability、Adapter、Job 和 Repository。
- `ResultExporter`：由固定文件名输出改为 `results/{dataset_id}/{job_id}` 版本目录和 manifest。
- 数据读取：Taobao 专用显式 Schema + Generic CSV Mapping，不再让通用分析直接依赖源列名。
- 任务执行：同步流程扩展为独立 Spark Worker 和持久化状态机。
- 标准数据：加入 Hive 行为/订单模型，按 `dataset_id` 动态分区覆盖。
- 平台元数据：加入正式 MySQL Repository，无静默内存降级。
- 前端：统一为平台总览、数据集管理、分析任务、分析中心、系统状态五项导航；详情、映射和任务详情改为内部工作区。

## V3.2 UI Acceptance

- 唯一主入口为 `/platform.html`；Legacy `/index.html` 保留但不在顶层导航。
- 主界面中文化，正式与订单数据集使用友好名称。
- Analysis 仅消费 V3 REST，模块由 Capability 动态控制。
- 淘宝数据展示行为、路径和趋势分析；订单数据展示销售分析，两套 UI 确实不同。
- 订单样例 Job #7 的 8 个模块成功；正式数据不重跑，复用 Job #5/#6。
- 12 个浏览器场景在 1366×768 下无横向溢出，控制台 0 error、0 uncaught exception。
- 完整证据见 `docs/UI_ACCEPTANCE_V3_2.md` 和 `docs/screenshots/v3.2/`。

## Dropped Components

- 不引入 Flask；它不符合 Legacy B 的统一 Java 技术路线。
- 不把开发用 InMemory Repository 当作正式部署方案。
- V3 不再依赖固定名称覆盖的 `spark/output/data/*.json` 作为结果源。
- `.idea`、`target`、runtime 和重复生成资源不纳入版本控制。
- 未验证的“Legacy 已有 Hive/MySQL”假设已删除，以实际实现与测试替代。

## NOT MIGRATED

以下 Legacy B 高级分析尚未改造为 V3 通用标准模型模块，但原代码、Legacy Dashboard 和历史输出仍保留：

- KMeans 用户聚类及聚类评估。
- A/F/R 用户价值模型和原有详细画像。
- 群体差异明细。
- 详细 `(user_id,item_id)` 行为路径明细。
- 时间异常窗口与热力分析。
- 缓存前后性能基准模块。

这些项目不属于本次 V3 多数据集核心验收的十个注册模块。未来迁移时必须先声明标准字段要求和 Capability，不可直接读取源列。

## Migration Risks

| 风险 | 控制 | 当前状态 |
|---|---|---|
| A/B 双代码源 | B 唯一主线，A 只读 | 已控制 |
| 大文件复制与预扫描 | 绝对路径登记，有界探测 | 已验证 |
| MySQL 伪持久化 | 正式模式显式 mysql，无回退，重启验证 | 已验证 |
| Hive 重试重复 | `dataset_id` 动态分区覆盖 | 已验证 |
| 结果覆盖 | dataset/job 版本目录和 latest SUCCESS | 已验证 |
| Spark 进程失控 | PID、退出码、日志和重启恢复 | 已验证 |
| 指标漂移 | Legacy 五项固定基准全量比较 | 差值为 0 |
| 网络暴露 | 默认绑定 127.0.0.1 | 已控制 |
| 严格漏斗误述 | 明确无 session_id，仅阶段汇总 | 已记录 |
| JDK HttpServer 扩展上限 | API 显著复杂后再评估 Spring Boot | 接受 |

## Regression Test

### Automated and dual-dataset tests

- Maven/JUnit：4 tests，0 failures，0 errors。
- 淘宝行为样本：9 个模块成功。
- 订单样本：8 个模块成功，funnel 正确禁用并返回 HTTP 400。
- PowerShell 启动脚本、Node JavaScript 语法和 Git diff 检查通过。

### MySQL integration

- 四张表真实创建。
- Dataset、5/8 条映射、FAILED/SUCCESS job、日志和 result_version 均真实持久化。
- 服务重启后仍能读取 2 个数据集、6 个任务和 latest SUCCESS。
- 样本 sales 和正式 overview 均保留两个成功历史版本。

### Formal 100M-row regression

- 正式数据：`dataset_id=2`
- 全模块成功：`job_id=5`，9 个模块，100,150,807 行
- 幂等重跑：`job_id=6`，latest overview 切换成功
- 正式 FAILED 证据：`job_id=4`

| 指标 | Legacy | V3 | 差值 |
|---|---:|---:|---:|
| 总记录数 | 100,150,807 | 100,150,807 | 0 |
| 用户数 | 987,994 | 987,994 | 0 |
| PV | 89,716,264 | 89,716,264 | 0 |
| 购买数 | 2,015,839 | 2,015,839 | 0 |
| 购买用户数 | 672,404 | 672,404 | 0 |

定义未改变且差值均为 0，因此没有生成 `docs/METRIC_DIFF_REPORT.md`。

## Full-chain Acceptance

| 环节 | 证据 | 结果 |
|---|---|---|
| Dataset | A 文件原地注册，MySQL dataset 2 | PASS |
| Mapping | 5/5 字段确认并跨重启保存 | PASS |
| Capability | 8 类行为能力动态启用 | PASS |
| Hive Standard Model | 唯一 `dataset_id=2` 分区，100,150,807 行 | PASS |
| Analysis Job | job 5/6 SUCCESS，退出码 0 | PASS |
| Versioned Result | 10 条正式版本，历史 overview 保留 | PASS |
| REST API | health、dataset、mapping、job、log、analytics | PASS |
| Dashboard | V3 与 Legacy 页面 HTTP 200 | PASS |
| Failure handling | job 4 FAILED、日志和退出码保留，可重试 | PASS |
| MySQL persistence | 直接查询与跨重启恢复 | PASS |

## Documentation Delivered

执行计划要求的审计、迁移、状态、架构、数据模型、适配器、数据库、模块、API、部署、测试、用户、论文和答辩文档均位于 `docs/`，索引见 `docs/README.md`。

## Operational Notes

- 正式启动使用 `scripts/start-v3.ps1 -Repository mysql -EnableHive -MaxHeap 12g`。
- 凭据只通过 `PLATFORM_JDBC_URL/USER/PASSWORD` 环境变量提供。
- 当前服务默认仅本机访问，无认证/TLS，不应直接暴露公网。
- Windows 本地模式的 SLF4J/Hadoop 提示为非阻塞警告，成功任务退出码与数据均已核验。

## Final Acceptance

所有 V3.2 必需验收已真实通过；无内存仓库冒充、无大文件复制或重跑、无指标差异、无静默接受失败。未平台化的 Legacy 高级模块继续明确标记 `NOT MIGRATED`。

`System Demo Ready: YES`
