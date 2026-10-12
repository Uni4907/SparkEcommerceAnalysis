package com.uni.platform.adapter;

import com.uni.platform.model.PlatformModels.DatasetRecord;

import java.util.List;

public class AdapterRegistry {
    private final List<DatasetAdapter> adapters = List.of(new TaobaoBehaviorAdapter(), new GenericCSVAdapter());

    public DatasetAdapter select(DatasetRecord dataset) {
        return adapters.stream().filter(a -> a.supports(dataset)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有可用 Adapter: " + dataset.datasetType()));
    }
}
