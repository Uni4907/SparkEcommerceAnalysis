package com.uni.platform.dataset;

import com.uni.platform.capability.CapabilityDetector;
import com.uni.platform.mapping.SchemaMapper;
import com.uni.platform.model.PlatformModels.CapabilityReport;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.DatasetStatus;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.RegistrationRequest;
import com.uni.platform.persistence.PlatformRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatasetManager {
    private final PlatformRepository repository;
    private final CsvInspector inspector;
    private final SchemaMapper mapper;
    private final CapabilityDetector detector;

    public DatasetManager(PlatformRepository repository) {
        this(repository, new CsvInspector(), new SchemaMapper(), new CapabilityDetector());
    }

    public DatasetManager(PlatformRepository repository, CsvInspector inspector,
                          SchemaMapper mapper, CapabilityDetector detector) {
        this.repository = repository;
        this.inspector = inspector;
        this.mapper = mapper;
        this.detector = detector;
    }

    public DatasetRecord register(RegistrationRequest request) throws IOException {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        Path path = Paths.get(request.filePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("数据文件不存在: " + path);

        CsvInspector.Inspection inspection = inspector.inspect(path, request.hasHeader(), request.delimiter());
        String now = Instant.now().toString();
        DatasetRecord created = repository.createDataset(new DatasetRecord(0, request.name(),
                text(request.description()), type(request.datasetType()), path.toString(), Files.size(path),
                inspection.rowCount(), DatasetStatus.MAPPING_REQUIRED, now, now, null,
                inspection.hasHeader(), inspection.delimiter()));

        repository.replaceMappings(created.id(),
                mapper.recommend(created.id(), created.datasetType(), inspection.fields()));
        return created;
    }

    public List<FieldMapping> mappings(long datasetId) {
        requireDataset(datasetId);
        return repository.findMappings(datasetId);
    }

    public List<FieldMapping> confirmMappings(long datasetId, List<FieldMapping> requested) {
        DatasetRecord dataset = requireDataset(datasetId);
        List<FieldMapping> confirmed = new ArrayList<>();
        Set<String> standardFields = new HashSet<>();
        for (FieldMapping mapping : requested) {
            if (mapping.standardField() == null || mapping.standardField().isBlank()) continue;
            if (!SchemaMapper.STANDARD_FIELDS.contains(mapping.standardField())) {
                throw new IllegalArgumentException("未知标准字段: " + mapping.standardField());
            }
            if (!standardFields.add(mapping.standardField())) {
                throw new IllegalArgumentException("标准字段重复映射: " + mapping.standardField());
            }
            confirmed.add(new FieldMapping(datasetId, mapping.sourceField(), mapping.sourceIndex(),
                    mapping.standardField(), mapping.inferredType(), mapping.confidence(), true,
                    mapping.samples() == null ? List.of() : mapping.samples()));
        }
        if (confirmed.isEmpty()) throw new IllegalArgumentException("至少确认一个字段映射");
        repository.replaceMappings(datasetId, confirmed);
        repository.updateDataset(dataset.withStatus(DatasetStatus.READY, Instant.now().toString()));
        return List.copyOf(confirmed);
    }

    public CapabilityReport capabilities(long datasetId) {
        requireDataset(datasetId);
        return detector.detect(datasetId, repository.findMappings(datasetId));
    }

    public DatasetRecord get(long id) { return requireDataset(id); }
    public List<DatasetRecord> list() { return repository.listDatasets(); }

    private DatasetRecord requireDataset(long id) {
        return repository.findDataset(id).orElseThrow(() -> new IllegalArgumentException("dataset 不存在: " + id));
    }
    private static String text(String value) { return value == null ? "" : value.strip(); }
    private static String type(String value) { return value == null || value.isBlank() ? "GENERIC_CSV" : value.strip().toUpperCase(); }
}
