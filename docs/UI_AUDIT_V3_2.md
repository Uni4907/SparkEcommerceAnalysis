# V3.2 UI 审计

审计日期：2026-09-14

审计基线：`d7706b5 docs: complete v3 final acceptance`

## 审计结论

V3.1 平台链路完整，但界面仍是开发后台形态；Legacy Dashboard 视觉和图表语义更成熟，却只读取单数据集静态 JSON。V3.2 应保留 V3 REST、Dataset、Job、Capability 和版本能力，把 Legacy 的卡片、蓝色视觉、双栏图表和业务化标题移植到一个主入口。不得 iframe Legacy，也不得读取 `data/*.json` 冒充 V3 结果。

## V3.1 页面现状

| 页面 | 现状 | 问题 | V3.2 决策 |
|---|---|---|---|
| Overview | 5 张原始值卡片 | 英文、字节/行数未格式化、无任务与图表预览 | 重做为平台总览 |
| Datasets | 注册、上传、原始对象表 | 列过多、内部名称突出、详情分散 | 合并为数据集管理 |
| Dataset Detail | JSON `pre` + Capability 文本 | 不适合答辩、留白大 | 改为内部 Tabs |
| Field Mapping | 独立顶层页 | 与数据集上下文割裂 | 合并到数据集管理 |
| Jobs | 原始任务表和模块 checkbox | 无筛选、状态未中文化、详情分散 | 合并为分析任务 |
| Job Detail | JSON 与日志 | 无概览、阶段、结果版本 | 改为同页详情面板 |
| Analytics | 单选择框、单通用柱图 | 图表语义弱、不能形成分析看板 | 重做为 Capability-aware 分析中心 |
| Legacy Dashboard | 顶层独立链接 | 用户感知为两套系统 | 从主导航移除，保留隐藏历史路径 |

当前顶层导航共 8 个入口，超过目标 5 个。可见英文包括 Overview、Datasets、Dataset Detail、Field Mapping、Jobs、Job Detail、Analytics、Enabled、Status、Rows、Size 等。

## Legacy 可复用资产

- 深蓝/品牌蓝 Header、白色卡片、浅灰背景。
- 核心数字卡片的居中数值与次级标签。
- 两列图表网格、统一 360px 图表高度。
- ECharts 饼图、柱图、折线图、横向排名图的配置思路。
- 中文图表标题、千分位数值、行为中文标签。
- 面板、阴影、圆角和响应式降为单列的方式。

复用方式是重新绑定 V3 API 数据；不复制 Legacy 的 `getJson("data/...")` 数据访问层。

## 不可直接迁入的 Legacy 内容

- KMeans、A/F/R、详细行为路径、时间异常、性能基准已在 V3.1 标记 `NOT MIGRATED`。
- Legacy 用户活跃度、购买力和详细画像没有对应 V3 版本结果。
- 旧漏斗口径和 V3 阶段汇总口径不同，不得混用标题或数值。
- Legacy 静态 `spark/output/data/*.json` 不携带当前 `dataset_id/job_id`，不得用于 V3 主分析中心。

## V3 API 与展示映射

| UI 区域 | V3 API |
|---|---|
| 全局数据集选择 | `GET /api/datasets` |
| 数据集信息 | `GET /api/datasets/{id}` |
| 数据预览/映射 | `GET /api/datasets/{id}/mapping` 的字段样例 |
| 能力检测 | `GET /api/datasets/{id}/capabilities` |
| 任务列表/详情/日志 | `GET /api/jobs`、`/api/jobs/{id}`、`/logs` |
| 结果版本 | 新增只读 `GET /api/jobs/{id}/results` |
| 分析图表 | `GET /api/datasets/{id}/analytics/{module}` |
| 系统状态 | 新增只读 `GET /api/status` |

新增 API 只补足展示所需的运行状态和结果版本，不修改 Spark 指标、Hive/MySQL schema、Job 状态机或结果版本语义。

## 信息架构

顶层固定为：

```text
平台总览｜数据集管理｜分析任务｜分析中心｜系统状态
```

- 数据集内部：基本信息、数据预览、字段映射、能力检测、分析记录。
- 任务内部：任务列表、任务概览、执行进度、日志、结果版本。
- 分析中心：按 Capability 生成中文分析 Tabs，不支持项不显示。

## 视觉问题与修正

- 主内容最大宽度从单纯 1280px 扩展为适配 1366/1440/1920 的流式容器。
- 消除首页只有卡片的大面积空白，增加任务、质量和两张核心预览图。
- 统一中文字体、标题、间距、圆角、边框、阴影和空状态。
- READY/RUNNING/SUCCESS/FAILED/PENDING 统一为中文 Badge。
- 文件大小、行数、时间统一格式化。
- 内部 ID 只作为次要信息；正式淘宝数据优先显示“淘宝用户行为数据（正式数据）”。

## 验收边界

Phase 1～8 完成后暂停，提供新导航、平台总览、数据集管理和分析中心截图、修改清单与新增 API 理由。Phase 9～12 需等待 Uni/Lumi 视觉审查通过后再执行。

