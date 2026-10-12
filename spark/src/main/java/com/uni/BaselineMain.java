package com.uni;

import com.uni.config.AnalysisConfig;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.*;

public class BaselineMain {

    public static void main(String[] args) {

        AnalysisConfig config = AnalysisConfig.load();

        // 创建SparkSession，作为Spark程序入口
        SparkSession spark = SparkSession.builder()
                .appName("SparkEcommerceAnalysis")
                .master("local[*]")
                .getOrCreate();

        //读取UserBehavior.csv文件
        Dataset<Row> df = spark.read()
                .option("header", "false")
                .option("inferSchema", "true")
                .csv(config.dataPath);

        // 给没有表头的数据添加字段名
        df = df.toDF(
                "user_id",
                "item_id",
                "category_id",
                "behavior",
                "timestamp"
        );

        df = df.withColumn(
                "timestamp",
                col("timestamp").cast("long")
        );

        //缓存数据
        df.cache();

        //购买行为数据
        Dataset<Row> buyData =
                df.filter(
                        col("behavior").equalTo("buy")
                );

        //缓存购买数据
        buyData.cache();
        buyData.count();

        // 查看前10条数据
        df.show(10);

        // 查看结构
        df.printSchema();

        // 数据总量
        System.out.println("数据总量：" + df.count());

        // 用户数量
        long totalUserCount = df.select("user_id").distinct().count();
        System.out.println("用户数量：" + totalUserCount);

        // 商品数量
        System.out.println("商品数量：" + df.select("item_id").distinct().count());

        // 商品类别数量
        System.out.println("商品类别数量：" + df.select("category_id").distinct().count());

        // 行为统计
        df.groupBy("behavior")
                .count()
                .show();

        // 用户行为占比分析
        long totalBehaviorCount = df.count();

        df.groupBy("behavior")
                .count()
                .withColumn(
                        "percentage",
                        col("count")
                                .divide(totalBehaviorCount)
                                .multiply(100)
                )
                .show();

        // 计算各行为数量
        long pvCount = df.filter(df.col("behavior").equalTo("pv"))
                .count();

        long buyCount = df.filter(df.col("behavior").equalTo("buy"))
                .count();

        // 浏览到购买转化率
        double pvBuyConversionRate =
                (double) buyCount / pvCount * 100;

        System.out.println("浏览次数：" + pvCount);

        System.out.println("购买次数：" + buyCount);

        System.out.println("浏览购买转化率："
                + pvBuyConversionRate + "%");

        // 加购数量
        long cartCount = df.filter(df.col("behavior").equalTo("cart"))
                .count();

        // 加购购买转化率
        double cartBuyConversionRate =
                (double) buyCount / cartCount * 100;

        System.out.println("加购次数：" + cartCount);

        System.out.println("加购购买转化率："
                + cartBuyConversionRate + "%");

        //收藏数量
        long favCount = df.filter(df.col("behavior").equalTo("fav"))
                .count();

        //收藏购买转化率
        double favBuyConversionRate =
                (double) buyCount / favCount * 100;

        System.out.println("收藏次数：" + favCount);

        System.out.println("收藏购买转化率："
                + favBuyConversionRate + "%");


        //筛选出有过购买行为的用户并去重
        Dataset<Row> buyUsers =
                buyData
                        .select("user_id")
                        .distinct();

        long buyUserCount = buyUsers.count();
        System.out.println("购买用户数：" + buyUserCount);

        //用户购买率
        double userPurchaseRate =
                (double) buyUserCount / totalUserCount * 100;

        System.out.println("用户购买率："
                + userPurchaseRate + "%");

        //统计用户行为次数
        Dataset<Row> userActionCount =
                df.groupBy("user_id")
                        .count()
                        .withColumnRenamed("count", "action_count")
                        .orderBy(col("action_count").desc());
        userActionCount.show(10);

        //计算用户行为次数平均值
        userActionCount
                .select(
                        avg("action_count")
                ).withColumnRenamed(
                        "avg(action_count)",
                        "avg_action_count"
                )
                .show();

        //用户行为次数分级
        Dataset<Row> userActiveLevel =
                userActionCount
                        .withColumn(
                                "user_active_level",
                                when(col("action_count").lt(50), "低活跃用户")
                                        .when(col("action_count").lt(200), "普通用户")
                                        .otherwise("高活跃用户")
                        );
        userActiveLevel.show(10);

        //统计各活跃度等级用户数
        userActiveLevel.groupBy("user_active_level")
                .count()
                .show();

        //用户购买次数统计
        Dataset<Row> userPurchaseCount =
                buyData
                        .groupBy("user_id")
                        .count()
                        .withColumnRenamed(
                                "count",
                                "purchase_count"
                        )
                        .orderBy(
                                col("purchase_count").desc()
                        );
        userPurchaseCount.show(10);

        //保留无购买次数用户
        Dataset<Row> allUserPurchaseCount =
                df.select("user_id")
                        .distinct()
                        .join(userPurchaseCount, "user_id", "left");
        allUserPurchaseCount.show(10);

        //用户平均购买次数
        allUserPurchaseCount
                .select(
                        avg("purchase_count")
                ).withColumnRenamed("avg(purchase_count)",
                        "avg_purchase_count"
                )
                .show();

        //用户购买次数分级
        Dataset<Row> userPurchaseSegment =
                allUserPurchaseCount
                        .withColumn(
                                "user_buy_level",
                                when(col("purchase_count").isNull(), "暂无购买用户")
                                        .when(col("purchase_count").leq(1), "一次购买用户")
                                        .when(col("purchase_count").leq(5), "普通购买用户")
                                        .otherwise("高频购买用户")
                        );
        userPurchaseSegment.show(10);

        //统计各购买力等级用户数
        userPurchaseSegment.groupBy("user_buy_level")
                .count()
                .withColumnRenamed("count", "user_count")
                .orderBy(col("user_count").desc())
                .show(10);

        //用户购买力频次分布
        Dataset<Row> purchaseFrequency =
                allUserPurchaseCount
                        .withColumn(
                                "purchase_count",
                                when(col("purchase_count").isNull(), 0)
                                        .otherwise(col("purchase_count"))
                        )
                        .groupBy("purchase_count")
                        .count()
                        .orderBy("purchase_count");
        purchaseFrequency.show(10);

        //用户购买商品种类统计
        Dataset<Row> userPurchaseItemCount =
                buyData
                        .groupBy("user_id")
                        .agg(
                                countDistinct("item_id")
                        )
                        .withColumnRenamed(
                                "count(DISTINCT item_id)",
                                "purchase_item_count"
                        );
        userPurchaseItemCount.show(10);

        //用户购买商品类别统计
        Dataset<Row> userPurchaseCategoryCount =
                buyData
                        .groupBy("user_id")
                        .agg(
                                countDistinct("category_id")
                        )
                        .withColumnRenamed(
                                "count(DISTINCT category_id)",
                                "purchase_category_count"
                        );
        userPurchaseCategoryCount.show(10);

        //获取最新购买日期
        long maxPurchaseTimestamp =
                buyData
                        .select(
                                max("timestamp")
                        )
                        .first()
                        .getLong(0);

        //统计用户最后购买日期
        Dataset<Row> userLastPurchaseTime =
                buyData
                        .groupBy("user_id")
                        .max("timestamp")
                        .withColumnRenamed(
                                "max(timestamp)",
                                "last_purchase_timestamp"
                        )
                        .withColumn(
                                "last_purchase_time",
                                from_unixtime(
                                        col("last_purchase_timestamp")
                                )
                        )
                        .withColumn(
                                "days_since_last_purchase",
                                datediff(
                                        from_unixtime(lit(maxPurchaseTimestamp)),
                                        from_unixtime(col("last_purchase_timestamp"))
                                )
                        );
        userLastPurchaseTime.show(10);

        //用户画像
        Dataset<Row> userProfile =
                userActionCount
                        .join(userPurchaseCount, "user_id", "left")
                        .join(userPurchaseItemCount, "user_id", "left")
                        .join(userPurchaseCategoryCount, "user_id", "left")
                        .join(userLastPurchaseTime, "user_id", "left")
                        .na().fill(0, new String[]{
                                "purchase_count",
                                "purchase_item_count",
                                "purchase_category_count"
                        })
                        .withColumn(
                                "has_purchase",
                                when(col("purchase_count").gt(0), "已购买用户")
                                        .otherwise("未购买用户")
                        );
        userProfile.show(10);

        //AFR指数
        Dataset<Row> userValueProfile =
                userProfile.select(
                                "user_id",
                                "purchase_count",
                                "purchase_item_count",
                                "purchase_category_count",
                                "days_since_last_purchase"
                        )
                        .withColumn(
                                "purchase_range",
                                col("purchase_item_count")
                                        .plus(col("purchase_category_count"))
                        )
                        .withColumn(
                                "A_score",
                                when(col("purchase_range").geq(20),5)
                                        .when(col("purchase_range").geq(10),4)
                                        .when(col("purchase_range").geq(5),3)
                                        .when(col("purchase_range").gt(0),2)
                                        .otherwise(0)
                        )
                        .withColumn(
                                "F_score",
                                when(col("purchase_count").geq(20),5)
                                        .when(col("purchase_count").geq(10),4)
                                        .when(col("purchase_count").geq(5),3)
                                        .when(col("purchase_count").gt(0),2)
                                        .otherwise(0)
                        )
                        .withColumn(
                                "R_score",
                                when(col("days_since_last_purchase").isNull(),0)
                                        .when(col("days_since_last_purchase").leq(3),5)
                                        .when(col("days_since_last_purchase").leq(7),4)
                                        .when(col("days_since_last_purchase").leq(30),3)
                                        .when(col("days_since_last_purchase").leq(90),2)
                                        .otherwise(1)
                        )
                        .withColumn(
                                "value_score",
                                col("A_score")
                                        .plus(col("F_score"))
                                        .plus(col("R_score"))
                        )
                        .withColumn(
                                "user_value_level",
                                when(col("value_score").equalTo(0),"未购买用户")
                                        .when(col("value_score").leq(6),"低价值用户")
                                        .when(col("value_score").leq(11),"普通价值用户")
                                        .otherwise("高价值用户")
                        );
        userValueProfile.show(10);

        userValueProfile
                .groupBy("A_score")
                .count()
                .show();
        userValueProfile
                .groupBy("F_score")
                .count()
                .show();
        userValueProfile
                .groupBy("R_score")
                .count()
                .show();
        userValueProfile
                .groupBy("value_score")
                .count()
                .orderBy("value_score")
                .show();
        userValueProfile
                .groupBy("user_value_level")
                .count()
                .show();

        System.out.println(
                "用户画像数量:"
                        + userProfile.count()
        );

        spark.stop();
    }
}
