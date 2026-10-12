# 部署与运行

## 环境要求

- Windows 10/11
- JDK 17
- Maven 3.6+
- Spark 3.5.x / 项目 Maven Spark 3.5.0
- Hadoop 3.3.6 Windows 原生文件：`winutils.exe` 与 `hadoop.dll`
- MySQL 8.x
- 正式全量运行建议 12GB Java 堆和至少 15GB 可用磁盘

## MySQL 准备

目标 database 必须预先存在。应用账号至少需要 `CREATE`、`SELECT`、`INSERT`、`UPDATE`、`DELETE`、`INDEX` 和 `REFERENCES` 权限。

以安全方式设置进程环境变量，不要把密码写入仓库：

```powershell
$env:PLATFORM_JDBC_URL='jdbc:mysql://127.0.0.1:3306/ecommerce_v3'
$env:PLATFORM_JDBC_USER='<application-user>'
$env:PLATFORM_JDBC_PASSWORD='<secret>'
```

如果使用 Windows 用户环境变量，需重新打开终端或应用，使新进程继承变量。

## 正式启动

在工程根目录执行：

```powershell
.\scripts\start-v3.ps1 `
  -Repository mysql `
  -EnableHive `
  -MaxHeap 12g `
  -Port 8090 `
  -RuntimeDir 'C:\data\ecommerce-v3-runtime'
```

特殊环境可附加：

```powershell
-JavaHome 'C:\Program Files\Java\jdk-17' `
-HadoopHome 'D:\Environment\hadoop-3.3.6' `
-MavenLocalRepository 'C:\Users\user\.m2\repository'
```

启动脚本会检查 JDK、Hadoop 原生文件以及三个 MySQL 环境变量。正式模式应启用 `-EnableHive`；省略后只生成版本结果，不写 Hive 标准模型。

## 验证启动

```powershell
Invoke-RestMethod http://127.0.0.1:8090/api/health
```

预期：

```json
{"status":"UP","repository":"mysql"}
```

浏览器访问：

- V3：`http://127.0.0.1:8090/platform.html`
- Legacy：`http://127.0.0.1:8090/index.html`

## 测试

```powershell
.\scripts\test-v3.ps1
```

Windows 出现 `NativeIO$Windows.access0` 时，检查 `hadoop.dll`、`HADOOP_HOME` 和 PATH，并确保 Java 进程有权加载原生 DLL。

## 目录

```text
runtime/
├── datasets/                  可选上传文件
├── jobs/{job_id}/             job-spec.json 与 job.log
└── results/
    ├── {dataset_id}/{job_id}/ data 与 manifest
    └── spark-warehouse/       Hive 本地 warehouse
```

## 停止、备份与恢复

- 前台服务使用 `Ctrl+C` 停止。
- 备份 MySQL 四张元数据表、runtime results 和 Hive warehouse/metastore。
- 不备份或复制 A 的 3.67GB 原文件时，必须保证原绝对路径持续可读。
- 代码回滚可切换 `legacy-completed`；数据回滚应使用数据库和 runtime 备份，不能依赖 Git。

## 部署限制

当前实现面向单机、本地或受信任内网演示。JDK HttpServer 没有生产级身份认证、TLS、限流和多节点调度；需要这些能力时应先设计安全边界，再评估 Spring Boot 或外部网关。

