package com.uni.platform.adapter;

import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import org.apache.spark.sql.SparkSession;

import java.util.List;

public interface DatasetAdapter {
    boolean supports(DatasetRecord dataset);
    StandardDataset adapt(SparkSession spark, DatasetRecord dataset, List<FieldMapping> mappings);
}
