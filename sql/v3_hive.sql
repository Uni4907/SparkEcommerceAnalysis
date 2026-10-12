CREATE DATABASE IF NOT EXISTS ecommerce_v3;
USE ecommerce_v3;

CREATE TABLE IF NOT EXISTS dwd_behavior_event (
    user_id STRING,
    item_id STRING,
    category_id STRING,
    event_type STRING,
    event_time BIGINT
)
PARTITIONED BY (dataset_id BIGINT)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd_order (
    order_id STRING,
    user_id STRING,
    item_id STRING,
    category_id STRING,
    order_time BIGINT,
    quantity DOUBLE,
    unit_price DOUBLE,
    amount DOUBLE
)
PARTITIONED BY (dataset_id BIGINT)
STORED AS PARQUET;

-- DWS/ADS tables must follow the same rule: dataset_id is mandatory;
-- job_id is mandatory on versioned result rows written outside DWD.
