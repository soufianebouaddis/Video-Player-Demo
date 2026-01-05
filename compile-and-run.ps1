# Video Player - Compile and Run Script
# This script compiles and runs the video player without Maven

$projectRoot = (Get-Location).Path
$srcDir = Join-Path $projectRoot "src\main\java"
$outDir = Join-Path $projectRoot "target\classes"
$mainClass = "os.org.VideoPlayerApp"

# Create output directory
if (!(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}

Write-Host "Video Player Build Script"
Write-Host ""
Write-Host "Project Root: $projectRoot"
Write-Host "Source Dir: $srcDir"
Write-Host "Output Dir: $outDir"
Write-Host ""

# Find Java compiler
$javac = Get-Command javac -ErrorAction SilentlyContinue
if (!$javac) {
    Write-Host "ERROR: javac not found in PATH"
    Write-Host "Please ensure Java Development Kit (JDK) is installed and in PATH"
    exit 1
}

Write-Host "Using Java: $(javac -version 2>&1)"
Write-Host ""

# Compile all Java files
Write-Host "Compiling Java files..."
$javaFiles = Get-ChildItem -Path "$srcDir\os\org" -Filter "*.java" -Recurse

if ($javaFiles.Count -eq 0) {
    Write-Host "ERROR: No Java files found!"
    exit 1
}

Write-Host "Found $($javaFiles.Count) Java files to compile"
Write-Host ""

# Compile with javac
$compileCmd = @(
    "javac",
    "-d", $outDir,
    "-encoding", "UTF-8",
    "-source", "11",
    "-target", "11"
)

foreach ($file in $javaFiles) {
    $compileCmd += $file.FullName
}

Write-Host "Compiling..."
& $compileCmd 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "COMPILATION FAILED!"
    exit 1
}

Write-Host "Compilation successful!"
Write-Host ""

# Run the application
Write-Host "Starting Video Player..."
Write-Host ""

$java = Get-Command java -ErrorAction SilentlyContinue
if (!$java) {
    Write-Host "ERROR: java not found in PATH"
    exit 1
}

# Run with proper classpath
$env:CLASSPATH = $outDir
& java -cp $outDir $mainClass

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Application exited with error code: $LASTEXITCODE"
}
