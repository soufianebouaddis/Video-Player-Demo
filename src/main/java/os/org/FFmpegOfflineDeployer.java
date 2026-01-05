package os.org;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles offline FFmpeg deployment for firewalled corporate environments.
 * 
 * Two deployment modes:
 * 1. EMBEDDED: FFmpeg bundled in JAR (requires special build)
 * 2. LOCAL: FFmpeg pre-placed in deployment folder
 * 
 * Checks for FFmpeg in this order:
 * 1. Cached in ~/.videoplayer/ffmpeg/ffmpeg.exe
 * 2. Local ffmpeg.zip in application directory
 * 3. Local ffmpeg/ folder in application directory
 * 4. Embedded in resources (if bundled)
 */
public class FFmpegOfflineDeployer {
    private static final Path CACHE_DIR = Paths.get(
        System.getProperty("user.home"), 
        ".videoplayer", 
        "ffmpeg"
    );

    /**
     * Check for locally available FFmpeg (without internet)
     * Returns null if no local FFmpeg found
     */
    public static File findLocalFFmpeg() {
        System.out.println("[Offline] Checking for local FFmpeg...");

        // 1. Check cache first
        File cached = CACHE_DIR.resolve("ffmpeg.exe").toFile();
        if (cached.exists()) {
            System.out.println("[Offline] ✓ Found cached FFmpeg at: " + cached.getAbsolutePath());
            return cached;
        }

        // 2. Check local ffmpeg/ folder with bin/ subfolder
        File localFolder = new File("ffmpeg");
        if (localFolder.exists() && localFolder.isDirectory()) {
            // Check in bin/ subfolder first (standard FFmpeg structure)
            File ffmpegInBin = new File(localFolder, "bin/ffmpeg.exe");
            if (ffmpegInBin.exists()) {
                System.out.println("[Offline] ✓ Found local ffmpeg/bin/ffmpeg.exe");
                return ffmpegInBin;
            }
            // Check if ffmpeg.exe is directly in ffmpeg folder
            File ffmpegDirect = new File(localFolder, "ffmpeg.exe");
            if (ffmpegDirect.exists()) {
                System.out.println("[Offline] ✓ Found local ffmpeg/ffmpeg.exe");
                return ffmpegDirect;
            }
        }

        // 3. Check local ffmpeg.zip in current directory
        File localZip = new File("ffmpeg.zip");
        if (localZip.exists()) {
            System.out.println("[Offline] Found local ffmpeg.zip - extracting...");
            try {
                extractLocalZip(localZip.toPath());
                File result = CACHE_DIR.resolve("ffmpeg.exe").toFile();
                if (result.exists()) {
                    System.out.println("[Offline] ✓ Extracted FFmpeg successfully");
                    return result;
                }
            } catch (Exception e) {
                System.err.println("[Offline] Failed to extract ffmpeg.zip: " + e.getMessage());
            }
        }

        // 4. Check for embedded resource in JAR
        File embedded = extractEmbeddedFFmpeg();
        if (embedded != null) {
            System.out.println("[Offline] ✓ Deployed embedded FFmpeg");
            return embedded;
        }

        System.out.println("[Offline] ✗ No local FFmpeg found");
        return null;
    }

    /**
     * Extract local ffmpeg.zip file to cache directory
     */
    private static void extractLocalZip(Path zipFile) throws IOException {
        Files.createDirectories(CACHE_DIR);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = CACHE_DIR.resolve(entry.getName());

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

        // Find and copy ffmpeg.exe to main cache folder
        Files.walk(CACHE_DIR)
            .filter(p -> p.getFileName().toString().equals("ffmpeg.exe"))
            .findFirst()
            .ifPresent(source -> {
                try {
                    Files.copy(source, CACHE_DIR.resolve("ffmpeg.exe"), 
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("[Offline] Failed to copy ffmpeg.exe: " + e.getMessage());
                }
            });
    }


    /**
     * Extract FFmpeg from JAR resources (if bundled)
     * Would require special build process to include ffmpeg.zip in resources
     */
    private static File extractEmbeddedFFmpeg() {
        try {
            InputStream resource = FFmpegOfflineDeployer.class
                .getResourceAsStream("/ffmpeg.zip");
            
            if (resource == null) {
                return null; // Not bundled
            }

            System.out.println("[Offline] Extracting embedded FFmpeg...");
            Files.createDirectories(CACHE_DIR);

            Path tempZip = CACHE_DIR.resolve("embedded-ffmpeg.zip");
            try (OutputStream out = Files.newOutputStream(tempZip)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = resource.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            extractLocalZip(tempZip);
            Files.deleteIfExists(tempZip);

            File result = CACHE_DIR.resolve("ffmpeg.exe").toFile();
            return result.exists() ? result : null;

        } catch (Exception e) {
            System.err.println("[Offline] Failed to extract embedded FFmpeg: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get cache directory path
     */
    public static Path getCacheDir() {
        return CACHE_DIR;
    }

    public static void main(String[] args) {
        File ffmpeg = findLocalFFmpeg();
        if (ffmpeg != null) {
            System.out.println("FFmpeg found at: " + ffmpeg.getAbsolutePath());
        } else {
            System.out.println("No local FFmpeg found. Please provide ffmpeg.zip in application directory.");
        }
    }
}
