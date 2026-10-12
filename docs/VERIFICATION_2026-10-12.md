# 本地验证记录（2026-10-12）

本记录用于说明仓库整理后的可复现检查结果，不代表对其他机器、其他数据集或生产环境的性能承诺。

## 验证环境

- Windows 10
- Java 17.0.10
- Maven 3.9.16
- Apache Spark 3.5.0（项目依赖）
- Hadoop Windows 原生库 3.3.6

## 自动化测试

在 `spark` 目录使用 Java 17、配置 `HADOOP_HOME` 后执行：

```powershell
mvn test
```

结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖的核心场景：

1. 字段推荐同时参考别名、类型和样例值。
2. 订单数据集在字段不足时禁用行为漏斗。
3. 行为与订单两类适配器及分析结果彼此隔离。
4. Hive 标准模型保留并隔离 `dataset_id`。

测试期间出现 SLF4J 未绑定实现的提示，但不影响测试结果；项目当前使用 Spark 依赖提供的日志体系。

## 前端静态检查

```powershell
node --check spark/src/main/resources/web/platform.js
```

结果：退出码为 0，无语法错误。

## UI 证据

`docs/screenshots/v3.2/` 中保留 12 张既有浏览器验收截图，对应清单见 `docs/UI_ACCEPTANCE_V3_2.md`。截图中的正式数据指标来自既有验收记录，本次整理未重新运行原始大数据，也未修改或上传原始数据。
