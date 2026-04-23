@echo off
echo Starting Discovery Server...
start "Discovery Server" cmd /k "cd /d C:\projects\plus-smart-home-tech\plus-smart-home-tech && java -jar infra\discovery-server\target\discovery-server-1.0-SNAPSHOT.jar"

timeout /t 3

echo Starting Config Server...
start "Config Server" cmd /k "cd /d C:\projects\plus-smart-home-tech\plus-smart-home-tech && java -jar infra\config-server\target\config-server-1.0-SNAPSHOT.jar"

timeout /t 5

echo Starting Warehouse...
start "Warehouse" cmd /k "cd /d C:\projects\plus-smart-home-tech\plus-smart-home-tech && java -jar commerce\warehouse\target\warehouse-1.0-SNAPSHOT.jar"

timeout /t 3

echo Starting Shopping Store...
start "Shopping Store" cmd /k "cd /d C:\projects\plus-smart-home-tech\plus-smart-home-tech && java -jar commerce\shopping-store\target\shopping-store-1.0-SNAPSHOT.jar"

timeout /t 3

echo Starting Shopping Cart...
start "Shopping Cart" cmd /k "cd /d C:\projects\plus-smart-home-tech\plus-smart-home-tech && java -jar commerce\shopping-cart\target\shopping-cart-1.0-SNAPSHOT.jar"

echo All services started!