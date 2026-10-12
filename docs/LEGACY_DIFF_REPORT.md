# Legacy A / B 差异审计

审计日期：2026-09-14

## 1. 总结

结论：**B 作为代码主工程，A 作为不可变原始基线、正式数据源和历史资料源。不是以 A/B 源码直接混合为新工程。**

B 的 `BaselineMain.java` 与 A 的 `Main.java` 仅有类名一行差异，证明 B 已完整保留 A 的核心代码。A 独有的关键价值是 3.67 GB 原始数据、最初日志、数据说明和原始截图；B 独有的关键价值是 Git 历史、模块化 Spark 分析、全量结果、可视化与报告。

## 2. 按模块比较

| 模块 | 标记 | A | B | 决策 |
|---|---|---|---|---|
| 项目结构 | B_BETTER | 单个 Spark Java 文件 | 17 个 Java 文件、分析模块、前端、报告、Git 历史 | B 为主，保留 A 快照 |
| 原始数据 | A_ONLY | 唯一 `UserBehavior.csv` | `data` 为空，引用 A 路径 | A 原地 KEEP，由 Dataset Manager 注册 |
| 字段说明 | DUPLICATE | 原始数据说明 | 基本复制并新增口径说明 | MERGE，注明 A 是原始来源 |
| 原始开发日志 | A_BETTER | 最早创建过程 | 包含 A 内容并追加 Codex 演进 | 两者都保留，B 文档引用 A |
| 原始截图 | A_ONLY | `behavior-count.png` | B 有复制和 overview 截图 | 原图 KEEP，重复项不复制 |
| Spark 读取 | B_BETTER | 绝对路径、单入口 | 参数化路径、时区、上下文、缓存 | ADAPT B |
| 基础指标 | DUPLICATE | 原始实现 | 同口径模块化实现 + 全量输出 | B 运行，A/BaselineMain 回归 |
| 用户分析 | B_BETTER | 活跃、购买力、画像、A/F/R | 另有分群、群体差异、KMeans、校验 | ADAPT B |
| 商品/类别 | B_ONLY | 无 | 热度、购买与转化排行 | ADAPT |
| 行为路径 | B_ONLY | 无 | 近似漏斗/链路 | ADAPT，保留口径警告 |
| 时间分析 | B_ONLY | 无 | 日期、小时、热力、异常窗口 | ADAPT |
| 结果导出 | B_ONLY | 控制台 | CSV + JSON | REFACTOR 为版本化结果 |
| 前端 | B_ONLY | 无 | ECharts 单数据集仪表盘 | ADAPT 为 Dataset-aware Analytics |
| 静态 HTTP | B_ONLY | 无 | JDK HttpServer | REFACTOR 为 API/静态一体服务 |
| Hive | DUPLICATE | 无 | 无 | 新增；不是历史复用项 |
| MySQL/JDBC | DUPLICATE | 无 | 无 | 新增；不是历史复用项 |
| 后端业务 API | DUPLICATE | 无 | 无 | 新增 |
| SQL | DUPLICATE | 空目录 | 空目录 | 用 V3 DDL 替代 |
| 启动/部署脚本 | DUPLICATE | 无 | 无 | 新增 Windows 脚本 |
| 自动化测试 | DUPLICATE | 空测试目录 | 空测试目录 | 新增 |
| 报告/论文 | B_ONLY | `report` 为空 | 全量真实数据报告初稿 | MERGE 为论文素材 |
| 构建 | B_BETTER | JDK 17 临时副本编译通过 | JDK 17 临时副本编译通过，依赖更多 | 两者可构建，B 能力更完整 |

## 3. 冲突项

### CONFLICT：价值模型文档与代码

早期外部描述的 R_score 阈值和价值等级与实际代码不一致。B 已在“模型调整说明”中选择当前代码口径，并保存历史说明。V3 不静默改变指标；若未来调整，必须版本化指标定义并生成差异报告。

### CONFLICT：服务能力的文字预期与真实实现

执行计划要求检查 Flask/Hive/MySQL，但 A/B 实际均没有这些模块。不能把静态 `DashboardServer` 描述成业务 API，也不能把 CSV 输出描述成 MySQL 写入。

### CONFLICT：默认运行环境

项目要求 JDK 17，当前系统终端默认 JDK 8。本机已有 JDK 17；部署脚本必须固定正确 JDK，而不能依赖默认环境。

## 4. 资产处置总表

| 分类 | 资产 |
|---|---|
| KEEP | A 原始 CSV、A 原始文档/截图、B Git 历史、`BaselineMain`、Legacy 全量输出、指标口径说明 |
| ADAPT | SparkSession 初始化、读取/缓存、分析器业务逻辑、Windows Hadoop 配置、ECharts 图表和 CSS |
| REFACTOR | `Main` 编排、`AnalysisContext`、`ResultExporter`、`DashboardServer`、前端数据访问层 |
| MERGE | A/B 开发日志、数据说明、论文和答辩材料 |
| DROP | `.idea`、`target`、重复生成的静态资源、空目录占位、不能验证的“已有 Hive/MySQL”假设 |

## 5. 主工程判断

推荐 **B**，技术理由：

1. B 完整保存 A 的 349 行原始逻辑，基线不会丢失。
2. B 已经有模块边界、全量结果和可视化，真实运行风险明显低于 A。
3. B 有可追溯 Git 历史，适合建立 migration snapshot 和回滚分支。
4. A 的关键独有资产可以只读引用或文档合并，无需以 A 为代码主线。
5. “Merge 为主工程”会制造两个代码源；正确的合并方式是 B 代码主线 + A 资产引用。
