package os.org;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class VideoPlayerController {
    private final VideoPlayerApp app;
    private final VideoPlayerModel model;
    private final IVideoPlayer videoPlayer;
    private final FullScreenHandler fullScreenHandler;

    public VideoPlayerController(VideoPlayerApp app, MediaControlBar controlBar, IVideoPlayer videoPlayer, VideoPlayerModel model) {
        this.app = app;
        this.videoPlayer = videoPlayer;
        this.model = model;
        this.fullScreenHandler = new FullScreenHandler(app);
    }

    public void openVideoFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".mp4") || name.endsWith(".avi") || 
                       name.endsWith(".mov") || name.endsWith(".mkv") ||
                       name.endsWith(".flv") || name.endsWith(".wmv") ||
                       name.endsWith(".webm") || name.endsWith(".m4v") ||
                       name.endsWith(".3gp") || name.endsWith(".ogv");
            }

            @Override
            public String getDescription() {
                return "Video Files (*.mp4, *.avi, *.mov, *.mkv, *.flv, *.wmv, *.webm, *.m4v, *.3gp, *.ogv)";
            }
        });

        int result = fileChooser.showOpenDialog(app);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            loadVideo(file);
        }
    }

    private void loadVideo(File file) {
        try {
            System.out.println("Loading video: " + file.getAbsolutePath());
            model.setCurrentFile(file);
            videoPlayer.loadVideo(file);
            videoPlayer.play();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(app,
                    "Error loading video: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
    }

    public void playVideo() {
        videoPlayer.play();
    }

    public void pauseVideo() {
        videoPlayer.pause();
    }

    public void togglePlayPause() {
        videoPlayer.togglePlayPause();
    }

    public void seekForward() {
        videoPlayer.seekForward();
    }

    public void seekBackward() {
        videoPlayer.seekBackward();
    }

    public void setVolume(double volume) {
        videoPlayer.setVolume((int) volume);
        model.setVolume(volume);
    }

    public void seekToPosition(int percentage) {
        SimpleDuration duration = model.getDuration();
        double totalMillis = duration.toMillis();
        long newMillis = (long) ((percentage / 100.0) * totalMillis);
        videoPlayer.seek(newMillis);
    }

    public void toggleFullScreen(boolean fullScreen) {
        fullScreenHandler.toggleFullScreen(fullScreen);
    }

    public void dispose() {
        videoPlayer.dispose();
    }
}
