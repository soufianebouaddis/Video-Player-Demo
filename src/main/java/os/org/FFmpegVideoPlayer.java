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
 * Self-contained video player using FFmpeg executable.
 * Decodes video frames and renders them in real-time.
 * Audio is played via Java's built-in audio system.
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
    
    // Seek queue system - holds the next seek target
    private AtomicLong pendingSeekTimeMs = new AtomicLong(-1);
    private final Object seekLock = new Object();

    public FFmpegVideoPlayer(MediaControlBar controlBar, VideoPlayerModel model) {
        this.controlBar = controlBar;
        this.model = model;
        setBackground(Color.BLACK);
        // Don't use setLayout(null) - let it use default FlowLayout or BorderLayout
        extractFFmpeg();
    }

    /**
     * Extract bundled FFmpeg executable from resources
     */
    private void extractFFmpeg() {
        try {
            ffmpegBinary = FFmpegDownloader.getFFmpegBinary();
            System.out.println("[FFmpeg] Ready: " + ffmpegBinary.getAbsolutePath());
            
            // Get ffprobe path
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
            
            // Get duration using ffprobe
            getDuration();
            
            controlBar.setVideoLoaded(true);
        } catch (Exception e) {
            System.err.println("[FFmpeg] Load error: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error loading video: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Get video duration and dimensions using ffprobe
     */
    private void getDuration() throws Exception {
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
        
        // Get video dimensions
        try {
            pb = new ProcessBuilder(
                ffprobeBinary.getAbsolutePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                currentVideoPath
            );
            process = pb.start();
            is = process.getInputStream();
            data = is.readAllBytes();
            String dimOutput = new String(data).trim();
            System.out.println("[FFmpeg] Dimension output:\n" + dimOutput);
            
            // Parse width and height from output like:
            // [STREAM]
            // width=1280
            // height=698
            // [/STREAM]
            String[] lines = dimOutput.split("\n");
            for (String line : lines) {
                if (line.startsWith("width=")) {
                    videoWidth = Integer.parseInt(line.substring(6).trim());
                } else if (line.startsWith("height=")) {
                    videoHeight = Integer.parseInt(line.substring(7).trim());
                }
            }
            System.out.println("[FFmpeg] Detected Resolution: " + videoWidth + "x" + videoHeight);
        } catch (Exception e) {
            System.err.println("[FFmpeg] Could not get dimensions: " + e.getMessage());
            System.out.println("[FFmpeg] Using default: 1280x720");
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
     * Start FFmpeg playback and frame rendering
     * FFmpeg handles all timing, Java only renders frames
     */
    /**
     * Start FFmpeg playback and frame rendering
     * FFmpeg handles all timing and processing
     * Java only renders frames to screen
     */
    private void startPlayback() {
        // Stop any existing playback
        stopPlayback = true;
        
        // Kill old processes immediately
        if (ffmpegProcess != null) {
            ffmpegProcess.destroyForcibly();
        }
        if (audioProcess != null) {
            audioProcess.destroyForcibly();
        }
        
        // Wait for old threads to die
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
        
        // Reset flag for new playback
        stopPlayback = false;
        System.out.println("[FFmpeg] Starting playback at " + (currentTimeMs / 1000) + "s");
        
        // Start audio thread
        startAudioPlayback();
        
        // Start decode thread
        decodeThread = new Thread(() -> {
            try {
                // Outer loop - restarts when seeking
                while (isPlaying && !stopPlayback) {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(ffmpegBinary.getAbsolutePath());
                    
                    // Seek to position if needed
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
                    cmd.add("-r");
                    cmd.add("30");
                    cmd.add("-an");
                    cmd.add("-");
                    
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    ffmpegProcess = pb.start();
                    System.out.println("[FFmpeg] Process started at " + (currentTimeMs / 1000) + "s");
                    
                    InputStream in = ffmpegProcess.getInputStream();
                    int frameSize = videoWidth * videoHeight * 3;
                    byte[] frameData = new byte[frameSize];
                    int frameCount = 0;
                    boolean seekDetected = false;
                    
                    // Inner loop - read frames until seek or stop
                    while (isPlaying && !stopPlayback && !seekDetected && in.available() >= 0 && ffmpegProcess.isAlive()) {
                        // Check for pending seek request
                        long pendingSeek = pendingSeekTimeMs.getAndSet(-1);
                        if (pendingSeek >= 0 && pendingSeek != currentTimeMs) {
                            System.out.println("[FFmpeg] Seek detected: " + (currentTimeMs / 1000) + "s -> " + (pendingSeek / 1000) + "s");
                            currentTimeMs = pendingSeek;
                            seekDetected = true;
                            
                            // Kill current process
                            if (ffmpegProcess != null) {
                                ffmpegProcess.destroyForcibly();
                            }
                            // Restart audio at new position
                            if (audioThread != null && audioThread.isAlive()) {
                                if (audioProcess != null) {
                                    audioProcess.destroyForcibly();
                                }
                                try {
                                    audioThread.join(100);  // Reduced from 300ms for faster seeking
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            startAudioPlayback();
                            break;  // Break inner loop to restart outer loop with new position
                        }
                        
                        int totalRead = 0;
                        while (totalRead < frameSize && !stopPlayback && !seekDetected) {
                            int nRead = in.read(frameData, totalRead, frameSize - totalRead);
                            if (nRead == -1) {
                                System.out.println("[FFmpeg] EOF reached");
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
                        final byte[] frameCopy = frameData.clone();
                        
                        // Render frame asynchronously
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
                        
                        // Update time
                        currentTimeMs = currentTimeMs + 33;
                        
                        if (frameCount % 30 == 0) {
                            System.out.println("[FFmpeg] Frame " + frameCount + " @ " + (currentTimeMs / 1000) + "s");
                        }
                        
                        try {
                            Thread.sleep(3);  // Reduced from 10ms for faster responsiveness
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    
                    // If not seeking, we're done
                    if (!seekDetected) {
                        break;
                    }
                }
                
                System.out.println("[FFmpeg] Playback ended");
                if (ffmpegProcess != null) {
                    ffmpegProcess.destroy();
                }
            } catch (Exception e) {
                System.err.println("[FFmpeg] Error: " + e.getMessage());
            }
        });
        decodeThread.setName("FFmpeg-Decode");
        decodeThread.start();
    }

    /**
     * Start audio playback using FFmpeg and Java's audio system
     */
    private void startAudioPlayback() {
        // Stop old audio thread if running
        if (audioThread != null && audioThread.isAlive()) {
            if (audioProcess != null) {
                audioProcess.destroyForcibly();
            }
            try {
                audioThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        audioThread = new Thread(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(ffmpegBinary.getAbsolutePath());
                
                if (currentTimeMs > 0) {
                    cmd.add("-ss");
                    cmd.add(String.valueOf(currentTimeMs / 1000.0));
                }
                
                // Faster FFmpeg initialization for quicker seeks
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
                
                System.out.println("[Audio] Playing: 44100Hz, 16-bit, Stereo");
                
                InputStream audioIn = audioProcess.getInputStream();
                byte[] audioBuffer = new byte[4096];
                int bytesRead;
                
                while (!stopPlayback && (bytesRead = audioIn.read(audioBuffer)) != -1) {
                    if (audioVolume < 1.0f) {
                        applyVolume(audioBuffer, bytesRead);
                    }
                    audioLine.write(audioBuffer, 0, bytesRead);
                }
                
                System.out.println("[Audio] Playback ended");
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
    
    /**
     * Apply volume to audio buffer
     */
    private void applyVolume(byte[] buffer, int length) {
        for (int i = 0; i < length; i += 2) {
            // Convert bytes to short (16-bit signed)
            int sample = ((buffer[i + 1] & 0xFF) << 8) | (buffer[i] & 0xFF);
            short shortSample = (short) sample;
            
            // Apply volume
            shortSample = (short) (shortSample * audioVolume);
            
            // Convert back to bytes
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
            
            // Force terminate processes immediately
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
        System.out.println("[FFmpeg] Seek requested to " + (newTimeMs / 1000) + "s");
        
        // Queue up the seek - don't interrupt current playback
        synchronized (seekLock) {
            pendingSeekTimeMs.set(newTimeMs);
        }
        
        // UI will be updated by decode thread after successful seek
    }

    @Override
    public void setVolume(int volume) {
        // volume is 0-100
        audioVolume = Math.max(0.0f, Math.min(1.0f, volume / 100.0f));
        System.out.println("[Audio] Volume: " + volume + "% (" + String.format("%.2f", audioVolume) + ")");
        
        // Also update audio line if available
        if (audioLine != null && audioLine.isOpen()) {
            try {
                if (audioLine.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                    javax.sound.sampled.FloatControl gainControl = 
                        (javax.sound.sampled.FloatControl) audioLine.getControl(
                            javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                    
                    // Convert volume (0-1) to dB (-80 to 0)
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
                System.err.println("[Audio] Error closing audio line: " + e.getMessage());
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
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Debug: print once when we have a frame
        if (currentFrame != null) {
            g.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), this);
            
            // Update time display
            controlBar.updateTime(
                new SimpleDuration(currentTimeMs),
                new SimpleDuration(durationMs)
            );
        } else {
            // Show loading message
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            if (isPlaying) {
                g.drawString("Decoding video... No frames yet", 20, getHeight() / 2);
            } else {
                g.drawString("No video loaded", getWidth() / 2 - 80, getHeight() / 2);
            }
        }
    }
}
