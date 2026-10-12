# REST API 文档

默认地址：`http://127.0.0.1:8090`

响应使用 JSON；任务日志使用 UTF-8 文本。错误响应形如：

```json
{"error":"错误说明"}
```

## 健康检查

### GET /api/health

返回服务状态和 Repository 模式。正式验收应看到 `repository=mysql`。

### GET /api/status

返回 UI 系统状态页使用的真实运行信息，包括 API、MySQL、Hive、Spark、活动任务、数据集/任务数量、Warehouse 路径及可访问状态。该接口只读，不以写死状态代替探测结果。

## 数据集

### GET /api/datasets

列出所有数据集。

### POST /api/datasets

登记已有 CSV，不复制文件。

```json
{
  "name": "Taobao UserBehavior Formal",
  "description": "official dataset",
  "datasetType": "TAOBAO_BEHAVIOR",
  "filePath": "C:\\path\\UserBehavior.csv",
  "hasHeader": false,
  "delimiter": ","
}
```

### POST /api/datasets/upload?filename=name.csv

请求体为原始 CSV 字节。上传受大小限制，文件名会清理，目标路径必须位于配置的数据目录。大型正式数据建议原地注册而非上传。

### GET /api/datasets/{id}

读取单个数据集。

## Mapping 与 Capability

### GET /api/datasets/{id}/mapping

读取推荐或已确认映射。

### POST /api/datasets/{id}/mapping/recommend

重新读取当前推荐。

### PUT /api/datasets/{id}/mapping

提交完整 Mapping 数组。只有确认后的标准字段参与能力检测。

### GET /api/datasets/{id}/capabilities

返回 `enabled` 集合和 `disabledReasons`，后者列出每项能力缺少的标准字段。

## 任务

### GET /api/jobs

按 id 倒序列出任务。

### POST /api/jobs

```json
{"datasetId":2,"modules":["overview","behavior"]}
```

空模块数组表示执行该数据集的全部可用模块。成功创建返回 HTTP 202 和 `PENDING` job；执行随后转为 `RUNNING`，最终为 `SUCCESS` 或 `FAILED`。

### GET /api/jobs/{id}

读取状态、PID、退出码、错误和时间。

### GET /api/jobs/{id}/logs

读取该任务独立日志。

### GET /api/jobs/{id}/results

读取指定任务的全部不可覆盖结果版本。用于任务详情页同时展示模块、版本和生成时间。

## 结果

### GET /api/datasets/{id}/analytics/{module}

返回该数据集和模块的 latest SUCCESS JSON。文件路径必须位于配置的结果根目录，否则拒绝读取。没有成功版本时返回 HTTP 400。

## HTTP 状态

- 200：读取或更新成功。
- 201：数据集或上传创建成功。
- 202：异步任务已接受。
- 400：请求、状态、能力或结果不可用。
- 404：路由不存在。
- 500：未预期的服务错误。

## 安全边界

当前 API 没有用户认证、TLS、CSRF 或细粒度授权，默认只绑定 `127.0.0.1`。不得直接暴露到公网；远程部署前应增加反向代理、认证和访问控制，或在 API 复杂度增长后迁移 Spring Boot。
