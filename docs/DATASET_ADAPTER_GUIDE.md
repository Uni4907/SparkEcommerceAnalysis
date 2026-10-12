# 数据集适配器指南

## 内置适配器

### TaobaoBehaviorAdapter

适用于 `datasetType=TAOBAO_BEHAVIOR` 的五列无表头 CSV：

```text
user_id,item_id,category_id,event_type,event_time
```

适配器使用显式 Schema，规范化事件类型，并过滤缺少用户、商品、时间或事件类型不在 `pv/cart/fav/buy` 中的记录。

### GenericCSVAdapter

适用于有表头或已完成字段映射的通用 CSV。它根据已确认 Mapping 构建标准行为模型或订单模型，不依赖文件名判断能力。

## 接入新数据集

1. 在 Dataset 页面登记 CSV 路径、类型、表头和分隔符。
2. 检查系统按字段名、类型和样例给出的推荐。
3. 将源字段映射到标准字段并确认。
4. 检查 Capability 页面显示的启用和禁用项。
5. 仅运行已启用模块。

## 新增专用适配器

1. 实现 `DatasetAdapter`。
2. 在 `supports` 中使用明确的 dataset type 契约。
3. 在 `adapt` 中只输出 `StandardDataset`，不要让分析模块读取源字段。
4. 在 `AdapterRegistry` 中注册，专用适配器应排在通用适配器之前。
5. 添加样本、字段映射、能力和结果隔离测试。

## 最低质量要求

- 不扫描大文件来猜测全部行数；探测必须有界。
- 不复制正式大文件。
- 类型转换失败应变为可识别空值或明确失败。
- 不允许从文件名直接启用分析模块。
- 适配后的每一行必须携带正确 `dataset_id`。

