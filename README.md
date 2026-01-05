# Video Player

A Java-based video player application using FFmpeg for video decoding and Java's built-in audio system for playback.

## Features

- Video playback with 30 FPS rendering
- Audio synchronization (44100Hz, 16-bit, Stereo)
- Forward/backward seek with ±10 seconds buttons
- Timeline slider for quick navigation
- Volume control
- Play/Pause functionality
- Full-screen mode support

## Requirements

- Java 11 or higher
- FFmpeg (downloaded automatically on first run)
- Windows, macOS, or Linux

## Getting Started

### Option 1: Run directly with batch/PowerShell scripts

Windows:
```
compile-and-run.bat
```

PowerShell:
```powershell
.\compile-and-run.ps1
```

### Option 2: Build with Maven

```
mvn clean package
java -jar target/VideoPlayer.jar
```

### Option 3: Compile and run manually

```
javac -d target/classes -encoding UTF-8 src/main/java/os/org/*.java
java -cp target/classes os.org.VideoPlayerApp
```

## Project Structure

```
VideoPlayer/
├── src/
│   ├── main/java/os/org/
│   │   ├── VideoPlayerApp.java          # Main application entry point
│   │   ├── VideoPlayerController.java   # UI controller
│   │   ├── FFmpegVideoPlayer.java       # Video decoding and playback
│   │   ├── MediaControlBar.java         # Playback controls UI
│   │   ├── VideoPlayerModel.java        # Data model
│   │   ├── FullScreenHandler.java       # Full-screen functionality
│   │   ├── FFmpegDownloader.java        # FFmpeg management
│   │   └── SimpleDuration.java          # Time utility
│   └── resources/
├── pom.xml                              # Maven configuration
├── compile-and-run.bat                  # Windows batch script
├── compile-and-run.ps1                  # PowerShell script
├── run.bat                              # Run script
└── download-ffmpeg-offline.ps1         # FFmpeg download helper

```

## Key Components

### FFmpegVideoPlayer
- Handles video decoding using FFmpeg
- Manages playback threads
- Implements frame rendering
- Supports seeking with minimal latency

### MediaControlBar
- Provides playback controls (Play, Pause, Forward, Backward)
- Time slider for seeking
- Volume control slider
- Time display

### FFmpegDownloader
- Automatically downloads FFmpeg on first run
- Supports offline deployment
- Caches FFmpeg for fast startup

## Controls

- Space: Play/Pause
- Left Arrow: Seek backward 10 seconds
- Right Arrow: Seek forward 10 seconds
- F: Toggle full-screen
- Volume Slider: Control audio level
- Time Slider: Navigate through video

## Building

### With Maven
```
mvn clean compile
mvn exec:java -Dexec.mainClass="os.org.VideoPlayerApp"
```

### Without Maven
```
javac -d target/classes -encoding UTF-8 -source 11 -target 11 src/main/java/os/org/*.java
java -cp target/classes os.org.VideoPlayerApp
```

## Performance Optimizations

- FFmpeg initialization flags: -analyzeduration 0 -probesize 32
- Queue-based seek system to prevent overlapping operations
- Self-restarting decode thread for smooth seeking
- 3ms frame processing loop for responsive UI
- Async frame conversion using ForkJoinPool

## Troubleshooting

### FFmpeg not found
- FFmpeg downloads automatically on first run
- Manual download: Run `download-ffmpeg-offline.ps1`
- Or download from: https://github.com/BtbN/FFmpeg-Builds

### Java not found
- Ensure Java 11+ is installed
- Verify Java is in your PATH

### Video not loading
- Check file format compatibility
- Verify FFmpeg is properly installed
- Try a different video file

## License

This project demonstrates video playback using FFmpeg and Java Swing.

## Notes

- FFmpeg is automatically downloaded and cached
- First run may take longer due to FFmpeg setup
- Subsequent runs use cached FFmpeg for instant startup
