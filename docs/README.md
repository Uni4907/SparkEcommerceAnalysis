# Spark 电商分析平台文档

## V3.2 状态与设计

- `CURRENT_PROJECT_STATUS.md`：当前阶段、代码与正式验收状态。
- `ARCHITECTURE.md`：分层架构、数据流、隔离与恢复。
- `DATA_MODEL.md`：行为、订单标准模型及运行 DTO。
- `DATABASE_DESIGN.md`：MySQL、Hive 和文件存储设计。
- `DATASET_ADAPTER_GUIDE.md`：内置适配器和新增数据集方式。
- `ANALYSIS_MODULES.md`：Capability、模块和指标口径。
- `API_DOCUMENTATION.md`：REST 路由、请求、响应和安全边界。
- `DEPLOYMENT.md`：正式 MySQL + Hive 部署与运维。
- `USER_GUIDE.md`：统一五项导航 Dashboard 使用流程。
- `UI_AUDIT_V3_2.md`：V3.2 展示层审计与整合范围。
- `UI_ACCEPTANCE_V3_2.md`：双数据集、浏览器与最终 UI 验收证据。

## 测试、论文与答辩

- `TEST_REPORT.md`：单元、双数据集、MySQL、Hive 和 1 亿行回归证据。
- `THESIS_MATERIAL.md`：摘要、研究问题、工程贡献和章节素材。
- `DEFENSE_NOTES.md`：演示顺序、常见问题和边界说明。
- 根目录 `FINAL_REPORT.md`：最终迁移与验收结论。

## 审计与迁移依据

- `LEGACY_AUDIT_A.md`
- `LEGACY_AUDIT_B.md`
- `LEGACY_DIFF_REPORT.md`
- `V3_MIGRATION_PLAN.md`

## Legacy 文档

- `数据说明.md`
- `开发日志.md`
- `运行说明.md`
- `模型调整说明.md`

## 主要入口

```text
SparkEcommerceAnalysis/
├── scripts/start-v3.ps1
├── scripts/test-v3.ps1
├── sql/v3_mysql.sql
├── sql/v3_hive.sql
├── spark/src/main/java/com/uni/platform/
├── spark/src/main/resources/web/platform.html
├── docs/
└── FINAL_REPORT.md
```

V3.2 唯一主入口是 `/platform.html`；Legacy 页面继续保留为 `/index.html`，但不出现在顶层导航。
