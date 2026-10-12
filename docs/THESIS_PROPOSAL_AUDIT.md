# SparkEcommerceAnalysis 毕设开题技术审计

> 审计日期：2026-10-11  
> 审计对象：当前 Git 仓库  
> 当前分支：`v3.2-ui`，HEAD `6d09f68`  
> 审计方式：文档、Git 状态、Java/JavaScript/SQL 源码、自动化测试源码、样本文件及正式数据文件有限头部检查。未启动 Spark、Hive、MySQL，未执行 Maven 测试，未扫描或重新处理 1 亿行正式数据。  
> 变更边界：本次仅新增本报告，未修改 Java 源码、配置、数据库或原始数据，未切换分支、未提交代码。

## 0. 审计结论摘要

1. V3.2 已经是一个具备 Dataset 注册、字段映射、Capability 检测、双标准模型、独立 Spark Worker、Hive 分区、MySQL 元数据、版本化结果、REST 与 Dashboard 的完整平台。仓库留有单元/双数据集/正式 1 亿行/浏览器验收记录；本次遵守限制，未重新运行这些服务与测试。[依据：`FINAL_REPORT.md:36-55,93-139`；`docs/TEST_REPORT.md:20-39,67-120`]
2. V3.2 正式注册的分析模块共 10 个：`overview`、`daily_trend`、`hourly`、`behavior`、`funnel`、`items`、`categories`、`user_segmentation`、`repurchase`、`sales`。这些模块主要是 Spark SQL/DataFrame 聚合，不应包装成 10 个机器学习算法。[依据：`spark/src/main/java/com/uni/platform/analysis/AnalysisRegistry.java:12-47`；`GenericAnalysisEngine.java:21-38`]
3. K-Means 和 A/F/R 的 Java 源码仍在当前代码树中，也由 Legacy `Main` 串行调用，但没有注册到 V3.2 的 `AnalysisRegistry`、Capability、Worker 结果接口和主 Dashboard；其准确状态是“仅 Legacy 版本实现”，不是 V3.2 平台模块。[依据：`spark/src/main/java/com/uni/Main.java:67-109`；`FINAL_REPORT.md:65-76`]
4. K-Means 是真实的 Spark MLlib 实现，具有 7 个行为特征、`log1p`、标准化、K=3..7 搜索、WSSSE 与轮廓系数；历史全量结果选择 K=3、轮廓系数 0.4083。但模型没有 V3 能力声明、模型文件持久化、稳定性实验或新平台回归。[依据：`UserClusterAnalyzer.java:43-59,74-194`；`report/毕业设计报告.md:390-438`]
5. Legacy A/F/R 不是标准 RFM。A 是购买范围（去重商品数+去重类目数），F 是购买事件次数，R 是相对数据内最大购买时间的最近购买天数；标准 RFM 的 M 是金额。正式淘宝数据没有金额和订单 ID，不能把 A 改名为 M。[依据：`UserValueAnalyzer.java:11-23,40-74`；`UserProfileAnalyzer.java:47-80`]
6. FP-Growth 尚无 Java 实现。正式淘宝数据没有订单 ID，不能把浏览、收藏或同一用户的购买事件直接当作真实购物篮；10 行订单样本虽然有 `order_id,item_id,amount`，但每个订单仅出现一行，不能证明存在多商品订单。因此当前结论是“缺少必要数据”，不应列入承诺交付范围。
7. 在当前真实数据条件下，推荐最终主范围为：**K-Means 用户聚类 + 明确命名的 A/F/R 行为价值模型**。其中只有 K-Means 是机器学习算法；A/F/R 是业务评分模型。若导师明确要求“标准 RFM”或“3 个算法”，必须先取得有真实金额且同一订单包含多行商品明细的订单数据，再将标准 RFM 和 FP-Growth列为条件模块。

## 1. 项目当前状态

### 1.1 Git 与工程结构

| 项目 | 审计结果 |
|---|---|
| 当前分支 | `v3.2-ui` |
| HEAD | `6d09f68 docs: complete v3.2 UI acceptance` |
| 工作区 | 审计开始时 `git status --porcelain` 无输出，工作区干净 |
| Legacy 快照 | `legacy-completed` / `master` 指向 `4ceb5d0` |
| 主技术栈 | Java 17、Spark 3.5.0（Core/SQL/Hive/MLlib）、MySQL Connector/J、JDK `HttpServer`、ECharts [依据：`spark/pom.xml:11-68`] |
| 主入口 | V3.2 `/platform.html`；Legacy `/index.html` 保留为历史入口 [依据：`DashboardServer.java:49-59`] |
| 主要目录 | `spark/` 计算与服务；`sql/` MySQL/Hive DDL；`sample-data/` 两个小样本；`docs/` 设计与验收；`scripts/` 启动/测试脚本 |

### 1.2 当前分层和调用关系

```text
Dashboard(platform.html/platform.js)
        │ fetch /api/*
        ▼
DashboardServer(JDK HttpServer)
        ├── PlatformApiHandler ──► PlatformService
        │                            ├── DatasetManager
        │                            │    ├── CsvInspector（最多读取 51 个非空行）
        │                            │    ├── SchemaMapper
        │                            │    └── CapabilityDetector
        │                            └── JobManager
        │                                 ├── MySQL PlatformRepository（任务/映射/版本）
        │                                 └── 独立 Maven/Java Spark Worker
        │                                      ├── AdapterRegistry
        │                                      ├── StandardDataset
        │                                      ├── Hive DWD（可选，dataset_id 分区覆盖）
        │                                      ├── GenericAnalysisEngine
        │                                      └── results/{dataset_id}/{job_id}
        └── 静态资源
```

