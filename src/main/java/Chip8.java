import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class Chip8 {
    record Opcode(int a, int b, int c, int d) {
        public int nnnn() {
            return this.a << 12 | this.b << 8 | this.c << 4 | this.d;
        }

        public int _nnn() {
            return (short) (this.b << 8 | this.c << 4 | this.d);
        }

        public int ___n() {
            return this.d;
        }

        public int _x__() {
            return this.b;
        }

        public int __y_() {
            return this.c;
        }

        public int __kk() {
            return this.c << 4 | this.d;
        }

        Opcode(int a, int b) {
            this(a >> 4, a & 0b00001111, b >> 4, b & 0b00001111);
        }
    }

    static class FrameBuffer {
        public final int WIDTH = 64, HEIGHT = 32;
        private final boolean[][] display = new boolean[HEIGHT][WIDTH];
        private boolean dirty = false;

        public boolean isDirty() {
            return this.dirty;
        }

        public void redraw() {
            this.dirty = false;
        }

        public void clear() {
            for (int i = 0; i < HEIGHT; i++)
                for (int j = 0; j < WIDTH; j++)
                    set(j, i, false);
        }

        public boolean get(int x, int y) {
            return this.display[y][x];
        }

        public void set(int x, int y, boolean bit) {
            this.display[y][x] = bit;
            this.dirty = true;
        }

        public boolean setXor(int x, int y, boolean bit) {
            boolean xorFlag = this.get(x, y) && bit;
            set(x, y, bit ^ this.get(x, y));
            return xorFlag;
        }

    }

    private final Random random = new Random();
    private final int[] registers = new int[16];
    private int index = 0;
    private final int[] stack = new int[32];
    private int stackPointer = -1;
    private int programCounter = 0x200;
    private final int[] memory = new int[4096];
    private int delayTimer = 0;
    private int soundTimer = 0;
    private final FrameBuffer frameBuffer = new FrameBuffer();
    private final InputManager inputManager;

    public FrameBuffer getFrameBuffer() {
        return this.frameBuffer;
    }

    private void unsupportedOpcode(Opcode o) {
        System.out.println("Unsupported opcode " + o);
    }

    private void execute_0(Opcode o) {
        if (o.nnnn() == 0x00e0) { // 00E0 - clear the display
            this.frameBuffer.clear();
        } else if (o.nnnn() == 0x00ee) { // 00EE - return
            this.programCounter = this.stack[stackPointer--];
        }
    }

    private void execute_8(Opcode o) {
        switch (o.d) {
            case 0 -> { // 8xy0 - Vx = Vy
                this.registers[o._x__()] = this.registers[o.__y_()];
            }

            case 1 -> { // 8xy1 - Vx | Vy
                this.registers[o._x__()] |= this.registers[o.__y_()];
                this.registers[0xf] = 0; // OG chip-8 quirk
            }

            case 2 -> { // 8xy2 - Vx & Vy
                this.registers[o._x__()] &= this.registers[o.__y_()];
                this.registers[0xf] = 0; // OG chip-8 quirk
            }

            case 3 -> { // 8xy3 - Vx ^ Vy
                this.registers[o._x__()] ^= this.registers[o.__y_()];
                this.registers[0xf] = 0; // OG chip-8 quirk
            }

            case 4 -> { // 8xy4 - Vx += Vy; VF is set to carry flag
                int sum = this.registers[o._x__()] + this.registers[o.__y_()];
                this.registers[o._x__()] = sum & 0xff;
                this.registers[0xf] = ((sum > 255) ? 1 : 0);
            }

            case 5 -> { // 8xy5 - Vx -= Vy; sets carry
                int carry = (this.registers[o._x__()] > this.registers[o.__y_()]) ? 1 : 0;
                this.registers[o._x__()] = (this.registers[o._x__()] - this.registers[o.__y_()]) & 0xff;
                this.registers[0xf] = carry;
            }

            case 6 -> { // 8xy6 - Vx = Vy; Vx >>= 1; sets Vf to 1 if LSB of Vx was 1
                this.registers[o._x__()] = this.registers[o.__y_()]; // OG Chip-8 quirk
                int carry = this.registers[o._x__()] & 0b00000001;
                this.registers[o._x__()] >>= 1;
                this.registers[0xf] = carry;
            }

            case 7 -> { // 8xy7 - Vx = Vy - Vx; sets carry
                int carry = (this.registers[o._x__()] < this.registers[o.__y_()]) ? 1 : 0;
                this.registers[o._x__()] = (this.registers[o.__y_()] - this.registers[o._x__()]) & 0xff;
                this.registers[0xf] = carry;
            }

            case 0xe -> { // 8xye - Vx = Vy; Vx <<= 1; sets Vf to 1 if MSB of Vx was 1
                this.registers[o._x__()] = this.registers[o.__y_()]; // OG Chip-8 quirk
                int carry = (this.registers[o._x__()] & 0b10000000) >> 7;
                this.registers[o._x__()] = (this.registers[o._x__()] << 1) & 0xff;
                this.registers[0xf] = carry;
            }

            default -> {
                unsupportedOpcode(o);
            }
        }
    }

    private void execute_d(Opcode o) {
        int x = this.registers[o._x__()] % 64, y = this.registers[o.__y_()] % 32, height = o.___n();
        for (int i = 0; i < height; i++) {
            if (y + i >= 32)
                break;

            int b = this.memory[this.index + i];
            for (int j = 0; j < 8; j++) {
                if (x + j >= 64)
                    break;

                if ((b & (0x80 >> j)) != 0)
                    this.registers[0xf] = this.getFrameBuffer().setXor(x + j, i + y, true) ? 1 : 0;
            }
        }
    }

    private void execute_e(Opcode o) {
        if (o.c == 0x9 && o.d == 0xe) { // Ex9E - skip if key in Vx is pressed
            if (this.inputManager.getKey(this.registers[o._x__()]))
                programCounter += 2;
        } else if (o.c == 0xa && o.d == 0x1) { // ExA1 - skip if key in Vx is not pressed
            if (!this.inputManager.getKey(this.registers[o._x__()]))
                programCounter += 2;
        }
    }

    private void execute_f(Opcode o) {
        if (o.d == 0x7) { // fx07 - Vx = DT
            this.registers[o._x__()] = delayTimer;
        } else if (o.d == 0xa) { // fx0a - wait for key press; store it in Vx
            this.registers[o._x__()] = inputManager.waitForKey();
        } else if (o.c == 0x1 && o.d == 0x5) { // fx15 - delay timer = Vx
            delayTimer = this.registers[o._x__()];
        } else if (o.c == 0x1 && o.d == 0x8) { // fx18 - sound timer = Vx
            soundTimer = this.registers[o._x__()];
        } else if (o.c == 0x1 && o.d == 0xe) { // fx1e - I += Vx
            this.index += this.registers[o._x__()];
        } else if (o.c == 0x2 && o.d == 0x9) { // fx29 - set I to hex font
            this.index = this.memory[this.registers[o._x__()] * 5];
        } else if (o.c == 0x3 && o.d == 0x3) { // fx33 - store bcd of Vx at I
            int value = this.registers[o._x__()];
            this.memory[this.index] = value / 100;
            this.memory[this.index + 1] = value % 100 / 10;
            this.memory[this.index + 2] = value % 10;
        } else if (o.c == 0x5 && o.d == 0x5) { // Fx55 - store registers
            for (int i = 0; i < o._x__() + 1; i++) {
                this.memory[this.index + i] = this.registers[i];
            }

            this.index += o._x__() + 1;
        } else if (o.c == 0x6 && o.d == 0x5) { // Fx65 - restore registers
            for (int i = 0; i < o._x__() + 1; i++) {
                this.registers[i] = this.memory[this.index + i];
            }

            this.index += o._x__() + 1;
        } else {
            System.out.println("Unsupported opcode " + o);
        }
    }


    private void execute(Opcode o) {
        switch (o.a) {
            case 0 -> {
                execute_0(o);
            }

            case 1 -> { // 1nnn - jump
                this.programCounter = o._nnn() - 2;
            }

            case 2 -> { // 2nnn - call
                this.stack[++this.stackPointer] = this.programCounter;
                this.programCounter = o._nnn() - 2;
            }

            case 3 -> { // 3xkk - skip if Vx == kk
                if (this.registers[o._x__()] == o.__kk())
                    this.programCounter += 2;
            }

            case 4 -> { // 4xkk - skip in Vx != kk
                if (this.registers[o._x__()] != o.__kk())
                    this.programCounter += 2;
            }

            case 5 -> { // 5xy0 - skip if Vx == Vy
                if (this.registers[o._x__()] == this.registers[o.__y_()])
                    this.programCounter += 2;
            }

            case 6 -> { // 6xkk - set Vx to kk
                this.registers[o._x__()] = o.__kk();
            }

            case 7 -> { // 7xkk - add kk to Vx
                this.registers[o._x__()] = (this.registers[o._x__()] + o.__kk()) & 0xff;
            }

            case 8 -> execute_8(o);

            case 9 -> { // 9xy0 - skip if Vx != Vy
                if (this.registers[o._x__()] != this.registers[o.__y_()])
                    this.programCounter += 2;
            }

            case 0xa -> { // annn - load value into I
                this.index = o._nnn();
            }

            case 0xb -> { // bnnn - jump to nnn + v0
                this.programCounter = (short) (o._nnn() + this.registers[0] - 2);
            }

            case 0xc -> { // cxkk - Vx = random() & kkk
                this.registers[o._x__()] = random.nextInt(256) & o.__kk();
            }

            case 0xd -> execute_d(o);
            case 0xe -> execute_e(o);
            case 0xf -> execute_f(o);
        }
    }

    public boolean tick() {
        Opcode opcode = new Opcode(this.memory[programCounter], this.memory[programCounter + 1]);
        execute(opcode);
        this.programCounter += 2;

        return this.programCounter <= 4096;
    }

    public void tickTimer() {
        if (this.delayTimer > 0) {
            this.delayTimer -= 1;
        }

        if (this.soundTimer > 0) {
            this.soundTimer -= 1;
        }
    }

    /**
     * Reads ROM from InputStream into memory
     */
    public void load(InputStream stream) throws IOException {
        int i = 0x200, b = 0;
        while ((b = stream.read()) != -1 && i < 4096) {
            memory[i++] = b;
        }
    }

    public Chip8(InputManager inputManager) {
        this.inputManager = inputManager;

        // Load hex font
        int[] hexFont = {
                0xf0, 0x90, 0x90, 0x90, 0xF0, // 0
                0x20, 0x60, 0x20, 0x20, 0x70, // 1
                0xf0, 0x10, 0xf0, 0x80, 0xf0, // 2
                0xf0, 0x10, 0xf0, 0x10, 0xf0, // 3
                0x90, 0x90, 0xf0, 0x10, 0x10, // 4
                0xf0, 0x80, 0xf0, 0x10, 0xf0, // 5
                0xf0, 0x80, 0xf0, 0x90, 0xf0, // 6
                0xf0, 0x10, 0x20, 0x40, 0x40, // 7
                0xf0, 0x90, 0xf0, 0x90, 0xf0, // 8
                0xf0, 0x90, 0xf0, 0x10, 0xf0, // 9
                0xf0, 0x90, 0xf0, 0x90, 0x90, // a
                0xe0, 0x90, 0xe0, 0x90, 0xe0, // b
                0xf0, 0x80, 0x80, 0x80, 0xf0, // c
                0xe0, 0x90, 0x90, 0x90, 0xe0, // d
                0xf0, 0x80, 0xf0, 0x80, 0xf0, // e
                0xf0, 0x80, 0xf0, 0x80, 0x80  // f
        };
        System.arraycopy(hexFont, 0, this.memory, 0, 16 * 5);
    }
}
