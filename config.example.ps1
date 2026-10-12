# Copy the values you need into your current PowerShell session.
# Do not put real credentials in this tracked example file.

$env:PLATFORM_REPOSITORY = 'memory' # change to 'mysql' for persistent metadata
$env:PLATFORM_JDBC_URL = 'jdbc:mysql://127.0.0.1:3306/ecommerce_v3'
$env:PLATFORM_JDBC_USER = 'app_user'
$env:PLATFORM_JDBC_PASSWORD = ''

# Optional JVM properties are passed on the Maven command line, for example:
# -Dspark.data.path=D:\data\UserBehavior.csv
# -Dplatform.runtime.dir=D:\spark-ecommerce-runtime