- `DashboardServer` 在同一进程挂载 `/api/` 和静态资源，并默认绑定 `127.0.0.1`。[依据：`DashboardServer.java:34-59`；`PlatformConfig.java:21-37`]
- API 的 Dataset、Mapping、Capability、Job、Log、Result、latest Analytics 路由由 `PlatformApiHandler.route` 提供。[依据：`PlatformApiHandler.java:45-99`]
- `PlatformService.submit` 先读取 Capability，再用 `AnalysisRegistry.validateRequested` 拒绝不支持模块，最后交给 `JobManager`。[依据：`PlatformService.java:51-57`]
- `JobManager.submit/runProcess` 先持久化 `PENDING`，写 `job-spec.json`，再启动 `exec:java@worker`；记录 PID、退出码、日志和 SUCCESS/FAILED。[依据：`JobManager.java:40-58,70-127`]
- `AnalysisWorker.main` 适配源数据、缓存并计数、可选写 Hive、执行注册模块、写 manifest；完成后主服务将结果版本写入 Repository。[依据：`AnalysisWorker.java:23-63`；`JobManager.java:100-119`]
- Hive 使用 `INSERT OVERWRITE ... PARTITION(dataset_id)`，重跑同一数据集不会追加重复分区。[依据：`StandardModelWriter.java:6-40`]
- MySQL 的四张表是 `dataset`、`dataset_field_mapping`、`analysis_job`、`result_version`。[依据：`sql/v3_mysql.sql:1-55`]
- 前端真实调用 `/api/datasets`、mapping、capabilities、jobs、logs、results、analytics、status；分析图表读取 V3 REST，不读取 Legacy 静态 JSON。[依据：`spark/src/main/resources/web/platform.js:159-207,355-405,532-605`]

### 1.3 数据集注册、字段映射与 Capability

1. `DatasetManager.register` 只把绝对规范路径、文件大小、有限检查结果和状态写入 Repository，不复制源文件。[依据：`DatasetManager.java:40-56`]
2. `CsvInspector.inspect` 最多读取 51 个非空行，最多保存每列 5 个样例；仅当文件不超过 10 MiB 时才完整计数，否则 `rowCount=-1`。[依据：`CsvInspector.java:15-52`]
3. `SchemaMapper` 使用确定性“别名+推断类型+样例”规则，不使用 AI；淘宝无表头五列按位置映射，通用 CSV 使用别名字典。[依据：`SchemaMapper.java:13-18,21-48,55-67`]
4. 映射必须人工确认；确认时拒绝未知标准字段和重复标准字段，并把 Dataset 状态改为 `READY`。[依据：`DatasetManager.java:64-83`]
5. Capability 只使用已确认的标准字段。每项能力有一个或多个字段集合，满足任一集合即启用，否则返回最接近规则的缺失字段。[依据：`CapabilityDetector.java:16-34,41-54`]
6. Capability 不是分析结果；`AnalysisRegistry` 再把 Capability 映射到可执行 Module。[依据：`AnalysisRegistry.java:13-32`]

### 1.4 已实现与未实现的边界

- V3.2 是本地主机上的单机 Spark 平台，不是 Spark 集群调度系统；Worker 默认 `local[*]`。[依据：`AnalysisWorker.java:33-39`]
- Hive 是可选物化层；分析直接消费适配后的 `StandardDataset`，不是从 Hive 回读。[依据：`AnalysisWorker.java:41-51`]
- MySQL 保存平台元数据和结果索引，不保存 1 亿行明细或所有分析结果内容。
- `/api/status` 对 Hive 的判断只是 warehouse 数据库目录是否存在，对 Spark 的判断只是是否有 PENDING/RUNNING 任务；它不是对 Hive Metastore 或 Spark 进程的深度健康检查。[依据：`PlatformApiHandler.java:101-121`]
- 当前无用户认证、权限、TLS、任务取消、集群队列、分布式事务、模型注册/模型服务。[依据：`docs/THESIS_MATERIAL.md:72-78`；源码未见相应模块]
- 当前 funnel 是阶段事件数和用户数汇总，不是 session 级顺序漏斗。[依据：`GenericAnalysisEngine.java:96-105`；`docs/ANALYSIS_MODULES.md:32-34`]

## 2. 已实现功能清单

状态定义：

- **A 已实现且有验收证据**：源码存在，仓库文档保留测试/正式运行/截图等验收记录；本次没有重新运行。
- **B 已实现但未充分验证**：源码存在，但现有证据不足或只覆盖小样本/表面状态。
- **C 仅 Legacy 版本实现**：Legacy `Main`/旧 Dashboard 可用，但未进入 V3 Registry、Worker、Capability、REST 主流程。
- **D 尚未实现**：源码和可追溯验收均不存在。

### 2.1 V3.2 平台能力

