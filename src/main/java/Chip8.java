import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class Chip8 {
    record Opcode(int a, int b, int c, int d) {
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
            boolean xorFlag = this.display[y][x] && bit;
            set(x, y, bit ^ this.display[y][x]);
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

    public FrameBuffer getFrameBuffer() {
        return this.frameBuffer;
    }

    private void execute(Opcode o) {
        if (o.a == 0 && o.b == 0 && o.c == 0xe && o.d == 0) { // 00E0 - clear the display
            this.frameBuffer.clear();
        } else if (o.a == 0 && o.b == 0 && o.c == 0xe && o.d == 0xe) { // 00EE - return
            this.programCounter = this.stack[stackPointer--];
        } else if (o.a == 1) { // 1nnn - jump
            this.programCounter = o._nnn() - 2;
        } else if (o.a == 2) { // 2nnn - call
            this.stack[++this.stackPointer] = this.programCounter;
            this.programCounter = o._nnn() - 2;
        } else if (o.a == 3) { // 3xkk - skip if Vx == kk
            if (this.registers[o._x__()] == o.__kk())
                this.programCounter += 2;
        } else if (o.a == 4) { // 4xkk - skip in Vx != kk
            if (this.registers[o._x__()] != o.__kk())
                this.programCounter += 2;
        } else if (o.a == 5 && o.d == 0) { // 5xy0 - skip if Vx == Vy
            if (this.registers[o._x__()] == this.registers[o.__y_()])
                this.programCounter += 2;
        } else if (o.a == 6) { // 6xkk - set Vx to kk
            this.registers[o._x__()] = o.__kk();
        } else if (o.a == 7) { // 7xkk - add kk to Vx
            this.registers[o._x__()] = (this.registers[o._x__()] + o.__kk()) & 0xff;
        } else if (o.a == 8) { // bitwise operations and math
            if (o.d == 0) { // 8xy0 - Vx = Vy
                this.registers[o._x__()] = this.registers[o.__y_()];
            } else if (o.d == 1) { // 8xy1 - Vx | Vy
                this.registers[o._x__()] |= this.registers[o.__y_()];
            } else if (o.d == 2) { // 8xy2 - Vx & Vy
                this.registers[o._x__()] &= this.registers[o.__y_()];
            } else if (o.d == 3) { // 8xy3 - Vx ^ Vy
                this.registers[o._x__()] ^= this.registers[o.__y_()];
            } else if (o.d == 4) { // 8xy4 - Vx += Vy; VF is set to carry flag
                int sum = this.registers[o._x__()] + this.registers[o.__y_()];
                this.registers[0xf] = ((sum > 255) ? 1 : 0);
                this.registers[o._x__()] = sum & 0xff;
            } else if (o.d == 5) { // 8xy5 - Vx -= Vy; sets carry
                this.registers[0xf] = (this.registers[o._x__()] < this.registers[o.__y_()]) ? 1 : 0;
                this.registers[o._x__()] = (this.registers[o._x__()] - this.registers[o.__y_()]) & 0xff;
            } else if (o.d == 6) { // 8x_6 - Vx >>= 1; sets Vf to 1 if LSB of Vx was 1
                this.registers[0xf] = this.registers[o._x__()] & 0b00000001;
                this.registers[o._x__()] >>= 1;
            } else if (o.d == 7) { // 8xy7 - Vx = Vy - Vx; sets carry
                this.registers[0xf] = (this.registers[o._x__()] > this.registers[o.__y_()]) ? 1 : 0;
                this.registers[o._x__()] = (this.registers[o.__y_()] - this.registers[o._x__()]) & 0xff;
            } else if (o.d == 0xe) { // 8x_e - Vx <<= 1; sets Vf to 1 if MSB of Vx was 1
                this.registers[0xf] = this.registers[o._x__()] & 0b10000000;
                this.registers[o._x__()] = (this.registers[o._x__()] << 1) & 0xff;
            }
        } else if (o.a == 9) { // 9xy0 - skip if Vx != Vy
            if (this.registers[o._x__()] != this.registers[o.__y_()])
                this.programCounter += 2;
        } else if (o.a == 0xa) { // annn - load value into I
            this.index = o._nnn();
        } else if (o.a == 0xb) { // bnnn - jump to nnn + v0
            this.programCounter = (short) (o._nnn() + this.registers[0]);
        } else if (o.a == 0xc) { // cxkk - Vx = random() & kkk
            this.registers[o._x__()] = random.nextInt(256) & o.__kk();
        } else if (o.a == 0xd) { // Dxyn - draw
            boolean collision = false;
            int x = this.registers[o._x__()], y = this.registers[o.__y_()];

            for (int i = 0; i < o.___n(); i++) {
                int b = this.memory[this.index + i];
                for (int j = 0; j < 8; j++) {
                    int real_x = (x + j) % 64, real_y = (y + i) % 32;
                    boolean isSet = (b & (0b10000000 >> j)) != 0;
                   collision = collision || this.frameBuffer.setXor(real_x, real_y, isSet);
                }
            }

            this.registers[0xf] = collision ? 1 : 0;
        } else if (o.a == 0xe) {
            System.out.println("Unsupported opcode " + o);
        } else if (o.a == 0xf) {
            if (o.d == 0x7) { // fx07 - Vx = DT
                this.registers[o._x__()] = delayTimer;
            } else if (o.d == 0xa) { // fx0a - wait for key press in Vx
                System.out.println("Unsupported opcode " + o);
            } else if (o.c == 0x1 && o.d == 0x5) { // fx15 - delay timer = Vx
                delayTimer = this.registers[o._x__()];
            } else if (o.c == 0x1 && o.d == 0x8) { // fx18 - sound timer = Vx
                soundTimer = this.registers[o._x__()];
            } else if (o.c == 0x1 && o.d == 0xe) { // fx1e - I += Vx
                this.index += this.registers[o._x__()];
            } else if (o.c == 0x2 && o.d == 0x9) {
                System.out.println("Unsupported opcode " + o);
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
            }
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
}
