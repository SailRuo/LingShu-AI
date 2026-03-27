@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LingShu AI Docker Build Script
echo ========================================
echo.

REM Get script directory
set "SCRIPT_DIR=%~dp0"

REM Step 1: Clean old builds
echo [1/7] Cleaning old builds...
cd /d "%SCRIPT_DIR%backend"
call mvn clean -q
if %errorlevel% neq 0 (
    echo ERROR: Maven clean failed
    pause
    exit /b 1
)
echo OK: Clean completed
echo.

REM Step 2: Build frontend
echo [2/7] Building frontend...
cd /d "%SCRIPT_DIR%frontend"
call npm run build
if %errorlevel% neq 0 (
    echo ERROR: Frontend build failed
    pause
    exit /b 1
)
echo OK: Frontend build completed
echo.

REM Step 3: Copy frontend to backend static
echo [3/7] Copying frontend to backend static...
set "STATIC_DIR=%SCRIPT_DIR%backend\lingshu-web\src\main\resources\static"
if exist "%STATIC_DIR%" (
    rmdir /s /q "%STATIC_DIR%"
)
mkdir "%STATIC_DIR%"
xcopy /E /I /Y "%SCRIPT_DIR%frontend\dist\*" "%STATIC_DIR%"
echo OK: Frontend copied to static
echo.

REM Step 4: Build backend JAR
echo [4/7] Building backend JAR...
cd /d "%SCRIPT_DIR%backend"
call mvn clean package -DskipTests -pl lingshu-web -am
if %errorlevel% neq 0 (
    echo ERROR: Backend build failed
    pause
    exit /b 1
)
echo OK: Backend JAR completed
echo.

REM Step 5: Stop old containers
echo [5/7] Stopping old containers...
cd /d "%SCRIPT_DIR%"
docker-compose down
echo OK: Old containers stopped
echo.

REM Step 6: Build Docker image
echo [6/7] Building Docker image...
docker build --no-cache -t lingshu-ai:latest .
if %errorlevel% neq 0 (
    echo ERROR: Docker build failed
    pause
    exit /b 1
)
echo OK: Docker image built
echo.

REM Step 7: Start containers
echo [7/7] Starting Docker containers...
docker-compose up -d
if %errorlevel% neq 0 (
    echo ERROR: Docker compose failed
    pause
    exit /b 1
)
echo OK: Containers started
echo.

echo ========================================
echo    Build completed successfully!
echo ========================================
echo.
echo View logs:
echo   docker-compose logs -f app
echo.
echo Access:
echo   http://localhost:8080
echo.


