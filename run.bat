@echo off
REM Video Player - Run Script
REM This script runs the video player application

setlocal enabledelayedexpansion

echo.
echo Video Player
echo.

REM Check if Java exists
where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: java not found in PATH
    echo Please ensure Java 11 or higher is installed
    pause
    exit /b 1
)

REM Try to run with Maven
where mvn >nul 2>&1
if !errorlevel! equ 0 (
    echo Starting with Maven...
    mvn exec:java -Dexec.mainClass="os.org.VideoPlayerApp" -q
    goto :end
)

REM Fallback to JAR if Maven is not available
if exist "target\VideoPlayer.jar" (
    echo Starting with compiled JAR...
    java -jar target\VideoPlayer.jar
    goto :end
)

REM Fallback to classes directory
if exist "target\classes" (
    echo Starting application...
    java -cp target\classes os.org.VideoPlayerApp
    goto :end
)

REM Build and run if neither exists
if exist "pom.xml" (
    echo Building project with Maven...
    mvn clean package -q
    if !errorlevel! equ 0 (
        echo Starting application...
        java -jar target\VideoPlayer.jar
    ) else (
        echo Build failed. Please check the error messages above.
        pause
    )
    goto :end
)

echo.
echo ERROR: Could not find project files or compiled JAR
echo Please run: mvn clean package
echo Then: java -jar target\VideoPlayer.jar
pause

:end
exit /b %errorlevel%