| 功能 | 状态 | 审计说明与证据 |
|---|---|---|
| 文件路径注册与有界探测 | A | 不复制大文件；正式文件预扫描行数为 -1。[`DatasetManager.java:40-56`；`CsvInspector.java:23-52`；`docs/TEST_REPORT.md:67-75`] |
| 小型 CSV 上传 | B | 有大小限制、扩展名和路径检查，但现有 4 个核心测试未覆盖上传接口。[`PlatformApiHandler.java:124-138`；`PlatformConfig.java:37`] |
| 字段推荐与人工确认 | A | 别名/类型/样例推荐和重复映射检查；有单元与 UI 验收。[`SchemaMapper.java:21-67`；`PlatformCoreTest.java:54-64`] |
| Capability 动态检测 | A | 11 项能力，订单正确禁用 funnel。[`CapabilityDetector.java:41-54`；`PlatformCoreTest.java:66-74`] |
| 淘宝五列专用 Adapter | A | 显式 schema、行为值过滤、标准字段转换；样本和正式数据均有验收记录。[`TaobaoBehaviorAdapter.java:18-48`] |
| 通用 CSV/订单 Adapter | A | 已确认映射生成行为或订单标准模型；订单 10 行样本通过。[`GenericCSVAdapter.java:22-69`；`PlatformCoreTest.java:76-115`] |
| Hive 行为/订单 DWD | A | 两表均按 `dataset_id` 分区；自动化测试和正式行数验收有记录。[`StandardModelWriter.java:16-40`；`PlatformCoreTest.java:118-130`] |
| MySQL 元数据持久化 | A | 四表、跨重启、FAILED/SUCCESS、latest SUCCESS 有既有验收记录。[`sql/v3_mysql.sql:1-55`；`docs/TEST_REPORT.md:56-65`] |
| 独立 Spark Worker 和任务状态 | A | 子进程、PID、退出码、日志、启动恢复、失败重试均实现并有记录。[`JobManager.java:25-38,70-139`] |
| 版本化结果与 latest SUCCESS | A | `dataset_id/job_id` 目录，结果行带双 ID，失败任务不会成为 latest。[`VersionedResultWriter.java:19-49`；`PlatformService.java:59-63`] |
| REST API | A | health/status/datasets/mapping/capabilities/jobs/logs/results/analytics 有源码与既有调用验收。[`PlatformApiHandler.java:45-99`] |
| V3.2 五项导航 Dashboard | A | 双数据集差异化页面、12 场景、0 console error 的历史证据。[`docs/UI_ACCEPTANCE_V3_2.md:11-50`] |
| 深度运行状态探测 | B | status API 主要基于 Repository 模式、目录和任务状态推断，并未实际查询 Hive/Spark 健康。[`PlatformApiHandler.java:101-121`] |

### 2.2 V3.2 注册分析模块

| Module | 计算内容 | 行为数据 | 订单数据 | 状态 |
|---|---|---:|---:|---|
| `overview` | 记录/订单、用户、商品、购买事件或收入概览 | 是 | 是 | A |
| `daily_trend` | 每日事件与购买数，或每日订单/数量/收入 | 是 | 是 | A |
| `hourly` | 小时事件分布，或小时订单/收入 | 是 | 是 | A |
| `behavior` | `event_type` 事件数 | 是 | 否 | A |
| `funnel` | 各行为阶段事件数与去重用户数 | 是 | 否 | A；但不是严格漏斗 |
| `items` | 行为/购买 Top 20，或数量/收入 Top 20 | 是 | 是 | A |
| `categories` | 类目行为/购买 Top 20，或数量/收入 Top 20 | 是 | 是 | A |
| `user_segmentation` | 购买次数/行为次数阈值规则分群 | 是 | 是 | A；规则聚合，不是 ML |
| `repurchase` | 买家、复购买家、复购率 | 是 | 是 | A |
| `sales` | 去重订单、买家、数量、收入 | 否 | 是 | A（只在 10 行订单样本充分验收） |

实现位置：`GenericAnalysisEngine.run/overview/behavior/dailyTrend/hourly/funnel/items/categories/segmentation/repurchase/sales`，见 `GenericAnalysisEngine.java:23-176`。淘宝样本 9 个适用模块、订单样本 8 个适用模块以及正式淘宝 9 个模块的既有验收记录见 `docs/TEST_REPORT.md:41-54,77-85`。

### 2.3 Legacy 功能与尚未实现功能

| 功能 | 状态 | 说明 |
|---|---|---|
| 用户活动、购买频次、详细用户画像 | C | Legacy 分析器与历史输出存在；V3 只保留简化规则分群。 |
| A/F/R 用户价值评分 | C | 源码和历史全量结果存在，未进入 V3 Registry/REST。 |
| K-Means 用户聚类 | C | Spark MLlib 源码和历史全量结果存在，未进入 V3 平台链路。 |
| 规则群体差异明细 | C | `SegmentBehaviorAnalyzer` 存在，未迁移。 |
| `(user_id,item_id)` 详细购买前行为路径 | C | `BehaviorPathAnalyzer` 存在；V3 仅阶段汇总。 |
| 时间有效窗口、异常数据与热力分析 | C | `TimeTrendAnalyzer` 存在；V3 仅日/小时聚合。 |
| 缓存前后性能基准 | C | Legacy `Main.runCacheBenchmark` 存在，未迁移。 |
| 标准 RFM（含 Monetary） | D | Java 源码无 RFM；正式行为数据无金额。 |
| FP-Growth 频繁项集/关联规则 | D | Java 源码无 `FPGrowth`；没有可验证的多商品订单数据。 |
| 模型持久化、在线预测、模型版本 | D | K-Means 只导出聚类结果和汇总，没有保存 `KMeansModel`。 |
| session 级漏斗 | D | 数据无 `session_id`。 |
| 认证、TLS、细粒度权限、任务取消 | D | 当前仅本机 HTTP 平台。 |

上述 Legacy 边界也由最终报告明确标记为 `NOT MIGRATED`。[依据：`FINAL_REPORT.md:65-76`]

## 3. Legacy 算法审计

### 3.1 K-Means 用户聚类

#### 3.1.1 源码与调用

- 类：`com.uni.analysis.UserClusterAnalyzer`
- 路径：`spark/src/main/java/com/uni/analysis/UserClusterAnalyzer.java`
- 入口：`UserClusterAnalyzer.run()`（71-195 行）
- Legacy 调用：`com.uni.Main.main()` 在画像、A/F/R、规则分群之后调用该模块（`Main.java:75-98`）。
- V3 状态：没有出现在 `AnalysisRegistry.modules()`（`AnalysisRegistry.java:35-47`），因此不能通过 V3 Job/API 执行。

#### 3.1.2 是否使用 Spark MLlib

是。源码直接导入并使用：

