@echo off
title LingShu-AI Backend

chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8

echo [INFO] Moving to Backend directory...
cd /d %~dp0backend

echo [INFO] Setting JVM memory limits to prevent OOM...
set MAVEN_OPTS=-Xmx1024m -Xms512m

echo [INFO] Cleaning up potential zombie Java processes...
taskkill /F /IM java.exe /T >nul 2>&1

if "%1"=="clean" (
    echo [INFO] Clean build requested, compiling and installing all modules...
    call mvn clean install -DskipTests
    if %errorlevel% neq 0 (
        echo [ERROR] Compilation failed.
        pause
        exit /b %errorlevel%
    )
) else (
    echo [INFO] Skipping build, running directly
)

echo [INFO] Moving to Web Module...
cd lingshu-web
echo [INFO] Starting Spring Boot from lingshu-web...
call mvn spring-boot:run
if %errorlevel% neq 0 (
    echo [ERROR] Startup failed.
    pause
)
