import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
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
                        if (frameBuffer.get(j, i)) {
                            g2d.fillRect(j * TILE_SIZE, i * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                        } else {
                            g2d.clearRect(j * TILE_SIZE, i * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                        }
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

        private Frame(Chip8 chip8, InputManager inputManager) {
            this.chip8 = chip8;
            this.inputManager = inputManager;

            this.setTitle("CHIP-8");
            this.setSize(640, 360);
            this.add(canvas);
            canvas.addKeyListener(inputManager);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });

            setVisible(true);
        }
    }

    public static void main(String[] args) throws IOException {
        InputManager inputManager = new InputManager();
        Chip8 chip8 = new Chip8(inputManager);
        Frame frame = new Frame(chip8, inputManager);
        chip8.load(Files.newInputStream(Path.of("F:/chip8/keypad.ch8")));

        // CPU thread
        new Thread(() -> {
            while (chip8.tick()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
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