- `org.apache.spark.ml.clustering.KMeans/KMeansModel`
- `org.apache.spark.ml.evaluation.ClusteringEvaluator`
- `org.apache.spark.ml.feature.VectorAssembler/StandardScaler`

依据：`UserClusterAnalyzer.java:5-9,94-106,124-158`；Maven 依赖见 `spark/pom.xml:42-47`。

#### 3.1.3 输入特征

从 `UserProfileAnalyzer.userProfile` 选取 7 个非负计数特征：

1. `action_count`：用户全部行为次数；
2. `pv_count`：浏览次数；
3. `cart_count`：加购次数；
4. `fav_count`：收藏次数；
5. `purchase_count`：购买事件次数；
6. `purchase_item_count`：购买过的去重商品数；
7. `purchase_category_count`：购买涉及的去重类目数。

依据：`UserClusterAnalyzer.java:43-52,74-83`。其中四类行为数由 `UserProfileAnalyzer` 对 `behavior` 聚合，购买商品/类目数来自 buy 子集，见 `UserProfileAnalyzer.java:45-65`。

#### 3.1.4 K 的确定、标准化和评价指标

- 每个特征先做 `log1p`，降低长尾计数影响。[`UserClusterAnalyzer.java:85-92`]
- `VectorAssembler` 形成向量，再用 `StandardScaler(withStd=true, withMean=false)`；即按标准差缩放但不中心化。[`UserClusterAnalyzer.java:94-106`]
- 遍历 K=3..7，固定 `seed=42`、`maxIter=30`。[`UserClusterAnalyzer.java:54-59,124-131`]
- 每个 K 记录 `trainingCost()`（代码称 WSSSE）和 `ClusteringEvaluator` 轮廓系数；轮廓系数在固定种子抽取的 20% 用户上计算。[`UserClusterAnalyzer.java:113-147`]
- 以轮廓系数最大值选择最终 K，再在全部特征集训练最终模型。[`UserClusterAnalyzer.java:144-158`]
- `ClusteringEvaluator` 未显式设置距离度量，使用 Spark 默认设置；论文中应写明实际默认度量，并在实现阶段固定配置，避免版本差异。

历史全量报告记录 K=3 的轮廓系数最高（0.4083），K=3..7 的 WSSSE 单调下降。[`report/毕业设计报告.md:400-411`] 本次没有重跑，因此这是“历史全量证据”，不是 2026-10-11 的现场复验。

#### 3.1.5 输出

- `cluster_evaluation`：`k,wssse,silhouette`；
- `cluster_summary`：每簇用户数及 7 个原始特征均值；
- `cluster_segment_cross`：聚类与规则分群交叉表；
- `user_cluster.csv`：`user_id,cluster` 明细。

依据：`UserClusterAnalyzer.java:160-194`。历史报告还记录了三簇画像和交叉解释。[`report/毕业设计报告.md:413-438`]

#### 3.1.6 迁移至 V3.2 的可行性

结论：**需要补充开发，但算法核心可高比例复用**。

- 淘宝标准行为模型具备 `user_id,item_id,category_id,event_type,event_time`，足以重新聚合全部 7 个特征。[`DATA_MODEL.md:3-15`]
- 需把 Legacy 列名 `behavior/timestamp` 改为标准模型的 `event_type/event_time`，并禁止直接读取源 CSV。
- 需新增用户特征宽表、`kmeans_clustering` Capability/Module、Worker 执行、版本化结果、REST 和 V3 Dashboard 页面。
- 应补充：特征空值/常量检查、至少多个随机种子或抽样稳定性、簇大小约束、业务解释、模型/参数元数据；如声称“模型可复用”，还需持久化模型。

### 3.2 A/F/R 用户价值模型

#### 3.2.1 A、F、R 的真实含义与公式

实现类：`com.uni.analysis.UserValueAnalyzer`，路径 `spark/src/main/java/com/uni/analysis/UserValueAnalyzer.java`，入口 `run()`。

| 维度 | 代码含义 | 公式/阈值 |
|---|---|---|
| A | Acquisition breadth / 购买范围；代码没有展开英文，但业务计算是范围，不是金额 | `purchase_range = purchase_item_count + purchase_category_count`；≥20→5，≥10→4，≥5→3，>0→2，否则0 |
| F | Frequency / 购买频率 | `purchase_count`；≥20→5，≥10→4，≥5→3，>0→2，否则0 |
| R | Recency / 最近购买 | `days_since_last_purchase`；空→0，≤3→5，≤7→4，≤30→3，≤90→2，否则1 |
| 总分 | 三项等权求和 | `value_score=A_score+F_score+R_score` |

依据：`UserValueAnalyzer.java:17-22,40-74`。

`days_since_last_purchase` 不是相对系统当前日期，而是“数据集中最大购买时间”减“用户最后购买时间”。[`UserProfileAnalyzer.java:67-80`] 这一选择适合封闭历史窗口，但不同数据集或滚动窗口比较时必须保存 reference date。

#### 3.2.2 输入字段与输出结构

原始必要字段：`user_id,item_id,category_id,behavior,timestamp`。只有 `behavior='buy'` 的记录用于购买次数、去重商品/类目、最后购买时间；全体用户用于保留未购买用户。[`Main.java:137-166`；`UserProfileAnalyzer.java:45-101`]

`userValueProfile` 字段为：

- `user_id`
- `purchase_count`
- `purchase_item_count`
- `purchase_category_count`
- `days_since_last_purchase`
- `purchase_range`
- `A_score`,`F_score`,`R_score`
- `value_score`
- `user_value_level`

导出 5 张分布表：`a_score_counts`、`f_score_counts`、`r_score_counts`、`value_score_counts`、`value_level_counts`。[`UserValueAnalyzer.java:80-102`]

#### 3.2.3 用户分层规则

