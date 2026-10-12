package com.uni.platform;

import com.uni.platform.adapter.AdapterRegistry;
import com.uni.platform.adapter.StandardDataset;
import com.uni.platform.analysis.AnalysisRegistry;
import com.uni.platform.analysis.GenericAnalysisEngine;
import com.uni.platform.capability.CapabilityDetector;
import com.uni.platform.dataset.DatasetManager;
import com.uni.platform.mapping.SchemaMapper;
import com.uni.platform.model.PlatformModels.CapabilityReport;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.FieldProfile;
import com.uni.platform.model.PlatformModels.RegistrationRequest;
import com.uni.platform.model.StandardModelWriter;
import com.uni.platform.persistence.InMemoryPlatformRepository;
import com.uni.platform.result.VersionedResultWriter;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCoreTest {
    private static SparkSession spark;
    private static Path sparkTemp;

    @BeforeAll static void startSpark() throws Exception {
        sparkTemp = Path.of("target", "hive-test-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(sparkTemp);
        System.setProperty("hadoop.home.dir", "D:\\Environment\\hadoop-3.3.6");
        spark = SparkSession.builder().appName("v3-dual-dataset-test").master("local[2]")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "2")
                .config("spark.sql.warehouse.dir", sparkTemp.resolve("warehouse").toString())
                .config("spark.local.dir", sparkTemp.resolve("spark-local").toString())
                .config("spark.hadoop.javax.jdo.option.ConnectionURL",
                        "jdbc:derby:;databaseName=" + sparkTemp.resolve("metastore") + ";create=true")
                .enableHiveSupport()
                .getOrCreate();
    }

    @AfterAll static void stopSpark() { if (spark != null) spark.stop(); }

    @Test void recommendationsUseAliasesTypesAndSamples() {
        SchemaMapper mapper = new SchemaMapper();
        List<FieldMapping> mappings = mapper.recommend(9, "GENERIC_CSV", List.of(
                new FieldProfile("buyer_id", 0, "string", List.of("U01")),
                new FieldProfile("amount", 1, "double", List.of("12.50")),
                new FieldProfile("action", 2, "string", List.of("pv", "buy"))));
        assertEquals("user_id", mappings.get(0).standardField());
        assertEquals("amount", mappings.get(1).standardField());
        assertEquals("event_type", mappings.get(2).standardField());
        assertTrue(mappings.get(2).confidence() >= 0.95);
    }

    @Test void capabilityDetectorDisablesFunnelForOrders() {
        List<FieldMapping> order = List.of("order_id", "user_id", "item_id", "order_time", "amount")
                .stream().map(name -> new FieldMapping(1, name, 0, name, "string", 1, true, List.of())).toList();
        CapabilityReport report = new CapabilityDetector().detect(1, order);
        assertTrue(report.enabled().contains("order_analysis"));
        assertTrue(report.enabled().contains("revenue_analysis"));
        assertFalse(report.enabled().contains("funnel_analysis"));
        assertTrue(report.disabledReasons().get("funnel_analysis").contains("event_type"));
    }

    @Test void dualDatasetAdaptersAndAnalysisAreIsolated(@TempDir Path temp) throws Exception {
        InMemoryPlatformRepository repo = new InMemoryPlatformRepository();
        DatasetManager manager = new DatasetManager(repo);
        Path root = Path.of("..").toAbsolutePath().normalize();
        DatasetRecord behavior = manager.register(new RegistrationRequest("Taobao Behavior Test", "test only",
                "TAOBAO_BEHAVIOR", root.resolve("sample-data/sample_taobao_behavior.csv").toString(), false, ","));
        DatasetRecord orders = manager.register(new RegistrationRequest("Order Sample", "test only",
                "GENERIC_CSV", root.resolve("sample-data/sample_order.csv").toString(), true, ","));
        behavior = confirm(manager, behavior);
        orders = confirm(manager, orders);

        CapabilityReport bc = manager.capabilities(behavior.id());
        CapabilityReport oc = manager.capabilities(orders.id());
        assertTrue(bc.enabled().containsAll(List.of("behavior_analysis", "funnel_analysis", "item_analysis",
                "category_analysis", "segmentation_analysis", "repurchase_analysis")));
        assertFalse(bc.enabled().contains("order_analysis"));
        assertTrue(oc.enabled().containsAll(List.of("order_analysis", "sales_analysis", "revenue_analysis",
                "repurchase_analysis", "item_analysis", "category_analysis")));
        assertFalse(oc.enabled().contains("funnel_analysis"));

        AdapterRegistry adapters = new AdapterRegistry();
        StandardDataset b = adapters.select(behavior).adapt(spark, behavior, manager.mappings(behavior.id()));
        StandardDataset o = adapters.select(orders).adapt(spark, orders, manager.mappings(orders.id()));
        assertEquals(12, b.behaviorEvents().count());
        assertEquals(10, o.orders().count());
        assertEquals(behavior.id(), b.behaviorEvents().select("dataset_id").distinct().first().getLong(0));
        assertEquals(orders.id(), o.orders().select("dataset_id").distinct().first().getLong(0));

        AnalysisRegistry registry = new AnalysisRegistry();
        List<String> bm = registry.validateRequested(List.of(), bc);
        List<String> om = registry.validateRequested(List.of(), oc);
        VersionedResultWriter bw = new VersionedResultWriter(behavior.id(), 101, temp);
        VersionedResultWriter ow = new VersionedResultWriter(orders.id(), 102, temp);
        new GenericAnalysisEngine().run(b, bm, bw);
        new GenericAnalysisEngine().run(o, om, ow);
        assertTrue(Files.isRegularFile(bw.jsonPath("funnel")));
        assertTrue(Files.isRegularFile(ow.jsonPath("sales")));
        assertFalse(Files.exists(ow.jsonPath("funnel")));
        assertTrue(Files.readString(ow.jsonPath("sales")).contains("\"dataset_id\":" + orders.id()));
        assertTrue(Files.readString(ow.jsonPath("sales")).contains("\"job_id\":102"));
    }

    @Test void hiveStandardModelsKeepDatasetId() throws Exception {
        spark.sql("DROP DATABASE IF EXISTS ecommerce_v3 CASCADE");
        String ddl = Files.readString(Path.of("..", "sql", "v3_hive.sql").toAbsolutePath())
                .replaceAll("(?m)^\\s*--.*$", "");
        for (String statement : ddl.split(";")) {
            if (!statement.isBlank()) spark.sql(statement);
        }
        StandardDataset standard = new StandardDataset(77,
                spark.sql("SELECT 77L dataset_id,'U1' user_id,'P1' item_id,'C1' category_id,'buy' event_type,1511568000L event_time"),
                spark.sql("SELECT 77L dataset_id,'O1' order_id,'U1' user_id,'P1' item_id,'C1' category_id,1511568000L order_time,2D quantity,10D unit_price,20D amount"));
        new StandardModelWriter().appendToHive(standard);
        assertEquals(77L, spark.sql("SELECT dataset_id FROM ecommerce_v3.dwd_behavior_event").first().getLong(0));
        assertEquals(77L, spark.sql("SELECT dataset_id FROM ecommerce_v3.dwd_order").first().getLong(0));
    }

    private static DatasetRecord confirm(DatasetManager manager, DatasetRecord dataset) {
        List<FieldMapping> confirmed = manager.mappings(dataset.id()).stream()
                .filter(m -> m.standardField() != null)
                .map(m -> new FieldMapping(dataset.id(), m.sourceField(), m.sourceIndex(), m.standardField(),
                        m.inferredType(), m.confidence(), true, m.samples())).toList();
        manager.confirmMappings(dataset.id(), confirmed);
        return manager.get(dataset.id());
    }
}
