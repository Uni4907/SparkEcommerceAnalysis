# 分析模块

## 能力与模块

Capability 表示标准字段足以支持某类分析；Module 是 Job 实际执行和输出的稳定名称。一个能力可以驱动多个模块，例如 `time_analysis` 同时启用 `daily_trend` 和 `hourly`。

| 模块 | 所需能力 | 行为数据 | 订单数据 | 主要输出 |
|---|---|---:|---:|---|
| `overview` | `overview` | 是 | 是 | 记录、用户、商品、购买或订单概览 |
| `daily_trend` | `time_analysis` | 是 | 是 | 每日事件/购买或订单/收入 |
| `hourly` | `time_analysis` | 是 | 是 | 小时分布 |
| `behavior` | `behavior_analysis` | 是 | 否 | 各行为事件量 |
| `funnel` | `funnel_analysis` | 是 | 否 | 各阶段事件数和用户数 |
| `items` | `item_analysis` | 是 | 是 | Top 20 商品 |
| `categories` | `category_analysis` | 是 | 是 | Top 20 类目 |
| `user_segmentation` | `segmentation_analysis` | 是 | 是 | 规则用户分群汇总 |
| `repurchase` | `repurchase_analysis` | 是 | 是 | 购买用户、复购用户、复购率 |
| `sales` | `sales_analysis` | 否 | 是 | 订单、买家、数量和收入 |

`order_analysis`、`revenue_analysis` 是能力报告中的可解释能力，目前不对应额外独立输出模块；订单概览和收入体现在 `overview`、`daily_trend` 与 `sales`。

## 核心口径

- 总记录数：标准行为模型有效记录数。
- 用户数：行为模型 `user_id` 去重数。
- PV：`event_type='pv'` 记录数。
- 购买数：`event_type='buy'` 记录数。
- 购买用户数：购买事件中的 `user_id` 去重数。
- 行为复购用户：购买次数至少 2 的用户。
- 订单复购用户：不同订单数至少 2 的用户。

## 漏斗限制

淘宝源数据没有 `session_id`。当前 funnel 是各阶段事件量和阶段用户量汇总，不是严格的同会话顺序转化链路；论文和演示不得把它描述为 session 级漏斗。

## 正式全量结果

正式 `dataset_id=2/job_id=5` 执行 9 个行为模块并成功生成 manifest。五项核心口径与 Legacy 完全一致，因此未生成 `METRIC_DIFF_REPORT.md`。