- `value_score=0`：未购买用户；
- `value_score<=6`：低价值用户；
- `value_score<=11`：普通价值用户；
- 其余（12-15）：高价值用户。

依据：`UserValueAnalyzer.java:68-74`。历史正式数据窗口仅 9 天，R 分数集中在 4/5，报告也承认阈值缺少敏感性分析。[`report/毕业设计报告.md:312-319,618-624`]

#### 3.2.4 与标准 RFM 的区别

| 项目 | Legacy A/F/R | 标准 RFM |
|---|---|---|
| 第一维 | A=购买范围 | R=最近一次交易 |
| 第二维 | F=购买事件次数 | F=交易频次（通常去重订单） |
| 第三维 | R=最近购买 | M=累计/平均交易金额 |
| 金额 | 不使用 | 必须使用 Monetary |
| 交易粒度 | buy 行为事件 | 通常订单或订单明细 |
| 阈值 | 固定业务阈值 | 常用分位数、业务阈值或聚类，并需验证稳定性 |

因此 A/F/R 不能直接视为 RFM；`purchase_item_count + purchase_category_count` 还把两个相关但不同量纲的去重计数直接相加，需要在论文中解释或改进。

#### 3.2.5 扩展为 RFM 的条件

- 算法结构（用户聚合→评分→分层→分布输出）可以复用。
- 淘宝行为数据可提供 R 和近似 F，但无 `order_id` 会把多次 buy 事件当频次，无金额则无法得到 M。
- 订单标准模型已定义 `order_id,user_id,order_time,amount`，结构上可计算标准 RFM。[`DATA_MODEL.md:16-30`]
- 目前只有 10 行订单样本，不能支撑有意义的阈值、稳定性或业务结论；必须补充真实、合法、规模足够的订单数据并确认金额口径（实付/应付/退款后）。

结论：**有代码骨架，但标准 RFM 在当前正式数据上缺少必要金额数据；不能直接迁移并改名。**

## 4. 数据集字段与算法适配矩阵

### 4.1 淘宝用户行为正式数据与样本

#### 已确认结构

| 数据集 | 文件 | 已确认范围 |
|---|---|---|
| 正式淘宝用户行为 | 外部 `data/UserBehavior.csv`，3,672,347,465 字节 | 本次仅读取前 12 行；仓库历史验收记录 100,150,807 行，未在本次重新扫描 |
| 淘宝行为样本 | `sample-data/sample_taobao_behavior.csv`，12 行 | 完整小样本；自动化测试源码断言适配后 12 行 [`PlatformCoreTest.java:80-102`] |

两者均无表头，五列位置契约如下：

| 位置 | 标准字段 | 源含义 | 原始/标准类型 | 业务含义 |
|---:|---|---|---|---|
| 1 | `user_id` | user_id | 原始整数；标准 STRING | 用户标识 |
| 2 | `item_id` | item_id | 原始整数；标准 STRING | 商品标识 |
| 3 | `category_id` | category_id | 原始整数；标准 STRING | 类目标识 |
| 4 | `event_type` | behavior | STRING | `pv/cart/fav/buy` |
| 5 | `event_time` | timestamp | Unix 秒，标准 BIGINT | 行为时间 |

类型与过滤依据：`TaobaoBehaviorAdapter.java:24-47`；标准模型见 `DATA_MODEL.md:3-15`；源字段说明见 `docs/数据说明.md:15-30`。

#### 关键数据条件

| 问题 | 结论 |
|---|---|
| 用户标识 | 有：`user_id` |
| 商品标识 | 有：`item_id` |
| 行为类型 | 有：`pv/cart/fav/buy` |
| 时间字段 | 有：Unix 秒 `event_time` |
| 订单 ID | **无** |
| 真实交易金额 | **无** |
| 订单商品明细 | **无**；`buy` 只是行为事件，不能证明订单边界或同单商品集合 |
| 用户行为特征 | **具备**；可构建计数、去重商品/类目、最近购买、行为比例等特征 |

### 4.2 订单样本

文件：`sample-data/sample_order.csv`，有表头，10 条记录。每一条样本 `order_id` 都不同。

| 字段 | 标准类型 | 业务含义 |
|---|---|---|
| `order_id` | STRING | 订单标识 |
| `user_id` | STRING | 用户标识 |
| `item_id` | STRING，可空 | 商品标识 |
| `category_id` | STRING，可空 | 类目标识 |
| `order_time` | 源为日期时间字符串，适配为 Unix 秒 BIGINT | 下单/交易时间 |
| `quantity` | DOUBLE | 商品数量 |
| `unit_price` | DOUBLE | 单价 |
| `amount` | DOUBLE | 行金额/订单金额字段 |

适配依据：`GenericCSVAdapter.java:26-64`；模型定义：`DATA_MODEL.md:16-30`。

| 问题 | 结论 |
|---|---|
| 订单 ID | 有 |
| 金额字段 | 有 `amount`，但仅样本值；不能据此声称是经核验的真实交易金额 |
| 订单商品明细 | 结构上每行有订单+商品+数量，**但样本中每个订单只有一行**，未证明存在多商品订单 |
| 用户行为特征 | 不含浏览/加购/收藏；可构建订单频次、金额、最近交易等交易特征 |
| 支撑标准 RFM | 字段结构满足，规模和真实性不足 |
| 支撑 FP-Growth | 当前样本不满足有效购物篮；所有篮子大小为 1 时无法得到有意义共购规则 |

### 4.3 数据集—算法适配矩阵

