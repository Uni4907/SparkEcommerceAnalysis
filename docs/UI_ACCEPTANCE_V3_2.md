# V3.2 UI 验收报告

验收日期：2026-09-14

## 结论

V3.2 Phase 9～12 已完成。统一平台入口、双数据集差异化展示、正式 latest SUCCESS、核心回归和 1366×768 浏览器验收均通过。

`System Demo Ready: YES`

## 双数据集验证

| 数据集 | latest SUCCESS | UI 分析模块 | 结果 |
|---|---:|---|---|
| 淘宝用户行为数据（正式数据） | overview：Job #6；其余模块：Job #5 | 总览、每日趋势、时段分析、用户行为、行为路径、商品、类目、用户分群、复购 | PASS |
| 订单交易样例数据 | Job #7 | 总览、每日趋势、时段分析、商品、类目、用户分群、复购、销售 | PASS |

订单样例仅有 10 行，本轮运行 Job #7 生成 8 个结果版本，用于验证订单 UI。正式 3.67GB 数据没有重新执行，继续复用 Job #5/#6。

## 正式指标复核

| 指标 | Legacy 基准 | V3.2 REST | 差值 |
|---|---:|---:|---:|
| 总记录数 | 100,150,807 | 100,150,807 | 0 |
| 用户数 | 987,994 | 987,994 | 0 |
| PV | 89,716,264 | 89,716,264 | 0 |
| 购买数 | 2,015,839 | 2,015,839 | 0 |
| 购买用户数 | 672,404 | 672,404 | 0 |

指标定义未改变，差值均为 0，因此无需生成 `METRIC_DIFF_REPORT.md`。

## 核心回归

- Maven/JUnit：4 tests，0 failures，0 errors。
- MySQL：服务重启后仍读取 2 个数据集、7 个任务、Job #7 的 8 个结果版本。
- Hive：淘宝与订单全部结果行的 `dataset_id` 检查无串数据。
- Job：Job #4 的 FAILED 状态与日志继续保留；Job #7 SUCCESS、退出码 0。
- latest SUCCESS：订单 sales 指向 Job #7；正式 overview 指向 Job #6；正式其他模块指向 Job #5。
- 结果版本：历史版本保留，没有覆盖旧结果。
- REST：Dataset、Mapping、Capability、Job、Log、Result、Analytics、Status 全部实测通过。

## 浏览器验收

浏览器：本机 Chrome，无头 DevTools；视口：1366×768。

- 12 个页面状态均命中预期数据集、页面和内部标签。
- 12 个页面均无横向溢出。
- Browser Console：0 error。
- Uncaught Exception：0。
- 所有图表读取 V3 REST API，没有读取 Legacy 静态 JSON。

截图位于 `docs/screenshots/v3.2/`：

1. `01_platform_overview.png`
2. `02_datasets.png`
3. `03_dataset_detail.png`
4. `04_field_mapping.png`
5. `05_capabilities.png`
6. `06_jobs.png`
7. `07_job_detail.png`
8. `08_analytics_overview.png`
9. `09_analytics_behavior.png`
10. `10_analytics_trend.png`
11. `11_order_dataset_analytics.png`
12. `12_system_status.png`

机器可读的页面状态、溢出与控制台检查结果见 `browser_acceptance.json`。

## 最终验收清单

- [x] 一个主系统入口
- [x] Legacy Dashboard 不再作为顶层入口
- [x] 顶层导航为 5 项
- [x] 主要 UI 中文化
- [x] 数据集名称用户友好
- [x] 内部 acceptance 名称不作为主要显示
- [x] Dataset Detail、Mapping、Job Detail 使用内部区域
- [x] Legacy 可复用图表整合进 Analysis
- [x] Analysis 使用 V3 REST 数据
- [x] Capability 动态控制模块
- [x] 淘宝和订单数据展示不同
- [x] 状态 Badge 中文
- [x] 数值和时间格式统一
- [x] 无大片无意义空白
- [x] 1366×768 正常演示
- [x] Browser Console 0 uncaught errors
- [x] Maven/JUnit 通过
- [x] 核心 API 回归通过
- [x] V3.1 Final Acceptance 能力未退化
