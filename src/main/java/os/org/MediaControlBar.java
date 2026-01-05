package os.org;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class MediaControlBar extends JPanel {
    private VideoPlayerController controller;

    private JButton playPauseBtn;
    private JButton forwardBtn;
    private JButton backwardBtn;
    private JSlider timeSlider;
    private JSlider volumeSlider;
    private JButton fullScreenBtn;
    private JLabel timeLabel;

    private boolean isPlaying = false;
    private Timer timeUpdateTimer;

    public MediaControlBar() {
        initComponents();
        setupTimer();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(800, 60));
        setBackground(new Color(40, 40, 40));

        // Left panel for playback controls
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        leftPanel.setBackground(new Color(40, 40, 40));

        // Play/Pause button
        playPauseBtn = new JButton("Play");
        playPauseBtn.setPreferredSize(new Dimension(80, 30));
        playPauseBtn.setToolTipText("Play/Pause (Space)");
        playPauseBtn.addActionListener(e -> togglePlayPause());

        // Backward button
        backwardBtn = new JButton("<< 10s");
        backwardBtn.setPreferredSize(new Dimension(80, 30));
        backwardBtn.setToolTipText("Back 10 seconds");
        backwardBtn.addActionListener(e -> seekBackward());

        // Forward button
        forwardBtn = new JButton("10s >>");
        forwardBtn.setPreferredSize(new Dimension(80, 30));
        forwardBtn.setToolTipText("Forward 10 seconds");
        forwardBtn.addActionListener(e -> seekForward());

        leftPanel.add(backwardBtn);
        leftPanel.add(playPauseBtn);
        leftPanel.add(forwardBtn);

        // Center panel for time slider
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBackground(new Color(40, 40, 40));

        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        timeSlider = new JSlider(0, 100, 0);
        timeSlider.setBackground(new Color(40, 40, 40));
        timeSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!timeSlider.getValueIsAdjusting() && controller != null) {
                    seekToPosition(timeSlider.getValue());
                }
            }
        });

        centerPanel.add(timeSlider, BorderLayout.CENTER);
        centerPanel.add(timeLabel, BorderLayout.SOUTH);

        // Right panel for volume and fullscreen
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        rightPanel.setBackground(new Color(40, 40, 40));

        // Volume slider
        volumeSlider = new JSlider(0, 100, 20);
        volumeSlider.setPreferredSize(new Dimension(80, 20));
        volumeSlider.setBackground(new Color(40, 40, 40));
        volumeSlider.addChangeListener(e -> {
            if (controller != null) {
                controller.setVolume(volumeSlider.getValue());
            }
        });

        // Fullscreen button
        fullScreenBtn = new JButton("Fullscreen");
        fullScreenBtn.setPreferredSize(new Dimension(100, 30));
        fullScreenBtn.setToolTipText("Toggle Full Screen (F)");
        fullScreenBtn.addActionListener(e -> toggleFullScreen());

        rightPanel.add(new JLabel("Volume:"));
        rightPanel.add(volumeSlider);
        rightPanel.add(fullScreenBtn);

        // Add all panels
        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // Set initial state
        setVideoLoaded(false);

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
    }

    private void setupTimer() {
        timeUpdateTimer = new Timer(100, e -> {
            if (controller != null && isPlaying) {
                updateTimeDisplay();
            }
        });
    }

    private void setupKeyboardShortcuts() {
        // Space for play/pause
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("SPACE"), "playPause");
        getActionMap().put("playPause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePlayPause();
            }
        });

        // F for fullscreen
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("F"), "fullScreen");
        getActionMap().put("fullScreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleFullScreen();
            }
        });

        // Left/Right arrows for seeking
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("LEFT"), "seekBack");
        getActionMap().put("seekBack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                seekBackward();
            }
        });

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("RIGHT"), "seekForward");
        getActionMap().put("seekForward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                seekForward();
            }
        });
    }

    public void setController(VideoPlayerController controller) {
        this.controller = controller;
    }

    public void setVideoLoaded(boolean loaded) {
        playPauseBtn.setEnabled(loaded);
        forwardBtn.setEnabled(loaded);
        backwardBtn.setEnabled(loaded);
        timeSlider.setEnabled(loaded);
        volumeSlider.setEnabled(loaded);

        if (!loaded) {
            timeLabel.setText("00:00 / 00:00");
            timeSlider.setValue(0);
        }
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        playPauseBtn.setText(playing ? "⏸" : "▶");

        if (playing) {
            timeUpdateTimer.start();
        } else {
            timeUpdateTimer.stop();
        }
    }

    public void updateTime(SimpleDuration currentTime, SimpleDuration duration) {
        if (!timeSlider.getValueIsAdjusting()) {
            int progress = (int) (currentTime.toMillis() / (double)duration.toMillis() * 100);
            timeSlider.setValue(progress);
        }

        updateTimeLabel(currentTime, duration);
    }

    public void setDuration(SimpleDuration duration) {
        updateTimeLabel(new SimpleDuration(0), duration);
    }

    private void updateTimeLabel(SimpleDuration currentTime, SimpleDuration duration) {
        String current = formatTime(currentTime);
        String total = formatTime(duration);
        timeLabel.setText(current + " / " + total);
    }

    private String formatTime(SimpleDuration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) (duration.toSeconds() % 60);
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateTimeDisplay() {
        // Time display is updated through JavaFX callbacks
    }

    private void togglePlayPause() {
        if (controller != null) {
            controller.togglePlayPause();
        }
    }

    private void seekForward() {
        if (controller != null) {
            controller.seekForward();
        }
    }

    private void seekBackward() {
        if (controller != null) {
            controller.seekBackward();
        }
    }

    private void seekToPosition(int percentage) {
        if (controller != null) {
            controller.seekToPosition(percentage);
        }
    }

    private void toggleFullScreen() {
        if (controller != null) {
            controller.toggleFullScreen(!isFullScreen);
            isFullScreen = !isFullScreen;
        }
    }

    private boolean isFullScreen = false;

    public void updateVolumeSlider(double volume) {
        volumeSlider.setValue((int) (volume * 100));
    }
}