| 算法/模型 | 淘宝正式行为数据 | 淘宝样本 | 订单样本 | 当前判定 |
|---|---|---|---|---|
| K-Means 7维行为聚类 | 字段满足且有历史全量结果 | 可做流程测试，规模太小 | 需另设计订单特征，不可直接复用7维行为特征 | 需要迁移开发 |
| Legacy A/F/R | 字段满足且有历史全量结果 | 可做流程测试 | 可改写，但 A 的定义与订单价值不一定合理 | 仅 Legacy 已实现 |
| 标准 RFM | R/F 可近似，M 缺失 | 同左 | 字段满足但仅10行、真实性不足 | 当前正式研究缺少必要数据 |
| FP-Growth 共购 | 无 order_id，不能形成真实购物篮 | 同左 | 有 order_id/item_id，但每单仅1行 | 缺少必要数据 |

## 5. 三个候选算法可行性评估

### 5.1 K-Means 用户聚类

| 维度 | 评估 |
|---|---|
| 业务目的 | 按行为活跃、购买倾向和购买广度发现数据驱动用户群，辅助精细运营，并与规则分群交叉解释 |
| 所需字段 | `user_id,item_id,category_id,event_type,event_time`，再聚合 7 维用户特征 |
| 当前数据 | 正式淘宝数据满足；已有历史全量聚类结果 |
| 代码复用 | 高：MLlib 流程和输出逻辑可复用；需改标准列名、平台接口和版本化输出 |
| Spark 组件 | Spark SQL/DataFrame 聚合；`VectorAssembler`、`StandardScaler`、`KMeans`、`ClusteringEvaluator` |
| 新增/修改模块 | 用户特征构建器、Capability/Registry、V3 Engine/Worker 分支、结果 DTO/manifest、REST/Dashboard、测试 |
| 主要难点 | 特征共线与长尾、K 稳定性、全量多轮训练成本、簇业务解释、模型/参数可追溯 |
| 验证方法 | 小样本流程；正式数据 K=3..7；WSSSE/轮廓系数；多 seed/抽样稳定性；簇规模和特征均值；与规则分群交叉；重跑一致性 |
| 结论 | **需要补充开发**；优先级最高，核心算法可直接复用但不能称 V3 已实现 |

### 5.2 标准 RFM 用户价值分析

| 维度 | 评估 |
|---|---|
| 业务目的 | 按最近交易、交易频率、消费金额识别高价值、待唤醒、潜力等客户 |
| 所需字段 | 至少 `user_id,order_id,order_time,amount`；需明确取消、退款、重复订单和金额口径 |
| 当前数据 | 正式淘宝数据无 order_id/amount；订单样本字段满足但仅10行，不能支撑研究结论 |
| 代码复用 | 中：Legacy 的用户聚合、评分、分层和输出框架可复用；A 必须替换为 M，F 应从 buy事件改为去重订单 |
| Spark 组件 | Spark SQL/DataFrame 的 `groupBy`、`max`、`countDistinct`、`sum`；可用 `approxQuantile` 做分位数评分 |
| 新增/修改模块 | 订单数据质量规则、RFM 特征表、评分策略版本、Capability/Registry、结果/REST/UI、阈值敏感性测试 |
| 主要难点 | 缺真实金额数据；金额和退款口径；观察窗与 reference date；极端值；小样本分位数失真 |
| 验证方法 | 手工可核小表；R/F/M 汇总对账；分位数边界；分层人数/金额贡献；阈值敏感性；跨时间窗稳定性 |
| 结论 | **当前正式数据缺少必要数据**。仅在获得真实订单数据后进入承诺范围；Legacy A/F/R 不得冒充 RFM |

说明：RFM 是经典用户价值分析模型，但本实现若只是 Spark SQL 聚合与规则评分，应称“RFM 模型应用”，不要称为自研机器学习算法。

### 5.3 FP-Growth 商品关联规则

| 维度 | 评估 |
|---|---|
| 业务目的 | 从真实订单购物篮中发现频繁商品组合和 `A→B` 关联规则，用于搭配推荐、捆绑营销和陈列 |
| 所需字段 | `order_id,item_id` 是最低要求；还需同一订单多商品、去重/数量规则、足够订单规模；时间和用户用于扩展验证 |
| 当前数据 | 淘宝行为无 order_id；订单样本10单10商品且每单一行，没有可用共购篮子 |
| 代码复用 | 无；仓库 Java 源码中不存在 `FPGrowth`/频繁项集/关联规则实现 |
| Spark 组件 | `org.apache.spark.ml.fpm.FPGrowth`；输入为每个订单的去重商品数组 |
| 新增/修改模块 | 订单篮子构建、数据质量、association Capability、FP-Growth Worker 模块、频繁项集/规则结果、REST/UI、参数配置 |
| 主要难点 | 获得真实篮子；热门商品偏置；support/confidence/lift解释；规则爆炸；参数与结果规模控制 |
| 验证方法 | 先验证多商品订单比例和篮子大小分布；手算小数据；support/confidence/lift对账；参数敏感性；时间留出后的规则覆盖 |
| 结论 | **缺少必要数据**；当前不建议开发或写入确定交付范围 |

禁止性边界：不能按“同一用户在全观察期浏览/购买过的商品”构造篮子后仍称为订单共购。这最多是用户级共现或行为关联，业务语义、支持度分母和时间跨度均不同，必须另立题目和验证口径。

## 6. 毕设工作量与技术特色

### 6.1 本科毕业设计工作量判断

