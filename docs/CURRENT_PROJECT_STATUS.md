# 当前项目状态

更新日期：2026-09-14

## 结论

V3.2 已完成 Phase 1～12 和最终验收。正式 MySQL、Hive、Spark 既有全量结果、REST API、统一中文 Dashboard、双数据集差异化展示和浏览器验收均已验证。B 是唯一代码主线，A 仅作为只读 Legacy 基线和正式数据源。

`System Demo Ready: YES`

## 代码状态

- 主工程：当前 Git 仓库根目录
- 分支：`v3.2-ui`
- V3.2 UI 检查点：`b085e33`
- 指标卡精简：`5529df7`
- Legacy 快照：`legacy-completed`，提交 `4ceb5d0`
- V3 Phase 14 检查点：`fba5cf2`
- 跨环境任务修复：`4e62e93`
- A 数据源：外部原始 `UserBehavior.csv`（未纳入仓库）
- A 原始文件未复制、未修改。

## 已完成能力

- Dataset 注册与受限 CSV 上传。
- 字段别名、类型和样例驱动的 Mapping 推荐与确认。
- 基于已确认标准字段的 Capability 动态启停。
- Taobao Behavior 与通用 CSV/订单适配器。
- Hive `dwd_behavior_event`、`dwd_order` 标准模型及 `dataset_id` 分区隔离。
- Spark 子进程任务、状态、PID、退出码和日志。
- `results/{dataset_id}/{job_id}` 结果版本与 latest SUCCESS。
- MySQL 数据集、映射、任务和结果索引持久化。
- REST API 与统一五项导航 V3.2 页面；Legacy Dashboard 仅保留历史入口。

## 正式验收快照

- 正式数据集：`dataset_id=2`
- 首次 Hive 缺表失败记录：`job_id=4`，状态 `FAILED`
- 全模块成功：`job_id=5`，9 个模块
- Overview 重跑成功：`job_id=6`
- latest Overview：`job_id=6`
- Hive 分区：仅 `dataset_id=2`
- Hive 行数：100,150,807
- MySQL 正式数据结果版本：10 条
- 五项 Legacy 基准：全部精确一致
- 指标差异报告：不需要生成
- 订单 UI 验证：`job_id=7`，8 个模块、8 个结果版本
- 浏览器验收：12 个场景，1366×768，无横向溢出，0 个未捕获异常

## 已知非阻塞警告

- Windows 本地 Spark 日志会出现 SLF4J 无绑定警告。
- Hive 本地模式可能打印 Hadoop 权限命令帮助文本。
- 上述信息未导致任务失败、数据缺失或指标偏差；正式成功任务退出码均为 0。
