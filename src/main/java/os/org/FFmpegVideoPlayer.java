package os.org;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Synchronized video player using FFmpeg with proper A/V sync
 */
public class FFmpegVideoPlayer extends JPanel implements IVideoPlayer {
    private Process ffmpegProcess;
    private Process audioProcess;
    private SourceDataLine audioLine;
    private final MediaControlBar controlBar;
    private final VideoPlayerModel model;
    private BufferedImage currentFrame;
    private final ForkJoinPool frameProcessingPool = new ForkJoinPool(2);
    private boolean isPlaying = false;
    private long durationMs = 0;
    private long currentTimeMs = 0;
    private File ffmpegBinary;
    private File ffprobeBinary;
    private volatile boolean stopPlayback = false;
    private String currentVideoPath;
    private Thread decodeThread;
    private Thread audioThread;
    private int videoWidth = 1280;
    private int videoHeight = 720;
    private float audioVolume = 1.0f;
    private double actualFrameRate = 30.0; // Actual video frame rate
    
    // Synchronization variables
    private volatile long playbackStartTime = 0; // System time when playback started
    private volatile long startPositionMs = 0;   // Video position where playback started
    private AtomicLong pendingSeekTimeMs = new AtomicLong(-1);
    private final Object seekLock = new Object();
    private volatile boolean isUpdatingUI = false;

    public FFmpegVideoPlayer(MediaControlBar controlBar, VideoPlayerModel model) {
        this.controlBar = controlBar;
        this.model = model;
        setBackground(Color.BLACK);
        extractFFmpeg();
    }

