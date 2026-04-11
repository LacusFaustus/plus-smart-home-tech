@echo off
echo Cleaning project...
call mvn clean

echo Removing Flyway cache...
if exist "%USERPROFILE%\.m2\repository\org\flywaydb" (
    rmdir /s /q "%USERPROFILE%\.m2\repository\org\flywaydb"
)

echo Building project with force update...
call mvn clean install -U -DskipTests

if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b %errorlevel%
)

echo Build successful!
echo.
echo Starting Docker containers...
docker-compose up -d

echo Waiting for containers to start...
timeout /t 10

echo.
echo Starting Collector...
start "Collector" cmd /c "cd telemetry/collector && mvn spring-boot:run"

timeout /t 5

echo Starting Aggregator...
start "Aggregator" cmd /c "cd telemetry/aggregator && mvn spring-boot:run"

timeout /t 5

echo Starting Analyzer...
start "Analyzer" cmd /c "cd telemetry/analyzer && mvn spring-boot:run"

echo.
echo All services started!
echo Check logs in respective console windows
pause