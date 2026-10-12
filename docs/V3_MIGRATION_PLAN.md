# V3.1 迁移方案

状态：审计完成，可进入备份与增量迁移阶段。

审计日期：2026-09-14

## 1. 主工程

主工程使用：

当前 Git 仓库根目录

策略：**B 作为唯一代码主线；A 作为只读原始基线、正式数据源和资料源。**

不复制 3.67 GB 数据，不覆盖 A，不从零重写已经验证的 Spark 指标。

## 2. 迁移前备份

1. 保留审计前已经存在的 `Main.java` 一处注释修改。
2. 将四份审计文档和当前工作树形成 migration snapshot 提交。
3. 创建 `legacy-completed` 分支指向该快照。
4. 从该快照创建并切换到 `v3-platform` 分支。
5. Legacy 全量 `spark/output` 保持原位并继续被 `.gitignore` 排除，作为只读指标基准。

回滚方式：切回 `legacy-completed`；A 原项目始终不受迁移影响。

## 3. 保留目录和文件

- `spark/src/main/java/com/uni/BaselineMain.java`：不改指标逻辑。
- `spark/src/main/java/com/uni/analysis/*`：先通过标准模型适配复用，再逐项去耦。
- `spark/src/main/resources/web/*`：复用图表、CSS、ECharts 与布局。
- `docs`、`report`、`images`：保留历史并补充 V3 文档。
- `spark/output`：保留为 Legacy 基准，不作为 V3 版本化输出位置。

## 4. 从 A 吸收的资产

- `UserBehavior.csv`：只在 Dataset Manager 中登记绝对路径与元数据，不复制。
- 最初数据说明、开发日志和截图：合入论文演进材料，并保留来源路径。
- A `Main.java`：B 的 `BaselineMain` 已完整保存，无需重复迁移。

## 5. 重构边界

| 现有组件 | V3 目标 |
|---|---|
| `AnalysisConfig` | 外部化平台配置、数据库配置、数据/结果根目录、进程参数 |
| `AnalysisContext` | 增加 dataset/job 运行上下文；分析器只见标准模型 |
| `Main` | 保留 Legacy 入口，新增适配器 + 注册表 + 编排器入口 |
| `ResultExporter` | 输出到 `results/{dataset_id}/{job_id}`，记录 manifest，禁止覆盖成功历史 |
| `DashboardServer` | 显式 loopback 配置、REST API、静态页面与统一错误响应 |
| 前端 `fetch(data/*.json)` | 改为 `/api/datasets/{id}/analytics/...` |

不为了风格改写已验证的聚合表达式。只有为 `dataset_id`、标准字段、能力动态启用和结果版本所需的部分才重构。

## 6. 新增模块

```text
platform/
├── dataset/      Dataset Manager、元数据与文件探测
├── mapping/      Schema Mapper、别名/类型/样例推荐
├── capability/   Capability Detector
├── adapter/      GenericCSVAdapter、TaobaoBehaviorAdapter
├── model/        dwd_behavior_event、dwd_order 标准模型
├── analysis/     Analysis Registry、模块声明与能力约束
├── job/          Job Manager、子进程、状态与日志
├── result/       Result Versioning、latest SUCCESS 查询
├── api/          REST 路由与 DTO
└── persistence/  MySQL repository + DDL
```

前端新增：Overview、Datasets、Dataset Detail、Field Mapping、Jobs、Job Detail、Analytics。

## 7. 数据模型与持久化

### MySQL 元数据

- `dataset`：计划要求的 10 个字段。
- `dataset_field_mapping`：源字段、标准字段、类型、置信度、确认状态。
- `analysis_job`：`dataset_id`、状态、进程、时间、日志路径、失败信息。
- `result_version`：`dataset_id`、`job_id`、模块、版本路径、创建时间。

### 标准模型

- `dwd_behavior_event(dataset_id,user_id,item_id,category_id,event_type,event_time)`。
- `dwd_order(dataset_id,order_id,user_id,item_id,category_id,order_time,quantity,unit_price,amount)`。

所有 DWD/DWS/ADS 结果必须携带 `dataset_id`；结果版本再携带 `job_id`。

