@echo off
chcp 65001 >nul
title 购票智能客服系统 - 微服务停止器
echo ================================================
echo   购票智能客服系统 - 微服务停止器
echo ================================================
echo.
echo 警告: 这将尝试停止所有正在运行的微服务!
echo.
echo 按任意键继续，或按Ctrl+C取消...
pause >nul
echo.

echo [1/3] 查找并停止Java进程...
taskkill /F /FI "WINDOWTITLE eq Ticket Gateway" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq User Service" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq Train Service" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq Order Service" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq AI Chat Service" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq Admin Service" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq Customer Service" >nul 2>&1

echo [2/3] 停止Spring Boot应用进程...
for %%i in (ticket-gateway user-service train-service order-service aichat-service admin-service customer-service) do (
    taskkill /F /IM "java.exe" /FI "WINDOWTITLE eq *%%i*" >nul 2>&1
)

echo [3/3] 恢复admin-service原始配置...
if exist admin-service\src\main\resources\application.yml.bak (
    copy admin-service\src\main\resources\application.yml.bak admin-service\src\main\resources\application.yml >nul
    del admin-service\src\main\resources\application.yml.bak >nul
    echo admin-service配置已恢复
)

echo.
echo ================================================
echo   所有微服务已停止!
echo ================================================
echo.
echo 提示:
echo   1. 可能需要手动关闭残留的CMD窗口
echo   2. 服务可能需要几秒钟才能完全停止
echo.
echo 按任意键退出...
pause >nul