package os.org;

/**
 * Prepares FFmpeg for the application
 * Downloads portable FFmpeg on first run if needed
 */
public class NativeLibraryLoader {

    public static void loadNatives() {
        try {
            // Ensure FFmpeg is available (downloads if needed)
            FFmpegDownloader.getFFmpegBinary();
            System.out.println("[App] FFmpeg is ready");
        } catch (Exception e) {
            System.err.println("[App] Warning: FFmpeg setup incomplete: " + e.getMessage());
            // Continue anyway - will show error when trying to play
        }
    }
}