    private void extractFFmpeg() {
        try {
            ffmpegBinary = FFmpegDownloader.getFFmpegBinary();
            System.out.println("[FFmpeg] Ready: " + ffmpegBinary.getAbsolutePath());
            
            if (ffmpegBinary != null && ffmpegBinary.exists()) {
                String ffmpegDir = ffmpegBinary.getParent();
                ffprobeBinary = new File(ffmpegDir, "ffprobe.exe");
            }
        } catch (Exception e) {
            System.err.println("[FFmpeg] Error: " + e.getMessage());
            JOptionPane.showMessageDialog(null,
                "Failed to initialize FFmpeg: " + e.getMessage(),
                "FFmpeg Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void loadVideo(File file) {
        try {
            currentVideoPath = file.getAbsolutePath();
            System.out.println("[FFmpeg] Loading: " + currentVideoPath);
            
            getVideoInfo();
            
            controlBar.setVideoLoaded(true);
        } catch (Exception e) {
            System.err.println("[FFmpeg] Load error: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error loading video: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Get video duration, dimensions, and frame rate using ffprobe
     */
    private void getVideoInfo() throws Exception {
        if (ffprobeBinary == null || !ffprobeBinary.exists()) {
            System.err.println("[FFmpeg] ffprobe not found");
            return;
        }
        
        // Get duration
        ProcessBuilder pb = new ProcessBuilder(
            ffprobeBinary.getAbsolutePath(),
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            currentVideoPath
        );
        
        Process process = pb.start();
        InputStream is = process.getInputStream();
        byte[] data = is.readAllBytes();
        String output = new String(data).trim();
        
        try {
            durationMs = (long) (Double.parseDouble(output) * 1000);
            System.out.println("[FFmpeg] Duration: " + (durationMs / 1000) + "s");
            model.setDuration(new SimpleDuration(durationMs));
            controlBar.setDuration(new SimpleDuration(durationMs));
        } catch (NumberFormatException e) {
            System.err.println("[FFmpeg] Could not parse duration: " + output);
        }
        
        // Get video dimensions and frame rate
        try {
            pb = new ProcessBuilder(
                ffprobeBinary.getAbsolutePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height,r_frame_rate",
                currentVideoPath
            );
            process = pb.start();
            is = process.getInputStream();
            data = is.readAllBytes();
            String dimOutput = new String(data).trim();
            System.out.println("[FFmpeg] Stream info:\n" + dimOutput);
            
            String[] lines = dimOutput.split("\n");
            for (String line : lines) {
                if (line.startsWith("width=")) {
                    videoWidth = Integer.parseInt(line.substring(6).trim());
                } else if (line.startsWith("height=")) {
                    videoHeight = Integer.parseInt(line.substring(7).trim());
                } else if (line.startsWith("r_frame_rate=")) {
                    String fpsStr = line.substring(13).trim();
                    // Parse fraction like "30/1" or "24000/1001"
                    if (fpsStr.contains("/")) {
                        String[] parts = fpsStr.split("/");
                        double num = Double.parseDouble(parts[0]);
                        double den = Double.parseDouble(parts[1]);
                        actualFrameRate = num / den;
                    } else {
                        actualFrameRate = Double.parseDouble(fpsStr);
                    }
                    System.out.println("[FFmpeg] Frame rate: " + String.format("%.2f", actualFrameRate) + " fps");
                }
            }
            System.out.println("[FFmpeg] Resolution: " + videoWidth + "x" + videoHeight);
        } catch (Exception e) {
            System.err.println("[FFmpeg] Could not get video info: " + e.getMessage());
            System.out.println("[FFmpeg] Using defaults: 1280x720 @ 30fps");
        }
    }

    @Override
    public void play() {
        if (!isPlaying) {
            isPlaying = true;
            stopPlayback = false;
            controlBar.setPlaying(true);
            startPlayback();
        }
    }

    /**
     * Start synchronized playback using system clock as master
     */
    private void startPlayback() {
        // Stop any existing playback
        stopPlayback = true;
        
        if (ffmpegProcess != null) {
            ffmpegProcess.destroyForcibly();
        }
        if (audioProcess != null) {
            audioProcess.destroyForcibly();
        }
        
        waitForThreads();
        
        stopPlayback = false;
        
        // Set the master clock - use system time as reference
        playbackStartTime = System.nanoTime();
        startPositionMs = currentTimeMs;
        
        System.out.println("[FFmpeg] Starting synchronized playback at " + (currentTimeMs / 1000) + "s");
        
        // Start audio first (audio is typically the master clock)
        startAudioPlayback();
        
        // Small delay to ensure audio starts first
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Start video decode thread
        startVideoPlayback();
    }
    
    private void waitForThreads() {
        if (decodeThread != null && decodeThread.isAlive()) {
            try {
                decodeThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (audioThread != null && audioThread.isAlive()) {
            try {
                audioThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Start video playback with proper synchronization to master clock
     */
    private void startVideoPlayback() {
        decodeThread = new Thread(() -> {
            try {
                // Calculate frame duration in nanoseconds
                long frameDurationNs = (long) ((1.0 / actualFrameRate) * 1_000_000_000);
                
                while (isPlaying && !stopPlayback) {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(ffmpegBinary.getAbsolutePath());
                    
                    if (currentTimeMs > 0) {
                        cmd.add("-ss");
                        cmd.add(String.valueOf(currentTimeMs / 1000.0));
                    }
                    
                    cmd.add("-i");
                    cmd.add(currentVideoPath);
                    cmd.add("-f");
                    cmd.add("rawvideo");
                    cmd.add("-pix_fmt");
                    cmd.add("rgb24");
                    cmd.add("-vf");
                    cmd.add("scale=" + videoWidth + ":" + videoHeight);
                    // Use actual frame rate instead of hardcoded 30
                    cmd.add("-r");
                    cmd.add(String.valueOf(actualFrameRate));
                    cmd.add("-an");
                    cmd.add("-");
                    
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    ffmpegProcess = pb.start();
                    System.out.println("[Video] Process started at " + (currentTimeMs / 1000) + "s");
                    
                    InputStream in = ffmpegProcess.getInputStream();
                    int frameSize = videoWidth * videoHeight * 3;
                    byte[] frameData = new byte[frameSize];
                    int frameCount = 0;
                    boolean seekDetected = false;
                    
                    while (isPlaying && !stopPlayback && !seekDetected && ffmpegProcess.isAlive()) {
                        // Check for seek
                        long pendingSeek = pendingSeekTimeMs.getAndSet(-1);
                        if (pendingSeek >= 0 && pendingSeek != currentTimeMs) {
                            System.out.println("[Video] Seek: " + (currentTimeMs / 1000) + "s -> " + (pendingSeek / 1000) + "s");
                            currentTimeMs = pendingSeek;
                            seekDetected = true;
                            
                            if (ffmpegProcess != null) {
                                ffmpegProcess.destroyForcibly();
                            }
                            
                            // Restart audio at new position
                            restartAudio();
                            
                            // Reset master clock
                            playbackStartTime = System.nanoTime();
                            startPositionMs = currentTimeMs;
                            
                            break;
                        }
                        
                        // Calculate expected time based on master clock
                        long elapsedNs = System.nanoTime() - playbackStartTime;
                        long expectedTimeMs = startPositionMs + (elapsedNs / 1_000_000);
                        
                        // Read frame
                        int totalRead = 0;
                        while (totalRead < frameSize && !stopPlayback && !seekDetected) {
                            int nRead = in.read(frameData, totalRead, frameSize - totalRead);
                            if (nRead == -1) {
                                System.out.println("[Video] EOF reached");
                                stopPlayback = true;
                                isPlaying = false;
                                controlBar.setPlaying(false);
                                return;
                            }
                            totalRead += nRead;
                        }
                        
                        if (totalRead < frameSize) {
                            break;
                        }
                        
                        frameCount++;
                        
                        // Update current time from master clock
                        currentTimeMs = expectedTimeMs;
                        
                        // Calculate when this frame should be displayed
                        long frameTimeMs = startPositionMs + (frameCount * 1000 / (long)actualFrameRate);
                        long displayTimeNs = playbackStartTime + (frameTimeMs - startPositionMs) * 1_000_000;
                        long currentNs = System.nanoTime();
                        long waitNs = displayTimeNs - currentNs;
                        
                        // If we're behind, skip sleeping
                        if (waitNs > 0) {
                            try {
                                Thread.sleep(waitNs / 1_000_000, (int)(waitNs % 1_000_000));
                            } catch (InterruptedException e) {
                                break;
                            }
                        } else if (waitNs < -frameDurationNs * 2) {
                            // If we're more than 2 frames behind, drop this frame
                            System.out.println("[Video] Dropping frame " + frameCount + " (late by " + (-waitNs / 1_000_000) + "ms)");
                            continue;
                        }
                        
                        final byte[] frameCopy = frameData.clone();
                        
                        // Render frame
                        frameProcessingPool.execute(() -> {
                            try {
                                BufferedImage frame = new BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_INT_RGB);
                                int[] pixels = new int[videoWidth * videoHeight];
                                
                                for (int i = 0; i < frameCopy.length; i += 3) {
                                    int r = frameCopy[i] & 0xFF;
                                    int g = frameCopy[i + 1] & 0xFF;
                                    int b = frameCopy[i + 2] & 0xFF;
                                    pixels[i / 3] = (r << 16) | (g << 8) | b;
                                }
                                
                                frame.setRGB(0, 0, videoWidth, videoHeight, pixels, 0, videoWidth);
                                currentFrame = frame;
                                SwingUtilities.invokeLater(() -> repaint());
                            } catch (Exception e) {
                                // Ignore
                            }
                        });
                        
                        if (frameCount % 30 == 0) {
                            long drift = expectedTimeMs - frameTimeMs;
                            System.out.println("[Video] Frame " + frameCount + " @ " + (expectedTimeMs / 1000) + "s (drift: " + drift + "ms)");
                        }
                    }
                    
                    if (!seekDetected) {
                        break;
                    }
                }
                
                System.out.println("[Video] Playback ended");
                if (ffmpegProcess != null) {
                    ffmpegProcess.destroy();
                }
            } catch (Exception e) {
                System.err.println("[Video] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        decodeThread.setName("FFmpeg-Video");
        decodeThread.start();
    }

    /**
     * Start audio playback
     */
    private void startAudioPlayback() {
        audioThread = new Thread(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(ffmpegBinary.getAbsolutePath());
                
                if (currentTimeMs > 0) {
                    cmd.add("-ss");
                    cmd.add(String.valueOf(currentTimeMs / 1000.0));
                }
                
                cmd.add("-analyzeduration");
                cmd.add("0");
                cmd.add("-probesize");
                cmd.add("32");
                cmd.add("-i");
                cmd.add(currentVideoPath);
                cmd.add("-f");
                cmd.add("s16le");
                cmd.add("-acodec");
                cmd.add("pcm_s16le");
                cmd.add("-ar");
                cmd.add("44100");
                cmd.add("-ac");
                cmd.add("2");
                cmd.add("-");
                
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                audioProcess = pb.start();
                
                AudioFormat audioFormat = new AudioFormat(44100, 16, 2, true, false);
                audioLine = AudioSystem.getSourceDataLine(audioFormat);
                audioLine.open(audioFormat);
                audioLine.start();
                
                System.out.println("[Audio] Started at " + (currentTimeMs / 1000) + "s");
                
                InputStream audioIn = audioProcess.getInputStream();
                byte[] audioBuffer = new byte[4096];
                int bytesRead;
                
                while (!stopPlayback && (bytesRead = audioIn.read(audioBuffer)) != -1) {
                    if (audioVolume < 1.0f) {
                        applyVolume(audioBuffer, bytesRead);
                    }
                    audioLine.write(audioBuffer, 0, bytesRead);
                }
                
                System.out.println("[Audio] Ended");
                if (audioLine != null && audioLine.isOpen()) {
                    audioLine.drain();
                    audioLine.stop();
                    audioLine.close();
                }
                
            } catch (Exception e) {
                System.err.println("[Audio] Error: " + e.getMessage());
            }
        });
        audioThread.setName("FFmpeg-Audio");
        audioThread.start();
    }
    
    private void restartAudio() {
        if (audioThread != null && audioThread.isAlive()) {
            if (audioProcess != null) {
                audioProcess.destroyForcibly();
            }
            try {
                audioThread.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        startAudioPlayback();
    }
    
    private void applyVolume(byte[] buffer, int length) {
        for (int i = 0; i < length; i += 2) {
            int sample = ((buffer[i + 1] & 0xFF) << 8) | (buffer[i] & 0xFF);
            short shortSample = (short) sample;
            shortSample = (short) (shortSample * audioVolume);
            buffer[i] = (byte) (shortSample & 0xFF);
            buffer[i + 1] = (byte) ((shortSample >> 8) & 0xFF);
        }
    }

    @Override
    public void pause() {
        if (isPlaying) {
            isPlaying = false;
            controlBar.setPlaying(false);
            stopPlayback = true;
            
            if (ffmpegProcess != null) {
                ffmpegProcess.destroyForcibly();
            }
            if (audioProcess != null) {
                audioProcess.destroyForcibly();
            }
            
            System.out.println("[FFmpeg] Paused at " + (currentTimeMs / 1000) + "s");
        }
    }

    @Override
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }

    @Override
    public void seekForward() {
        seek(currentTimeMs + 10000);
    }

    @Override
    public void seekBackward() {
        seek(Math.max(0, currentTimeMs - 10000));
    }

    @Override
    public void seek(long timeMs) {
        long newTimeMs = Math.min(timeMs, durationMs);
        System.out.println("[FFmpeg] Seek to " + (newTimeMs / 1000) + "s");
        
        synchronized (seekLock) {
            pendingSeekTimeMs.set(newTimeMs);
        }
    }

    @Override
    public void setVolume(int volume) {
        audioVolume = Math.max(0.0f, Math.min(1.0f, volume / 100.0f));
        System.out.println("[Audio] Volume: " + volume + "%");
        
        if (audioLine != null && audioLine.isOpen()) {
            try {
                if (audioLine.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                    javax.sound.sampled.FloatControl gainControl = 
                        (javax.sound.sampled.FloatControl) audioLine.getControl(
                            javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                    
                    float dB = audioVolume > 0 ? (float) (20 * Math.log10(audioVolume)) : -80;
                    dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                    gainControl.setValue(dB);
                }
            } catch (Exception e) {
                System.err.println("[Audio] Could not set system volume: " + e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        isPlaying = false;
        stopPlayback = true;
        controlBar.setPlaying(false);
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
        }
        if (audioProcess != null) {
            audioProcess.destroy();
        }
        if (audioLine != null && audioLine.isOpen()) {
            try {
                audioLine.stop();
                audioLine.close();
            } catch (Exception e) {
                System.err.println("[Audio] Error closing: " + e.getMessage());
            }
        }
    }

    @Override
    public void dispose() {
        stop();
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }    
    
    @Override
    public boolean isUpdatingUI() {
        return isUpdatingUI;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (currentFrame != null) {
            g.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), this);
            
            isUpdatingUI = true;
            try {
                controlBar.updateTime(
                    new SimpleDuration(currentTimeMs),
                    new SimpleDuration(durationMs)
                );
            } finally {
                isUpdatingUI = false;
            }
        } else {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            if (isPlaying) {
                g.drawString("Loading video...", getWidth() / 2 - 60, getHeight() / 2);
            } else {
                g.drawString("No video loaded", getWidth() / 2 - 60, getHeight() / 2);
            }
        }
    }
}