| 维度 | 评价 |
|---|---|
| 系统模块数量及复杂度 | 较充足。平台已有接入、映射、能力、适配、任务、存储、API、可视化等完整分层，明显超过单一分析脚本 |
| 大数据技术深度 | 中上。Spark SQL、独立 Worker、Hive 分区幂等、1 亿行回归真实可讲；但当前仍是本地模式，不宜声称集群调度/分布式部署 |
| 算法应用工作量 | 当前 V3 偏弱：10 个模块多为聚合。迁移 K-Means可显著补足；A/F/R是评分模型而非 ML；RFM/FP受数据限制 |
| 数据流程完整性 | 强。Dataset→Mapping→Capability→Adapter→标准模型→Job→Result→REST→Dashboard 闭环完整 |
| 多数据集适配 | 有工程亮点，但“多数据集”目前实际验收是一个正式行为集+一个10行订单样本，泛化证据仍有限 |
| 测试与验证工作量 | 已有4个自动化测试、双数据集、MySQL重启、Hive幂等、正式1亿行、浏览器场景；算法迁移后需新增专项验证 |
| 答辩展示性 | 高。可展示字段映射、Capability动态禁用、FAILED→重试、latest版本、双数据集差异、K选择和聚类画像 |

综合判断：即便只新增 K-Means 平台化和 A/F/R 平台化，结合现有平台闭环与正式大数据回归，作为本科毕设工作量已经充足。为了“凑三个算法”而虚构金额或购物篮，反而会降低可信度。

### 6.2 可用于答辩展示的技术特色

1. 3.67GB、100,150,807 行正式行为数据的全链路验收和 Legacy 五项指标零差异。
2. 源字段—标准字段—Capability—Module 的动态启停机制。
3. Hive `dataset_id` 分区覆盖与结果 `dataset_id/job_id` 版本隔离。
4. Spark 子进程 PID、日志、退出码、FAILED/SUCCESS 和重启恢复。
5. K-Means 的特征工程、K 选择、轮廓系数与业务规则分群交叉解释。
6. 对数据边界的诚实说明：无 session、不把行为当订单、不把 A/F/R 冒充 RFM。

### 6.3 最可能被质疑的问题

1. “10 个分析模块中哪些是真算法？”——应答：多数是 Spark SQL 指标模块；ML 算法是 K-Means，A/F/R是业务评分模型。
2. “为什么题目说大数据，Spark 却是 local 模式？”——必须说明验证的是分布式计算框架的编程模型和亿级本地实验，不声称完成 YARN/HDFS 集群部署。
3. “订单数据只有10行，如何证明多数据集泛化？”——只能证明结构与流程适配，不能证明大规模订单分析效果。
4. “RFM 的 M 在哪里？”——正式淘宝数据没有金额；当前 Legacy 是 A/F/R，不是 RFM。
5. “FP-Growth 的购物篮如何定义？”——当前没有真实多商品订单，不能实施或声称结果。
6. “K=3是否可靠？”——当前仅一个 seed 和20%轮廓抽样；应增加多 seed/抽样稳定性和参数记录。
7. “Capability 检测字段够了是否代表数据质量够了？”——不代表。当前能力主要检查字段存在，不验证空值率、唯一性、合法值、金额质量或篮子大小。
8. “系统状态是否真正检测 Hive/Spark？”——当前是浅层状态推断，需要诚实说明。
9. “历史验收是否可复现？”——本次未启动服务；最终论文阶段应在固定环境保留命令、日志、manifest和截图，算法模块迁移后再做一次受控回归。

## 7. 推荐的最终研究范围

### 7.1 推荐组合

#### 当前可承诺的主范围（推荐）

1. **K-Means 用户聚类**：作为唯一明确的机器学习算法，迁移至 V3 标准行为模型。
2. **A/F/R 行为用户价值模型**：按真实名称平台化，作为业务评分模型，并增加阈值/分位数敏感性对比；不得写成标准 RFM。

这两个应用共享同一用户特征宽表，能够复用 Legacy 全量证据，也不需要虚构订单金额或购物篮。论文表述建议为“用户聚类算法与用户价值评分模型”，不要笼统写成“两个机器学习算法”。

#### 条件范围

- 若在开题后获得真实、合规、规模足够的订单明细（含 `order_id,user_id,item_id,order_time,amount`，且同一订单可有多个商品行），第三项优先顺序为：
  1. 标准 RFM；
  2. 再视多商品订单比例决定是否加入 FP-Growth。
- 若没有新增订单数据：**不承诺标准 RFM，不做 FP-Growth**。

### 7.2 建议系统功能模块结构

```text
数据接入层
  Dataset Registry / CSV Inspector / Schema Mapping / Data Quality
标准模型层
  Behavior Event / Order Line / dataset_id Hive partitions
特征层（建议新增）
  User Behavior Feature Mart
  Optional Order/RFM Feature Mart
分析层
  现有10个指标模块
  K-Means Clustering（主新增）
  A/F/R Behavior Value（主新增）
  Optional Standard RFM（有真实订单后）
  Optional FP-Growth（有多商品订单后）
任务与结果层
  Worker / MySQL Job / Versioned Result / latest SUCCESS
服务与展示层
  REST / Dashboard / 算法参数、评价指标、结果解释
```

### 7.3 技术路线

1. 用现有 Dataset/Mapping/Adapter 把源数据变为标准行为或订单模型。
2. 增加数据质量门槛，而不只检查字段：空值率、合法行为、时间范围、用户数、购买数；订单数据还检查金额与多商品订单比例。
3. 构建一次可复用的用户特征宽表，避免 K-Means 与价值模型各自重复全表聚合。
4. K-Means：`log1p`→向量化→标准化→K 搜索→最终训练→簇画像/交叉解释。
5. A/F/R：明确 reference date 和阈值版本；保留 Legacy 固定阈值作为基线，再做分位数或敏感性实验。
6. 每个算法结果写入 `dataset_id/job_id` 版本目录，参数、特征、评价指标进入 manifest/结果元数据。
7. REST 与 Dashboard 展示算法参数、K评估、簇画像、价值分层、版本和数据口径。
8. 在小样本做可手算测试，再在正式淘宝数据做一次受控全量回归；不在开题阶段反复扫描全量数据。

### 7.4 算法设计与验证方案

#### K-Means

