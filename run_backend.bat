@echo off
title LingShu-AI Backend
echo [INFO] Moving to Web Module...
cd /d %~dp0backend\lingshu-web
echo [INFO] Starting Spring Boot from lingshu-web...
call mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"
if %errorlevel% neq 0 (
    echo [ERROR] Startup failed.
    pause
)
