package os.org;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class VideoPlayerApp extends JFrame {
    private VideoPlayerController controller;
    private FFmpegVideoPlayer ffmpegPlayerPanel;
    private MediaControlBar controlBar;
    private final VideoPlayerModel model;

    public VideoPlayerApp() {
        model = new VideoPlayerModel();
        initUI();
    }

    private void initUI() {
        setTitle("Java Video Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 720);
        setLocationRelativeTo(null);

        // Create main layout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // FFmpeg Player Panel
        controlBar = new MediaControlBar();
        ffmpegPlayerPanel = new FFmpegVideoPlayer(controlBar, model);
        ffmpegPlayerPanel.setPreferredSize(new Dimension(1280, 600));

        mainPanel.add(ffmpegPlayerPanel, BorderLayout.CENTER);
        mainPanel.add(controlBar, BorderLayout.SOUTH);

        add(mainPanel);

        // Initialize controller
        controller = new VideoPlayerController(this, controlBar, ffmpegPlayerPanel, model);
        controlBar.setController(controller);

        // Menu bar
        createMenuBar();
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open Video");
        openItem.addActionListener(e -> controller.openVideoFile());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            ffmpegPlayerPanel.dispose();
            System.exit(0);
        });

        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // View menu
        JMenu viewMenu = new JMenu("View");
        JCheckBoxMenuItem fullScreenItem = new JCheckBoxMenuItem("Full Screen");
        fullScreenItem.addActionListener(e ->
                controller.toggleFullScreen(fullScreenItem.isSelected()));

        viewMenu.add(fullScreenItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);

        setJMenuBar(menuBar);
    }

    public FFmpegVideoPlayer getFFmpegPlayerPanel() {
        return ffmpegPlayerPanel;
    }

    public static void main(String[] args) {
        // Load native VLC libraries from bundled resources
        NativeLibraryLoader.loadNatives();
        
        try {
            SwingUtilities.invokeLater(() -> {
                VideoPlayerApp app = new VideoPlayerApp();
                app.setVisible(true);
            });
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("VLC")) {
                System.err.println("\nVLC Library not found!");
                System.err.println("Please install VLC media player from: https://www.videolan.org/vlc/");
            }
        }
    }
}
