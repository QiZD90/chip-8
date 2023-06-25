package ml.qizd.chip8;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainApplication {
    private static class Frame extends java.awt.Frame {
        public class Canvas extends java.awt.Canvas {
            @Override
            public void update(Graphics g) {
                paint(g);
            }

            @Override
            public void paint(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                Chip8.FrameBuffer frameBuffer = chip8.getFrameBuffer();

                for (int i = 0; i < frameBuffer.HEIGHT; i++) {
                    for (int j = 0; j < frameBuffer.WIDTH; j++) {
                        g2d.setColor(frameBuffer.get(j, i) ? Color.WHITE : Color.BLACK);
                        g2d.fillRect(j * TILE_SIZE, i * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    }
                }
            }

            public Canvas() {
                this.setSize(640, 320);
            }
        }
        private static final int TILE_SIZE = 10;
        Chip8 chip8;
        InputManager inputManager;
        Canvas canvas = new Canvas();

        private void showErrorDialog(String error) {
            Dialog dialog = new Dialog(this, "Error", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    dialog.setVisible(false);
                }
            });

            dialog.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                        dialog.setVisible(false);

                    super.keyPressed(e);
                }
            });
            dialog.setSize(300, 100);
            dialog.add(new Label(error));
            dialog.setVisible(true);
        }

        private Frame(Chip8 chip8, InputManager inputManager) {
            this.chip8 = chip8;
            this.inputManager = inputManager;
            this.setTitle("CHIP-8");
            this.setSize(660, 380);

            MenuBar menuBar = new MenuBar();
            Menu menu = new Menu("CHIP-8");

            MenuItem load = new MenuItem("Load ROM", new MenuShortcut(KeyEvent.VK_L));
            load.addActionListener((a) -> {
                FileDialog fileDialog = new FileDialog(this, "Select ROM");
                fileDialog.setVisible(true);

                if (fileDialog.getFile() == null)
                    return;

                Path path = Path.of(fileDialog.getDirectory(), fileDialog.getFile());
                try (InputStream stream = Files.newInputStream(path)) {
                    this.chip8.load(stream);
                } catch (Exception e) {
                    showErrorDialog("Failed to open file " + path);
                }
            });
            menu.add(load);

            MenuItem reset = new MenuItem("Reset CHIP-8", new MenuShortcut(KeyEvent.VK_R));
            reset.addActionListener((a) -> {
                this.chip8.reset();
            });
            menu.add(reset);

            menuBar.add(menu);
            this.setMenuBar(menuBar);

            canvas.addKeyListener(inputManager);
            this.add(canvas);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });

            setVisible(true);
        }
    }

    public static void main(String[] args) {
        InputManager inputManager = new InputManager();
        Chip8 chip8 = new Chip8(inputManager);
        Frame frame = new Frame(chip8, inputManager);

        // CPU thread
        new Thread(() -> {
            long lastTime = System.nanoTime();
            long delta = 0;
            while (true) {
                long time = System.nanoTime();
                delta += time - lastTime;
                lastTime = time;

                if (delta >= 2000000) {
                    chip8.tick();
                    delta -= 2000000;
                }
            }
        }).start();

        // Graphics thread
        new Thread(() -> {
            while (true) {
                if (chip8.getFrameBuffer().isDirty()) {
                    frame.canvas.repaint();
                    chip8.getFrameBuffer().redraw();
                }

                try {
                    Thread.sleep(1000 / 60);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

        // Timer thread
        new Thread(() -> {
            while (true) {
                chip8.tickTimer();
                try {
                    Thread.sleep(1000 / 60);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

    }
}
