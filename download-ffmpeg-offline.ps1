# Download and prepare FFmpeg for offline deployment
# This script downloads FFmpeg and prepares it for use with the video player

param(
    [string]$OutputDir = ".",
    [switch]$ExtractFolder
)

# FFmpeg Build Details
$FFmpegUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2024-01-02-12-58/ffmpeg-N-126354-gad22e83e6c-win64-gpl.zip"
$ZipFile = Join-Path $OutputDir "ffmpeg.zip"
$ExtractDir = Join-Path $OutputDir "ffmpeg"

Write-Host "FFmpeg Offline Preparation"
Write-Host ""

Write-Host "Configuration:"
Write-Host "  Output Directory: $OutputDir"
Write-Host "  ZIP File: $ZipFile"
if ($ExtractFolder) {
    Write-Host "  Extract Folder: $ExtractDir"
}
Write-Host ""

# Step 1: Download
Write-Host "Step 1: Downloading FFmpeg portable..."
Write-Host "  Source: FFmpeg-Builds (GitHub)"
Write-Host "  File: ffmpeg-N-126354-gad22e83e6c-win64-gpl.zip"
Write-Host "  Size: Approx 200 MB"
Write-Host "  Duration: 2-10 minutes depending on connection speed"
Write-Host ""

try {
    $ProgressPreference = 'SilentlyContinue'
    
    Write-Host "  Downloading..." -NoNewline
    $startTime = Get-Date
    
    Invoke-WebRequest -Uri $FFmpegUrl -OutFile $ZipFile -TimeoutSec 3600
    
    $endTime = Get-Date
    $duration = $endTime - $startTime
    $fileSize = (Get-Item $ZipFile).Length / 1MB
    
    Write-Host " Done"
    Write-Host "  File Size: $([Math]::Round($fileSize, 2)) MB"
    Write-Host "  Time: $([Math]::Round($duration.TotalSeconds)) seconds"
    Write-Host ""
} catch {
    Write-Host " Error"
    Write-Host "ERROR: Failed to download FFmpeg" 
    Write-Host "Message: $_"
    Write-Host ""
    Write-Host "Troubleshooting:"
    Write-Host "  1. Verify you have internet access"
    Write-Host "  2. Verify the URL is still valid"
    Write-Host "  3. Download manually from:"
    Write-Host "     https://github.com/BtbN/FFmpeg-Builds/releases"
    Write-Host "     Look for: ffmpeg-*-win64-gpl.zip"
    exit 1
}

# Step 2: Extract (if requested)
if ($ExtractFolder) {
    Write-Host "Step 2: Extracting FFmpeg..."
    Write-Host ""
    
    try {
        Write-Host "  Extracting..." -NoNewline
        
        if (Test-Path $ExtractDir) {
            Remove-Item $ExtractDir -Recurse -Force
        }
        
        [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipFile, $OutputDir)
        
        # Move nested folder to top level if it exists
        $nested = Get-ChildItem $OutputDir -Directory | Where-Object { 
            (Get-ChildItem $_.FullName).Count -gt 0 -and 
            (Test-Path (Join-Path $_.FullName "bin\ffmpeg.exe"))
        }
        
        if ($nested) {
            Get-ChildItem $nested[0].FullName | Move-Item -Destination $OutputDir -Force
            Remove-Item $nested[0].FullName -Force
        }
        
        Rename-Item (Get-ChildItem $OutputDir -Directory | 
                    Where-Object { (Test-Path (Join-Path $_.FullName "bin\ffmpeg.exe")) } | 
                    Select-Object -First 1).FullName $ExtractDir -ErrorAction SilentlyContinue
        
        Write-Host " Done"
        Write-Host "  Extracted to: $ExtractDir"
        Write-Host "  Size: $([Math]::Round((Get-ChildItem $ExtractDir -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB, 2)) MB"
        Write-Host ""
    } catch {
        Write-Host " Error"
        Write-Host "ERROR: Failed to extract FFmpeg"
        Write-Host "Message: $_"
        Write-Host ""
        Write-Host "Note: ZIP file is still available at: $ZipFile"
        Write-Host "You can manually extract it if needed"
    }
}

# Step 3: Verify
Write-Host "Step 3: Verification"
Write-Host ""

$fileExists = Test-Path $ZipFile
$folderExists = Test-Path $ExtractDir
$ffmpegInFolder = if ($folderExists) { Test-Path (Join-Path $ExtractDir "bin\ffmpeg.exe") } else { $false }

if ($fileExists) {
    Write-Host "ffmpeg.zip available for deployment"
} else {
    Write-Host "ffmpeg.zip not found"
}

if ($folderExists -and $ffmpegInFolder) {
    Write-Host "ffmpeg/ folder ready for deployment"
} elseif ($folderExists) {
    Write-Host "ffmpeg/ folder exists but ffmpeg.exe not found"
}

Write-Host ""
Write-Host "Done! FFmpeg is ready for use."
