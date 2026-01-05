package os.org;

import java.io.File;

public class VideoPlayerModel {
    private File currentFile;
    private SimpleDuration currentTime;
    private SimpleDuration duration;
    private double volume = 0.8;
    private boolean isFullScreen = false;

    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(File currentFile) {
        this.currentFile = currentFile;
    }

    public SimpleDuration getCurrentTime() {
        return currentTime != null ? currentTime : new SimpleDuration(0);
    }

    public void setCurrentTime(SimpleDuration currentTime) {
        this.currentTime = currentTime;
    }

    public SimpleDuration getDuration() {
        return duration != null ? duration : new SimpleDuration(0);
    }

    public void setDuration(SimpleDuration duration) {
        this.duration = duration;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    public boolean isFullScreen() {
        return isFullScreen;
    }

    public void setFullScreen(boolean fullScreen) {
        isFullScreen = fullScreen;
    }
}
