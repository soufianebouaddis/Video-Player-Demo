package os.org;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and manages FFmpeg portable executable
 * Completely self-contained - no system dependencies
 */
public class FFmpegDownloader {
    private static final String FFMPEG_DOWNLOAD_URL = 
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2024-01-02-12-58/ffmpeg-N-126354-gad22e83e6c-win64-gpl.zip";
    
    private static final Path APP_DATA = Paths.get(
        System.getProperty("user.home"), 
        ".videoplayer", 
        "ffmpeg"
    );

    public static File getFFmpegBinary() throws Exception {
        // Check if already downloaded
        File ffmpegExe = APP_DATA.resolve("ffmpeg.exe").toFile();
        
        if (ffmpegExe.exists()) {
            System.out.println("[FFmpeg] Found cached: " + ffmpegExe.getAbsolutePath());
            return ffmpegExe;
        }

        // Try offline deployment first (for firewalled environments)
        File offlineFFmpeg = FFmpegOfflineDeployer.findLocalFFmpeg();
        if (offlineFFmpeg != null) {
            return offlineFFmpeg;
        }

        // Download if needed
        System.out.println("[FFmpeg] Downloading portable FFmpeg...");
        System.out.println("[FFmpeg] This happens only once (~200MB)");
        try {
            downloadFFmpeg();
        } catch (Exception e) {
            System.err.println("[FFmpeg] Download failed: " + e.getMessage());
            System.err.println("[FFmpeg] Your company firewall is blocking external downloads.");
            System.err.println("[FFmpeg] Offline deployment options:");
            System.err.println("[FFmpeg]   1. Place ffmpeg.zip in application directory");
            System.err.println("[FFmpeg]   2. Place ffmpeg/ folder in application directory");
            System.err.println("[FFmpeg]   3. Download separately and extract to: " + APP_DATA.toAbsolutePath());
            throw e;
        }

        if (ffmpegExe.exists()) {
            System.out.println("[FFmpeg] Downloaded successfully");
            return ffmpegExe;
        }

        throw new Exception("Failed to obtain FFmpeg binary");
    }

    private static void downloadFFmpeg() throws Exception {
        // Create app data directory
        Files.createDirectories(APP_DATA);

        System.out.println("[FFmpeg] Connecting to download server...");
        URL url = new URL(FFMPEG_DOWNLOAD_URL);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        long contentLength = conn.getContentLengthLong();
        System.out.println("[FFmpeg] Download size: " + formatSize(contentLength));

        Path zipFile = APP_DATA.resolve("ffmpeg.zip");

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(zipFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long bytesDownloaded = 0;
            long lastUpdate = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesDownloaded += bytesRead;

                // Progress every 5 seconds
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 5000) {
                    double percent = (bytesDownloaded * 100.0) / contentLength;
                    System.out.printf("[FFmpeg] Downloaded: %.1f%% (%s / %s)%n",
                        percent,
                        formatSize(bytesDownloaded),
                        formatSize(contentLength));
                    lastUpdate = now;
                }
            }
        }

        System.out.println("[FFmpeg] Extracting...");
        extractZip(zipFile, APP_DATA);

        // Find and copy ffmpeg.exe to main folder
        Files.walk(APP_DATA)
            .filter(p -> p.getFileName().toString().equals("ffmpeg.exe"))
            .findFirst()
            .ifPresent(source -> {
                try {
                    Files.copy(source, APP_DATA.resolve("ffmpeg.exe"), 
                        StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[FFmpeg] Extracted successfully");
                } catch (IOException e) {
                    System.err.println("Failed to copy ffmpeg.exe: " + e.getMessage());
                }
            });

        // Clean up zip
        Files.deleteIfExists(zipFile);
    }

    private static void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (OutputStream os = Files.newOutputStream(targetPath)) {
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static void main(String[] args) {
        try {
            File ffmpeg = getFFmpegBinary();
            System.out.println("FFmpeg ready at: " + ffmpeg.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
