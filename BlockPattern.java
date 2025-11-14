import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;

public class BlockPattern implements LifePattern {

    private final byte[][] pattern = new byte[][] {
        {(byte) 0b0000},
        {(byte) 0b0110},
        {(byte) 0b0110},
        {(byte) 0b0000}
    };
	
    @Override
    public String getName() { return "Block"; }
    @Override public boolean isStillLife() { return true; }
    @Override
    public byte[][] getPattern() { return pattern; }
    @Override
    public int getPatternWidth() { return 4; }
    @Override
    public int getPatternHeight() { return 4; }
    @Override
    public Color getHighlightColor() { return Color.BLUE; }
    @Override
    public int getPeriod() { return 1; }
    @Override
    public int getDx() { return 0; }
    @Override
    public int getDy() { return 0; }

    // Use a helper class or make getBit public static
    private boolean getBit(byte[][] grid, int x, int y) {
        return (grid[y][x >> 3] & (1 << (x & 7))) != 0;
    }

    @Override
    public boolean matches(byte[][] grid, int row, int col, int rows, int cols) {
        if (row + getPatternHeight() > rows || col + getPatternWidth() > cols) return false;

        for (int r = 0; r < getPatternHeight(); r++) {
            byte patternRow = pattern[r][0];
            for (int c = 0; c < getPatternWidth(); c++) {
                boolean patternAlive = (patternRow & (1 << (getPatternWidth() - 1 - c))) != 0;
                boolean cellAlive = getBit(grid, col + c, row + r);

                //System.out.printf("Checking row=%d col=%d: patternAlive=%b, cellAlive=%b%n",
                        //row + r, col + c, patternAlive, cellAlive);

                if (patternAlive != cellAlive) {
                    return false;
                }
            }
        }

        //System.out.println("Pattern matched at row=" + row + ", col=" + col);
        return true;
    }

    
public void markPattern(boolean[][] buffer, byte[][] grid,int row, int col, int rows, int cols) {
    int patternHeight = getPatternHeight();
    int patternWidth = getPatternWidth();

    for (int r = 0; r < patternHeight; r++) {
        for (int c = 0; c < patternWidth; c++) {
            // Only alive cells
            boolean alive = (pattern[r][0] & (1 << (patternWidth - 1 - c))) != 0;
            if (!alive) continue;

            int gx = (col + c) % cols;
            int gy = (row + r) % rows;

            buffer[gy][gx] = true;
        }
    }
}
}
