import javafx.scene.paint.Color;

public class BeehivePattern implements LifePattern {

    // --- Phase 0: Horizontal Beehive (4x3 bounding box) ---
    private final byte[][] horizontalShape = new byte[][] {
        // Pattern: 0110
        {(byte) 0b0110}, // r=0
        // Pattern: 1001
        {(byte) 0b1001}, // r=1
        // Pattern: 0110
        {(byte) 0b0110}  // r=2
    };

    // --- Phase 1: Vertical Beehive (3x4 bounding box) ---
    private final byte[][] verticalShape = new byte[][] {
        // Pattern: 010
        {(byte) 0b0100}, 
        // Pattern: 101
        {(byte) 0b1010},
        // Pattern: 101
        {(byte) 0b1010},
        // Pattern: 010
        {(byte) 0b0100}
    };
    
    // We will use a wrapper method for matching since LifePattern's default matches() isn't used for multi-orientation patterns.
    private boolean getBit(byte[][] g, int x, int y, int rows, int cols) {
        x = (x + cols) % cols;
        y = (y + rows) % rows;
        return (g[y][x >> 3] & (1 << (x & 7))) != 0;
    }

    @Override
    public String getName() { return "Beehive"; }
    @Override public byte[][] getPattern() { return horizontalShape; }
    @Override public int getPatternWidth() { return 4; }
    @Override public int getPatternHeight() { return 3; }
    @Override public Color getHighlightColor() { return Color.DODGERBLUE; }
    @Override public int getPeriod() { return 1; }
    @Override public int getDx() { return 0; }
    @Override public int getDy() { return 0; }
    @Override public boolean isStillLife() { return true; }


    /**
     * Checks if the pattern exists at (row, col) in the grid, checking both orientations.
     * Since it's a Still Life, we only check the current state (grid).
     */
    @Override
    public boolean matches(byte[][] grid, int row, int col, int rows, int cols) {
        // Try to match the 4x3 horizontal shape
        if (matchesShape(grid, horizontalShape, 4, 3, row, col, rows, cols)) {
            // System.out.println("Beehive (Horizontal) found at: " + row + ", " + col);
            return true;
        }
        
        // Try to match the 3x4 vertical shape
        if (matchesShape(grid, verticalShape, 4, 4, row, col, rows, cols)) {
            // System.out.println("Beehive (Vertical) found at: " + row + ", " + col);
            return true;
        }
        
        return false;
    }
    
    private boolean matchesShape(byte[][] grid, byte[][] shape, int shapeWidth, int shapeHeight, 
                                 int row, int col, int rows, int cols) {
        
        if (row + shapeHeight > rows || col + shapeWidth > cols) return false;

        for (int r = 0; r < shapeHeight; r++) {
            byte patternRowByte = shape[r][0];
            for (int c = 0; c < shapeWidth; c++) {
                
                // Get expected pattern bit: uses (shapeWidth - 1 - c) for correct right-to-left packing
                boolean patternExpected = (patternRowByte & (1 << (shapeWidth - 1 - c))) != 0;
                
                // Get actual grid bit
                boolean cellAlive = getBit(grid, col + c, row + r, rows, cols);

                if (patternExpected != cellAlive) {
                    return false;
                }
            }
        }
        return true;
    }


    /** Marks the detected pattern onto the highlight buffer. */
    @Override
    public void markPattern(boolean[][] buffer, byte[][] grid, int row, int col, int rows, int cols) {
        // 1. Determine which phase (horizontal or vertical) matches the current location
        byte[][] currentShape;
        int shapeWidth, shapeHeight;

        if (matchesShape(grid, horizontalShape, 4, 3, row, col, rows, cols)) {
            currentShape = horizontalShape;
            shapeWidth = 4;
            shapeHeight = 3;
        } else if (matchesShape(grid, verticalShape, 4, 4, row, col, rows, cols)) {
            currentShape = verticalShape;
            shapeWidth = 4;
            shapeHeight = 4;
        } else {
            // Should not happen if markPattern is called after a successful match()
            return;
        }

        // 2. Mark the live cells of the determined phase
        for (int r = 0; r < shapeHeight; r++) {
            byte patternRowByte = currentShape[r][0];
            for (int c = 0; c < shapeWidth; c++) {
                
                boolean cellAlive = (patternRowByte & (1 << (shapeWidth - 1 - c))) != 0;
                
                if (cellAlive) {
                    int gx = (col + c + cols) % cols;
                    int gy = (row + r + rows) % rows;
                    buffer[gy][gx] = true;
                }
            }
        }
    }
}