- 保留 7 维特征作为 Legacy 基线；增加特征相关性/分布说明。
- 比较 K=3..7，至少 3 个 seed；记录 WSSSE、轮廓系数、簇占比和结果一致性。
- 输出用户簇、簇中心或原始特征均值、规则分群交叉表。
- 验收：小样本可运行；正式行数/用户数对账；结果携带 dataset/job；重复运行参数相同可追溯。

#### A/F/R

- 固定阈值版本完整复现 Legacy；另做分位数或阈值敏感性，不静默改口径。
- 输出 A/F/R 分数、总分、等级和人数/行为贡献；保存 reference date。
- 验收：手工用户样例、边界阈值、未购买用户、总人数守恒、历史分布对账。

#### 条件 RFM/FP-Growth

- 先做数据准入审计，再决定立项；不把“字段存在”当作“数据可用”。
- RFM 必须对账订单数、金额和 reference date。
- FP-Growth 必须先报告订单数、多商品订单率、篮子大小分布，再设置 support/confidence；结果至少包含 lift。

### 7.5 后续开发优先级

| 优先级 | 工作 | 理由 |
|---:|---|---|
| P0 | 与导师确认 A/F/R 与 RFM 的术语、是否必须3项算法、是否能提供真实订单明细 | 决定课题边界，避免后期返工 |
| P1 | 设计 V3 用户特征宽表与算法结果/参数契约 | 两个推荐模块的共同基础 |
| P2 | 迁移 K-Means 到 Capability/Registry/Worker/REST/Dashboard，并做稳定性实验 | 复用最高、算法性最强、数据已满足 |
| P3 | 迁移 A/F/R并增加阈值敏感性，明确“行为价值模型” | 数据满足、Legacy证据强 |
| P4 | 若获得真实订单数据，实现标准 RFM | 依赖外部数据条件 |
| P5 | 仅在多商品订单充足时实现 FP-Growth | 当前缺必要数据，不应提前开发 |

## 8. 开题报告与答辩 PPT 建议

### 8.1 适合放进开题报告

- 研究背景：传统固定五列程序的字段耦合、结果覆盖、任务不可追踪问题。
- 研究目标：多数据集适配、标准模型、任务可观测、结果可追溯、用户分析模型平台化。
- 系统架构与数据流：Dataset→Mapping→Capability→Adapter→Hive→Worker→Result→REST→Dashboard。
- 数据说明和限制：正式淘宝五列、无金额/订单/session；订单目前仅样本。
- 拟研究方法：K-Means + A/F/R，标准 RFM/FP-Growth设为有数据前提的可选扩展。
- 技术路线、功能模块、测试方案、进度计划和风险控制。
- 预期创新/特色应定位为工程创新和算法应用，不宣称原创聚类或推荐算法。

### 8.2 适合放进答辩 PPT

- 一张总体架构图和一张端到端数据流图。
- Dataset 映射与 Capability 动态启停截图。
- FAILED/SUCCESS、日志、结果版本切换证据。
- 1 亿行规模与五项指标零差异表。
- K=3..7 的 WSSSE/轮廓系数折线、最终簇画像和规则分群交叉图。
- A/F/R 公式、分层结果和“不是标准 RFM”的一页边界说明。
- 数据适配矩阵：为什么当前不做 FP-Growth。
- 局限与展望：local Spark、无 session、订单样本小、认证/TLS未实现。

### 8.3 不建议在开题或 PPT 中使用的表述

- “V3.2 已经实现 K-Means/RFM/FP-Growth”。
- “A/F/R 就是 RFM”。
- “淘宝 buy 行为等于真实订单共同购买”。
- “现有10个分析模块都是算法”。
- “系统已完成分布式 Spark 集群部署”。
- “订单样本证明了真实销售规律或商品关联规则”。

## 9. 需要用户或导师确认的问题

1. 学院/导师是否接受最终范围为“K-Means 算法 + A/F/R 行为价值评分模型”，并明确只有前者是机器学习算法？
2. 课题是否硬性要求 3 个算法应用，还是允许以完整平台工程 + 1 个 ML 算法 + 1 个业务模型作为主要工作量？
3. 题目是否保留《基于Spark的电商大数据分析与可视化系统的设计与实现》，或突出“多数据集适配/用户分析”？
4. 是否能够提供合法、真实、规模足够的订单商品明细，至少包含 `order_id,user_id,item_id,order_time,amount`？
5. 金额应采用下单金额、实付金额还是退款后净额？取消/退款订单如何处理？
6. 若提供订单数据，同一订单是否有多行商品，且多商品订单比例是否足以支持 FP-Growth？
7. K-Means 是否需要保存模型以支持新用户预测，还是只要求离线聚类分析？
8. 最终验收是否要求 Spark 集群环境，还是 Java + Spark local 的亿级实验可接受？
9. 论文阶段是否允许重新运行一次正式全量算法回归并保存 manifest、日志和截图？本次审计未运行正式数据。

## 10. 最终审计判定

- **项目现状**：V3.2 平台主体完成且有较完整的历史验收证据；当前 Git 工作区在审计前干净。
- **算法现状**：K-Means 和 A/F/R 仅存在于 Legacy 流程；标准 RFM 与 FP-Growth 尚未实现。
- **数据现状**：正式淘宝数据适合行为特征、A/F/R和K-Means；不适合标准 RFM 或真实订单 FP-Growth。订单样本只能证明 schema/流程，不能证明算法业务效果。
- **推荐范围**：优先完成 K-Means 平台化和 A/F/R 行为价值模型平台化；标准 RFM 与 FP-Growth必须以新增真实订单明细为前置条件。
- **核心风险**：把 Spark SQL 聚合包装成算法、把 A/F/R写成 RFM、把用户行为共现写成共同购买、用10行订单样本支撑业务结论、把 local Spark写成集群部署。

