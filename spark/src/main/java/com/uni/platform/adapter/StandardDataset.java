package com.uni.platform.adapter;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/** Adapter output. Analysis code is not allowed to read the source CSV directly. */
public record StandardDataset(long datasetId, Dataset<Row> behaviorEvents, Dataset<Row> orders) {
    public boolean hasBehavior() { return behaviorEvents != null; }
    public boolean hasOrders() { return orders != null; }
}
