# Legacy B 审计报告

审计对象：当前 Git 仓库

计划中给出的 `spark` 子目录属于上级 Git 工作树；本次同时审计项目根中的文档、图片和报告。审计阶段未修改历史代码、数据、SQL 或配置。

审计日期：2026-09-14

## 1. 基本信息

| 项目 | 结果 |
|---|---|
| Git 根目录 | 当前 Git 仓库根目录 |
| 当前分支 | `master`，HEAD `b2a144a` |
| 历史 | 9 个提交，包含 baseline、模块化、性能、聚类、全量报告等演进证据 |
| 工作树 | 审计前已有 `spark/src/main/java/com/uni/Main.java` 一处未提交注释文字变化；不是本次审计造成 |
| 非 `.git` 文件数 | 126 |
| 非 `.git` 文件总大小 | 228,481,155 bytes |
| 主要技术栈 | Java 17、Maven、Spark 3.5.0、Spark SQL、Spark MLlib、原生 HTML/CSS/JS、ECharts、JDK HttpServer |

## 2. 完整性

| 能力 | 状态 | 证据 |
|---|---|---|
| README / 运行文档 | 已实现 | `docs/README.md`、运行说明、模型口径、开发日志 |
| CSV 读取与清洗 | 部分实现 | 读取、列名、类型转换和时间窗口处理已实现；尚无通用校验/映射 |
| Spark 分析 | 已实现 | 10 个分析类 + KMeans + 主编排 |
| Hive | 未实现 | 依赖、配置、建表或查询代码均不存在 |
| MySQL / JDBC | 未实现 | 驱动、连接、DDL、写入代码均不存在 |
| 后端 | 部分实现 | 仅有静态文件 HTTP 服务，无业务 API |
| 前端 | 已实现（单数据集） | 8 个分析标签页，读取静态 `data/*.json` |
| 测试 | 未实现 | `src/test/java` 为空 |
| 脚本 | 未实现 | 没有 `.bat`、`.ps1` 或部署脚本 |
| 报告 | 已实现 | 有全量真实数据毕业设计报告初稿 |

## 3. 工程结构

核心源代码包含 17 个 Java 文件：

- `Main`：顺序编排分析阶段。
- `BaselineMain`：A 的 `Main.java` 完整副本，仅修改类名；差异为 1 行。
- `AnalysisContext` / `AnalysisConfig`：共享 Spark 上下文和运行参数。
- 10 个分析器：质量、活跃度、画像、价值、规则分群、群体差异、KMeans、商品、行为链路、时间趋势。
- `ResultExporter`：结果写成单个 CSV，并为小表生成 JSON。
- `WebAssets`：复制静态资源。
- `DashboardServer`：提供静态文件 HTTP 服务。

`spark/output/data` 中现有 33 个 CSV 与 27 个 JSON，合计约 228 MB。大文件包括 `user_segment.csv`、`user_profile.csv`、行为路径明细与 `user_cluster.csv`。

## 4. 实际运行能力验证

### 本次验证

- 在临时审计副本中使用 JDK 17、Maven 离线依赖执行 `mvn compile`：通过。
- 从临时编译产物启动 `DashboardServer`，读取历史 `output`：启动通过。
- HTTP 请求 `/index.html`：200，5,444 bytes。
- HTTP 请求 `/data/overall_stats.json`：200，265 bytes。

### 历史全量运行证据

现有结果文件、开发日志与报告互相印证一次全量运行：

- 总记录数 100,150,807；用户数 987,994。
- `pv` 89,716,264；`cart` 5,530,446；`fav` 2,888,258；`buy` 2,015,839。
- 画像、分群、聚类用户数均为 987,994。
- 各阶段计时与缓存对比结果已导出。

本次审计没有重跑 3.67 GB 全量 Spark 流程，避免在阶段门槛前覆盖历史输出；因此结论区分“本次编译/HTTP 验证”和“历史全量输出证据”。

### 当前不能验证/不存在

- Hive 建表：没有实现。
- MySQL 连接与写入：没有实现。
- Flask：没有实现，项目为 Java 技术栈。
- REST API：没有实现；当前 HTTP 服务只提供静态文件 GET。

## 5. 历史修复与应保留能力

- A 原始代码以 `BaselineMain` 留档，便于指标回归。
- 数据路径和输出路径已改为 JVM 参数，但默认值仍是本机绝对路径。
- Spark 时区固定为 `Asia/Shanghai`，warehouse 目录可配置。
- 文档记录 Java 17 模块开放参数、Windows Hadoop 本地库和 12 GB 堆建议。
- 本机 `HADOOP_HOME=D:\Environment\hadoop-3.3.6`，`winutils.exe` 与 `hadoop.dll` 均存在。
- 结果导出已处理 Spark 分片合并、UTF-8、JSON 数值和字符串转义。
- 静态文件服务包含路径规范化和目录穿越防护。
- 画像、分群与聚类包含行数/空值硬校验。
- 前端已有懒加载与尺寸重算的历史修复。

## 6. 已识别问题与风险

- 当前终端默认 Java 8；必须显式选 JDK 17。
- 项目没有测试框架和自动化回归，现有“测试通过”主要依赖历史运行证据。
- `inferSchema` 不适合作为平台输入契约，应以映射后显式 schema 替代。
- 默认数据路径绑定用户名和桌面目录。
- 结果文件按固定名称覆盖，没有 `dataset_id`、`job_id` 或版本目录。
- 主流程同步执行；没有任务队列、状态、日志 API 或失败恢复。
- `DashboardServer` 使用只含端口的 `InetSocketAddress`，实际可能监听所有网卡，但提示文本只显示 127.0.0.1；迁移时应显式绑定 loopback 或允许配置。
- 数据集没有会话 ID，当前 Funnel 是 `(user_id,item_id)` 的近似链路，不能表述为严格会话漏斗。
- B 的 `data` 目录为空，运行依赖 A 的外部原始数据路径。
- 审计前工作树已有一处未提交注释变化，备份时必须原样保留并明确纳入快照。

## 7. 资产分类

| 资产 | 分类 | 迁移方向 |
|---|---|---|
| `BaselineMain` | KEEP | 永久保留为 A 指标基线 |
| 分析类中的业务口径 | ADAPT | 改为只读取标准行为/订单模型，并增加 `dataset_id`,`job_id` |
| `AnalysisContext` / `Main` | REFACTOR | 拆为适配、能力、分析注册和编排层 |
| `ResultExporter` | REFACTOR | 保留格式能力，写入版本目录/持久化结果元数据 |
| `DashboardServer` | REFACTOR | 扩展为受约束的 REST + 静态服务，异步触发任务 |
| ECharts 图表、CSS、布局 | ADAPT | 保留视觉资产，改为 dataset-aware Analytics 页面 |
| 全量 `output` | KEEP | 作为 Legacy 回归基准，不迁入版本化运行目录 |
| 运行、模型口径、开发日志、报告 | MERGE | 合入 V3 文档与论文材料 |
| `target`, `.idea`, 重复的 `output` 静态资源 | DROP | 不作为源码资产迁移 |

## 8. 审计结论

B 是明确的 V3 主工程基础。它已经验证 Spark 分析口径、Windows 运行适配、结果导出和 ECharts 展示，并完整保留 A 的基线代码。平台化缺口很大，但以 B 增量演进比从 A 重建更可控。
