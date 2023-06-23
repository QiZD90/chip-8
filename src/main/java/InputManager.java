import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class InputManager implements KeyListener {
    private final boolean[] keys = new boolean[16];

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyChar()) {
            case '1' -> this.keys[1] = true;
            case '2' -> this.keys[2] = true;
            case '3' -> this.keys[3] = true;
            case '4' -> this.keys[0xc] = true;

            case 'q' -> this.keys[4] = true;
            case 'w' -> this.keys[5] = true;
            case 'e' -> this.keys[6] = true;
            case 'r' -> this.keys[0xd] = true;

            case 'a' -> this.keys[7] = true;
            case 's' -> this.keys[8] = true;
            case 'd' -> this.keys[9] = true;
            case 'f' -> this.keys[0xe] = true;

            case 'z' -> this.keys[0xa] = true;
            case 'x' -> this.keys[0] = true;
            case 'c' -> this.keys[0xb] = true;
            case 'v' -> this.keys[0xf] = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyChar()) {
            case '1' -> this.keys[1] = false;
            case '2' -> this.keys[2] = false;
            case '3' -> this.keys[3] = false;
            case '4' -> this.keys[0xc] = false;

            case 'q' -> this.keys[4] = false;
            case 'w' -> this.keys[5] = false;
            case 'e' -> this.keys[6] = false;
            case 'r' -> this.keys[0xd] = false;

            case 'a' -> this.keys[7] = false;
            case 's' -> this.keys[8] = false;
            case 'd' -> this.keys[9] = false;
            case 'f' -> this.keys[0xe] = false;

            case 'z' -> this.keys[0xa] = false;
            case 'x' -> this.keys[0] = false;
            case 'c' -> this.keys[0xb] = false;
            case 'v' -> this.keys[0xf] = false;
        }
    }

    public boolean getKey(int key) {
        return this.keys[key];
    }
}
