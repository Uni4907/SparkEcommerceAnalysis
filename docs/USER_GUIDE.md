# 用户指南

## 启动

先按 `DEPLOYMENT.md` 配置 Java、Hadoop 和 MySQL 环境变量，然后运行：

```powershell
.\scripts\start-v3.ps1 -Repository mysql -EnableHive -MaxHeap 12g
```

打开 `http://127.0.0.1:8090/platform.html`。

## 页面说明

### 平台总览

显示当前数据集、最近成功分析、数据规模、可用模块、平台状态、最近任务、数据质量和分析预览。正式环境的系统状态应显示 MySQL 正常。

### 数据集管理

有两种接入方式：

- 原地注册：填写现有 CSV 的绝对路径，适合正式大文件。
- 上传：把较小 CSV 保存到平台数据目录，再注册返回路径。

填写 name、description、datasetType、filePath、hasHeader 和 delimiter 后提交。

### 数据集内部详情

查看文件大小、行数、状态、格式和 Capability。大型文件注册时行数可能先显示 `-1`，成功分析后更新为实际行数。

### 字段映射

检查每个源字段的标准字段、类型、置信度和样例。确认无误后保存；只有确认映射的数据集才能运行任务。

### 分析任务

系统只勾选当前数据集可用的模块。提交后任务依次经历：

```text
PENDING -> RUNNING -> SUCCESS | FAILED
```

不要关闭正在运行全量任务的主服务。

### 任务内部详情

查看任务 id、PID、退出码、错误和日志。失败后应先修复日志中的问题，再重新提交；不要删除失败证据。

### 分析中心

模块标签由当前数据集 Capability 动态决定。淘宝数据展示行为、路径和趋势类模块，订单数据展示销售类模块。系统只返回当前数据集该模块的 latest SUCCESS；标题区显示实际 `job_id`。

### 系统状态

显示 API、MySQL、Hive、Spark 和 Warehouse 的真实运行状态，以及 Repository 模式、任务和数据集数量。

## 正式淘宝数据流程

1. 注册 `UserBehavior.csv`，选择 `TAOBAO_BEHAVIOR`、无表头、逗号分隔。
2. 确认 `_c0`～`_c4` 映射为 user、item、category、event type 和 event time。
3. 检查 Behavior、Funnel、Item、Category、Time、Segmentation、Repurchase、Overview 能力启用。
4. 在 Jobs 提交全部可用模块。
5. 等待 SUCCESS 并查看日志。
6. 在 Analytics 核对 overview、behavior、funnel 和 repurchase。

## 订单数据流程

1. 注册有表头订单 CSV。
2. 映射 order_id、user_id、order_time，以及可用的 item/category/quantity/price/amount。
3. 无行为事件时 funnel 应自动禁用。
4. 运行 overview、daily_trend、hourly、items、categories、segmentation、repurchase 和 sales 中可用的模块。

## 常见问题

### 页面显示 API 请求失败

确认服务仍运行、端口正确，并访问 `/api/health`。

### MySQL 模式无法启动

确认目标 database 已存在，三个 `PLATFORM_JDBC_*` 变量已被当前进程继承，账号拥有建表和 CRUD 权限。

### 出现 NativeIO 或 hadoop.dll 错误

确认 `HADOOP_HOME\bin` 同时包含 `winutils.exe` 和 `hadoop.dll`，且已加入 PATH。

### 任务失败后数据集还能重跑吗？

可以。任务保留 FAILED 证据，数据集恢复 READY；修复环境或数据问题后重新提交。

### 为什么分析中心不是刚失败任务的结果？

Analytics 始终选择 latest SUCCESS。失败任务不会替换已验证的成功版本。
