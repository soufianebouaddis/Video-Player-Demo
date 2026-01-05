package os.org;

/**
 * Simple duration representation in milliseconds.
 * Used across video player implementations for time tracking.
 */
public class SimpleDuration {
    private final long milliseconds;

    public SimpleDuration(long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public long getMilliseconds() {
        return milliseconds;
    }

    public long toMillis() {
        return milliseconds;
    }

    public long toSeconds() {
        return milliseconds / 1000;
    }

    public long toMinutes() {
        return milliseconds / (1000 * 60);
    }

    @Override
    public String toString() {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
