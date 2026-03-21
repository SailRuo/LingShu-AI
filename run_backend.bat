@echo off
title LingShu-AI Backend

echo [INFO] Moving to Backend directory...
cd /d %~dp0backend

echo [INFO] Compiling and Installing modules...
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %errorlevel%
)

echo [INFO] Moving to Web Module...
cd lingshu-web
echo [INFO] Starting Spring Boot from lingshu-web...
call mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"
if %errorlevel% neq 0 (
    echo [ERROR] Startup failed.
    pause
)
