package os.org;

import java.io.File;

/**
 * Common interface for video players
 * Allows switching between different backend implementations
 */
public interface IVideoPlayer {
    void loadVideo(File file);
    void play();
    void pause();
    void togglePlayPause();
    void seekForward();
    void seekBackward();
    void seek(long timeMs);
    void setVolume(int volume);
    void stop();
    void dispose();
    boolean isPlaying();
}