Hive 用于 ODS/DWD/DWS/ADS 大表，MySQL 用于平台元数据、任务和结果索引。开发/测试允许 repository fake，但正式部署验收必须连接真实 MySQL；不得把内存状态冒充 MySQL。

## 8. API 与任务模型

- `POST /api/datasets`：注册/上传数据集。
- `GET /api/datasets`、`GET /api/datasets/{id}`。
- `POST /api/datasets/{id}/mapping/recommend`、`PUT .../mapping`。
- `GET /api/datasets/{id}/capabilities`。
- `POST /api/jobs`：创建 `PENDING` job 后异步启动 Spark 子进程。
- `GET /api/jobs`、`GET /api/jobs/{id}`、`GET /api/jobs/{id}/logs`。
- `GET /api/datasets/{id}/analytics/{module}`：默认解析 latest SUCCESS。

状态机：`PENDING -> RUNNING -> SUCCESS|FAILED`。每次执行生成独立日志和结果目录。

## 9. 分阶段执行与验收

1. **Phase 2 备份**：snapshot、`legacy-completed`、`v3-platform`。
2. **Phase 3 主工程整理**：配置、目录、测试基础；Legacy 入口仍可编译。
3. **Phase 4-6**：Dataset、Mapping、Capability 的纯 Java 单元测试。
4. **Phase 7-9**：两个标准模型、适配器、分析注册表；复用 Legacy 分析口径。
5. **Phase 10-11**：异步 Job、日志、版本化结果和 latest SUCCESS。
6. **Phase 12-13**：REST API 与七个前端页面。
7. **Phase 14**：淘宝行为 + `sample_order.csv` 双数据集测试。
8. **Phase 15-16**：完整文档、部署验证、回归与最终报告。

每阶段先运行快速测试；全量 3.67 GB 回归只在数据模型和指标稳定后执行，避免重复消耗。

## 10. 双数据集能力预期

| 数据集 | 应启用 | 应禁用 |
|---|---|---|
| Taobao Behavior | Behavior、Funnel、Items、Categories、Segmentation、Repurchase、Time、Overview | Order、Sales、Revenue |
| Order Sample | Order、Sales、Revenue、Repurchase、Items、Categories、Time、Overview | Funnel（无行为事件时） |

能力检测只依据已确认标准字段，不依据文件名或人工硬编码的数据集类型。

## 11. 回归与指标一致性

淘宝全量回归至少比较：总记录数、用户数、PV、购买数、购买用户数。

基准值：

| 指标 | Legacy B |
|---|---:|
| 总记录数 | 100,150,807 |
| 用户数 | 987,994 |
| PV | 89,716,264 |
| 购买数 | 2,015,839 |
| 购买用户数 | 672,404 |

定义不变时必须一致；定义变化时生成 `docs/METRIC_DIFF_REPORT.md`，列出旧定义、新定义、差值和原因。

## 12. 风险与控制

- **环境漂移**：启动脚本固定 JDK 17、Hadoop 路径并启动前自检。
- **大文件重复**：正式 CSV 只登记路径；测试样本单独标记用途。
- **结果覆盖**：结果目录强制使用 dataset/job 维度，SUCCESS 版本不可原位改写。
- **进程失控**：记录 PID、超时、退出码和日志；服务重启后可识别遗留 RUNNING job。
- **指标漂移**：BaselineMain + 固定基准输出 + 差异报告。
- **数据库不可用**：API 明确失败，不以静默内存回退冒充持久化成功。
- **网络暴露**：默认显式绑定 `127.0.0.1`，远程部署再通过配置开放。
- **漏斗语义**：没有 session_id 时标记为近似链路，不宣传为严格漏斗。
- **历史工作树变更**：备份提交包含并保留审计前的注释改动，不覆盖或还原。

## 13. 删除策略

迁移初期不物理删除历史资产。确定无引用后，仅从 V3 源码/构建清单排除 `.idea`、`target` 和重复生成资源；Git 历史和 `legacy-completed` 分支保证可恢复。任何 Legacy 功能无法迁移时，在最终报告中标为 `NOT MIGRATED` 并说明原因。
