CREATE TABLE IF NOT EXISTS dataset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    dataset_type VARCHAR(64) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    row_count BIGINT NOT NULL DEFAULT -1,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    last_analyzed_at TIMESTAMP(3) NULL,
    has_header BOOLEAN NOT NULL,
    delimiter_char VARCHAR(8) NOT NULL
);

CREATE TABLE IF NOT EXISTS dataset_field_mapping (
    dataset_id BIGINT NOT NULL,
    source_field VARCHAR(255) NOT NULL,
    source_index INT NOT NULL,
    standard_field VARCHAR(64) NOT NULL,
    inferred_type VARCHAR(32) NOT NULL,
    confidence DOUBLE NOT NULL,
    confirmed BOOLEAN NOT NULL,
    samples_json TEXT NOT NULL,
    PRIMARY KEY (dataset_id, source_index),
    CONSTRAINT fk_mapping_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS analysis_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_modules TEXT NOT NULL,
    log_path TEXT NOT NULL,
    process_id BIGINT NULL,
    exit_code INT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    started_at TIMESTAMP(3) NULL,
    finished_at TIMESTAMP(3) NULL,
    INDEX idx_job_dataset_status (dataset_id, status),
    CONSTRAINT fk_job_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS result_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    module_name VARCHAR(64) NOT NULL,
    result_path TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    INDEX idx_result_latest (dataset_id, module_name, id),
    CONSTRAINT fk_result_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id),
    CONSTRAINT fk_result_job FOREIGN KEY (job_id) REFERENCES analysis_job(id)
);
