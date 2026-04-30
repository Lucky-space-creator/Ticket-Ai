@echo off
chcp 65001 >nul
title Ticket System - Microservice Starter
echo ================================================
echo   Ticket Intelligent Customer Service System
echo ================================================
echo.
echo Please ensure the following are installed:
echo   1. Java 17+
echo   2. Maven 3.8+
echo   3. MySQL 8.0+ (localhost:3306)
echo   4. Redis (localhost:6379)
echo   5. RocketMQ (optional, localhost:9876)
echo   6. Ollama (optional, localhost:11434)
echo.
echo Press any key to start all services...
pause >nul
echo.

echo [1/9] Compiling project...
call mvn clean compile -DskipTests
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)
echo Compile success!
echo.

:: Fix admin-service port conflict (8084 -> 8086)
echo [2/9] Adjusting admin-service port...
set "ADMIN_YML=admin-service\src\main\resources\application.yml"
if exist "%ADMIN_YML%" (
    rem Create backup if not exists
    if not exist "%ADMIN_YML%.bak" (
        copy "%ADMIN_YML%" "%ADMIN_YML%.bak" >nul
        echo Backup created: application.yml.bak
    )
    rem Force port to 8086 (avoid conflict with aichat-service:8084)
    powershell -Command "(Get-Content '%ADMIN_YML%') -replace 'port: \d+', 'port: 8086' | Set-Content '%ADMIN_YML%'"
    echo admin-service port set to 8086
) else (
    echo Warning: admin-service config file not found, skip.
)
echo.

echo [3/9] Starting Gateway Service (port: 8080)...
start "Ticket Gateway" cmd /k "cd ticket-gateway && mvn spring-boot:run"
timeout /t 8 >nul

echo [4/9] Starting User Service (port: 8081)...
start "User Service" cmd /k "cd user-service && mvn spring-boot:run"
timeout /t 5 >nul

echo [5/9] Starting Train Service (port: 8082)...
start "Train Service" cmd /k "cd train-service && mvn spring-boot:run"
timeout /t 5 >nul

echo [6/9] Starting Order Service (port: 8083)...
start "Order Service" cmd /k "cd order-service && mvn spring-boot:run"
timeout /t 5 >nul

echo [7/9] Starting AI Chat Service (port: 8084)...
start "AI Chat Service" cmd /k "cd aichat-service && mvn spring-boot:run"
timeout /t 5 >nul

echo [8/9] Starting Admin Service (port: 8086)...
start "Admin Service" cmd /k "cd admin-service && mvn spring-boot:run"
timeout /t 5 >nul

echo [9/9] Starting Customer Service (port: 8085)...
start "Customer Service" cmd /k "cd customer-service && mvn spring-boot:run"
timeout /t 5 >nul

echo.
echo ================================================
echo   All microservices have been launched!
echo ================================================
echo.
echo Service URLs:
echo   Gateway:      http://localhost:8080
echo   User:         http://localhost:8081
echo   Train:        http://localhost:8082
echo   Order:        http://localhost:8083
echo   AI Chat:      http://localhost:8084
echo   Admin:        http://localhost:8086/admin
echo   Customer:     http://localhost:8085/customer
echo.
echo Frontend (run separately):
echo   Vue frontend:   cd frontend ^&^& npm run dev  (port 3000)
echo   Admin frontend: cd admin    ^&^& npm run dev  (port 3001)
echo.
echo Monitor:
echo   Druid:        http://localhost:8080/druid (admin/admin)
echo.
echo Important notes:
echo   1. Each service takes 30-60 seconds to fully start.
echo   2. To stop a service, close its CMD window.
echo   3. To stop all, close all CMD windows.
echo.
echo Press any key to exit this launcher...
pause >nul