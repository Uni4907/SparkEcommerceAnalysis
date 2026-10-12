# 答辩要点

## 一句话介绍

本项目把固定淘宝 CSV 的 Spark 分析程序迁移为可登记多数据集、动态判断能力、持久化任务并保留结果版本的电商分析平台，同时保证 1 亿行核心指标与 Legacy 完全一致。

## 五分钟讲解顺序

1. 问题：旧系统字段和单一文件绑定，结果会覆盖，任务缺少状态与日志。
2. 决策：B 作为代码主线，A 只读提供正式数据，不复制 3.67GB 文件。
3. 架构：Dataset → Mapping → Capability → Adapter → Hive Standard Model → Job → Versioned Result → REST → Dashboard。
4. 实现：Java 17 + Spark；MySQL 管元数据，Hive 管大表，JDK HttpServer 管本地 API。
5. 验证：淘宝和订单双数据集；真实 MySQL；正式 100,150,807 行；五项指标差值为 0。
6. 边界：漏斗不是 session 级；服务未实现公网认证；后续复杂度增加再考虑 Spring Boot。

## 演示脚本

1. 打开“平台总览”，展示正式数据规模、最近任务和数据质量。
2. 在“数据集管理”选择正式 `dataset_id=2`，展示绝对路径、文件大小和 100,150,807 行。
3. 在内部“字段映射”标签展示五列到标准字段的确认结果。
4. 展示 Capability 的启用与禁用原因。
5. 在“分析任务”展示 job 4 FAILED、job 5/6 SUCCESS 和订单 job 7 SUCCESS。
6. 打开 job 4 日志说明失败可审计，再打开成功任务日志。
7. 在“分析中心”读取 latest overview，指出 `job_id=6`；切换订单数据展示销售分析差异。
8. 打开“系统状态”，说明 MySQL/Hive/Spark 均来自真实状态 API；Legacy `/index.html` 只作为历史入口保留。

## 高频问题

### 为什么没有使用 Flask？

Legacy B 是 Java 17 + Spark。当前 API 数量和并发需求有限，在 DashboardServer 上增量扩展能减少跨语言部署和重复模型；若以后需要复杂鉴权、事务、OpenAPI 或多节点调度，再迁移 Spring Boot。

### 为什么 MySQL 和 Hive 都需要？

MySQL 适合小而频繁更新的元数据和任务状态；Hive 适合 Spark 查询的大规模标准明细。混用一个存储会牺牲查询或事务管理能力。

### 如何证明不是内存伪持久化？

健康接口返回 `repository=mysql`；直接查询四张 MySQL 表；服务重启后仍读取到数据集、映射、FAILED/SUCCESS、版本和 latest SUCCESS。

### 如何避免重跑造成重复数据？

Hive 使用 `dataset_id` 动态分区覆盖。同一数据集重跑后仍为 100,150,807 行，而不是翻倍；其他分区不受影响。

### 如何证明指标没有漂移？

对同一个 A 中的正式文件执行 V3 全量任务，比较总记录数、用户数、PV、购买数和购买用户数，五项差值全部为 0。

### 为什么正式文件不复制？

原地登记减少磁盘占用和数据源分叉；A 保持只读，B 只保存路径、元数据和派生结果。

### Capability 和 Module 有什么区别？

Capability 表示字段足以支持某类分析，Module 表示具体可执行输出。一个时间能力可以启用 daily_trend 和 hourly 两个模块。

### 失败任务会怎样？

任务保留 FAILED、退出码、错误和日志；数据集恢复 READY，可修复后重试。成功版本不会被失败任务替代。

### 漏斗是否严格？

不是。源数据缺少 session_id，当前输出是阶段事件量和阶段用户量汇总，文档明确标注这一限制。

## 不应声称的内容

- 不声称提出新的推荐算法或聚类算法。
- 不把阶段汇总说成严格 session 漏斗。
- 不声称当前 JDK HttpServer 可直接公网生产部署。
- 不声称 MySQL、Hive 和文件系统之间存在分布式事务。
