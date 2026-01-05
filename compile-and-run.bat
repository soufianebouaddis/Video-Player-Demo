@echo off
REM Video Player - Compile and Run
REM This script compiles and runs the video player without Maven

setlocal enabledelayedexpansion

set "projectRoot=%cd%"
set "srcDir=%projectRoot%\src\main\java"
set "outDir=%projectRoot%\target\classes"
set "mainClass=os.org.VideoPlayerApp"

if not exist "%outDir%" mkdir "%outDir%"

echo Video Player Build Script
echo.
echo Project Root: %projectRoot%
echo Source Dir: %srcDir%
echo Output Dir: %outDir%
echo.

REM Check if javac exists
where javac >nul 2>&1
if errorlevel 1 (
    echo ERROR: javac not found in PATH
    echo Please ensure Java Development Kit (JDK) is installed
    pause
    exit /b 1
)

echo Compiling Java files...
cd /d "%projectRoot%"

REM Compile all Java files
javac -d "%outDir%" -encoding UTF-8 -source 11 -target 11 ^
    "%srcDir%\os\org\*.java"

if errorlevel 1 (
    echo COMPILATION FAILED!
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo.
echo Starting Video Player...
echo.

REM Run the application
java -cp "%outDir%" %mainClass%


pause
