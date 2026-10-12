# Third-party notices

This repository includes or directly depends on third-party software. The list below covers the direct dependencies declared by this project and the browser asset committed to the repository; transitive Maven dependencies remain governed by their own license terms.

| Component | Version | Use | License |
|---|---:|---|---|
| Apache Spark Core / SQL / Hive / MLlib | 3.5.0 | Distributed data processing | Apache License 2.0 |
| Jackson Databind | 2.15.2 | JSON serialization | Apache License 2.0 |
| MySQL Connector/J | 8.3.0 | Optional MySQL metadata repository | GPLv2 with the Universal FOSS Exception |
| JUnit Jupiter | 5.10.2 | Tests | Eclipse Public License 2.0 |
| Apache ECharts | 5.5.0 | Dashboard charts; bundled as `echarts.min.js` | Apache License 2.0 |

The bundled ECharts file retains its upstream Apache license header. Maven resolves the remaining libraries from their publishers; this repository does not vendor their binary artifacts.

This notice is informational and is not a substitute for reviewing the complete license texts before redistributing a public build.
