package os.org;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class FullScreenHandler {
    private JFrame frame;
    private GraphicsDevice device;
    private Window fullScreenWindow;
    private boolean isFullScreen = false;
    private Rectangle normalBounds;

    public FullScreenHandler(JFrame frame) {
        this.frame = frame;
        this.device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
    }

    public void toggleFullScreen(boolean enable) {
        if (enable && !isFullScreen) {
            enterFullScreen();
        } else if (!enable && isFullScreen) {
            exitFullScreen();
        }
    }

    private void enterFullScreen() {
        if (device.isFullScreenSupported()) {
            normalBounds = frame.getBounds();

            frame.dispose();
            frame.setUndecorated(true);
            device.setFullScreenWindow(frame);

            frame.setVisible(true);
            isFullScreen = true;

            // Add ESC key listener to exit fullscreen
            frame.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        exitFullScreen();
                    }
                }
            });
        }
    }

    private void exitFullScreen() {
        if (isFullScreen) {
            device.setFullScreenWindow(null);
            frame.dispose();
            frame.setUndecorated(false);

            if (normalBounds != null) {
                frame.setBounds(normalBounds);
            }

            frame.setVisible(true);
            isFullScreen = false;
        }
    }

    public boolean isFullScreen() {
        return isFullScreen;
    }
}
