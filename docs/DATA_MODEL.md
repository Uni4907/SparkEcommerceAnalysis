# 数据模型

## 标准行为模型

Hive 表：`ecommerce_v3.dwd_behavior_event`

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | STRING | 用户标识 |
| `item_id` | STRING | 商品标识 |
| `category_id` | STRING | 类目标识 |
| `event_type` | STRING | `pv/cart/fav/buy` |
| `event_time` | BIGINT | Unix 秒时间戳 |
| `dataset_id` | BIGINT | Hive 分区键 |

## 标准订单模型

Hive 表：`ecommerce_v3.dwd_order`

| 字段 | 类型 | 说明 |
|---|---|---|
| `order_id` | STRING | 订单标识 |
| `user_id` | STRING | 用户标识 |
| `item_id` | STRING | 商品标识，可空 |
| `category_id` | STRING | 类目标识，可空 |
| `order_time` | BIGINT | Unix 秒时间戳 |
| `quantity` | DOUBLE | 数量 |
| `unit_price` | DOUBLE | 单价 |
| `amount` | DOUBLE | 金额 |
| `dataset_id` | BIGINT | Hive 分区键 |

## 运行模型

- `DatasetRecord`：文件路径、大小、行数、格式、状态和时间。
- `FieldMapping`：源字段、序号、标准字段、推断类型、置信度、样例和确认状态。
- `CapabilityReport`：启用能力与各禁用能力缺失字段。
- `AnalysisJob`：状态、模块、日志、PID、退出码和时间。
- `ResultVersion`：数据集、任务、模块、结果路径和创建时间。
- `WorkerSpec/WorkerManifest`：主服务与 Spark 子进程之间的输入和完成协议。

## 约束

- 所有 DWD 行必须携带 `dataset_id`。
- 所有外部分析结果必须同时携带 `dataset_id` 和 `job_id`。
- 同一数据集重跑时动态覆盖其 Hive 分区，不影响其他数据集。
- 已成功的历史结果目录不原位覆盖。

