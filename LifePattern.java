import javafx.scene.paint.Color;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.PixelFormat;
import java.nio.ByteBuffer;

public interface LifePattern {

    /** Return the pattern as a byte[][]; each byte represents up to 8 cells. */
    byte[][] getPattern();

    /** Width in bits of the pattern (number of cells per row). */
    int getPatternWidth();

    /** Height of the pattern (number of rows). */
    default int getPatternHeight() {
        return getPattern().length;
    }

    default boolean isStillLife() { return false; }

    String getName();

    /** Color to highlight the pattern */
    Color getHighlightColor();

    /** Period of the pattern (1 for still lifes) */
    int getPeriod();

    /** Horizontal shift per period */
    int getDx();

    /** Vertical shift per period */
    int getDy();

    /**
     * Checks if the pattern exists at (row, col) in the grid.
     * Grid is byte[][] with 8 cells per byte.
     */
    default boolean matches(byte[][] grid, int row, int col, int rows, int cols) {
        byte[][] p = getPattern();
        int patternHeight = getPatternHeight();
        int patternWidth = getPatternWidth();

        for (int r = 0; r < patternHeight; r++) {
            int gy = (row + r) % rows;
            for (int c = 0; c < patternWidth; c++) {
                int gx = (col + c) % cols;

                boolean gridCell = (grid[gy][gx >> 3] & (1 << (gx & 7))) != 0;
                boolean patternCell = (p[r][0] & (1 << c)) != 0;

                if (gridCell != patternCell) return false;
            }
        }
        return true;
    }

    /**
     * Highlights the pattern in the WritableImage at the given position.
     * Scales correctly for the canvas size.
     */
    
public void markPattern(boolean[][] buffer,byte[][] grid, int row, int col, int rows, int cols);
}