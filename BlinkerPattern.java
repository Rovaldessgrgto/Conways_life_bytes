import javafx.scene.paint.Color;

public class BlinkerPattern implements EvolvingPattern {

    // Phase 1 (horizontal)
    private final byte[][] phase1 = {
        {(byte) 0b000},
        {(byte) 0b111},
        {(byte) 0b000}
    };

    // Phase 2 (vertical)
    private final byte[][] phase2 = {
        {(byte) 0b010},
        {(byte) 0b010},
        {(byte) 0b010}
    };
    
    @Override
    public String getName() { return "Blinker"; }

    @Override
    public byte[][] getPattern() { return phase1; }  // default

    @Override
    public int getPatternWidth() { return 3; }

    @Override
    public int getPatternHeight() { return 3; }

    @Override
    public Color getHighlightColor() { return Color.CYAN; }

    @Override
    public int getPeriod() { return 2; }

    @Override
    public int getDx() { return 0; }

    @Override
    public int getDy() { return 0; }

    // 🐛 FIX: Simplified getBit. It relies on the caller to provide wrapped coordinates.
    private boolean getBit(byte[][] grid, int x, int y, int rows, int cols) {
        // x = (x + cols) % cols; // Removed redundant wrap
        // y = (y + rows) % rows; // Removed redundant wrap
        return (grid[y][x >> 3] & (1 << (x & 7))) != 0;
    }

    /** Detects the blinker transition between two frames */
    @Override
    public boolean matchesAfterEvolution(byte[][] oldGrid, byte[][] newGrid,
                                         int row, int col, int rows, int cols) {
        if (matchesPhase(oldGrid, newGrid, row, col, rows, cols, phase1, phase2)
            || matchesPhase(oldGrid, newGrid, row, col, rows, cols, phase2, phase1)) {
            
            //System.out.println("Blinker detected at center (" + row + ", " + col + ")");
            return true;
        }
        return false;
    }

    private boolean matchesPhase(byte[][] oldGrid, byte[][] newGrid,
                                 int row, int col, int rows, int cols,
                                 byte[][] oldPhase, byte[][] newPhase) {
    // Change loops to run from -1 to 1 for dr (delta row) and dc (delta col)
    for (int dr = -1; dr <= 1; dr++) { 
        for (int dc = -1; dc <= 1; dc++) {
            
            // Calculate actual grid coordinates (Center anchor logic)
            int gx = (col + dc + cols) % cols;
            int gy = (row + dr + rows) % rows;
            
            // Use dr+1 and dc+1 to map back to the 0-indexed 3x3 pattern array
            boolean oldExpected = ((oldPhase[dr + 1][0] >> (2 - (dc + 1))) & 1) == 1; 
            boolean newExpected = ((newPhase[dr + 1][0] >> (2 - (dc + 1))) & 1) == 1;

            boolean oldAlive = getBit(oldGrid, gx, gy, rows, cols);
            boolean newAlive = getBit(newGrid, gx, gy, rows, cols);

            if (oldAlive != oldExpected || newAlive != newExpected) {
                return false;
            }
        }
    }
    return true;
}

     @Override
public void markPattern(boolean[][] buffer, byte[][] grid, int row, int col, int rows, int cols) {
    byte[][][] phases = {phase1, phase2};
    byte[][] currentPhase = phase1; 
    
    // We don't need to detect the phase if we assume this is only called after a match. 
    // However, if we must detect the phase for highlighting, we must use the correct logic:
    // The previous implementation for marking only checked if live pattern cells were live in the grid,
    // which is not a proper match for a phase. For thread-safe marking, we should use the same coordinates
    // used in matchesPhase, which center on the pattern's bounding box.

    // 1️⃣ Detect the current phase
    for (int phaseIndex = 0; phaseIndex < phases.length; phaseIndex++) {
        byte[][] phase = phases[phaseIndex];
        boolean match = true;

        for (int r = 0; r < 3 && match; r++) {
            for (int c = 0; c < 3 && match; c++) {
                boolean bit = ((phase[r][0] >> (2 - c)) & 1) == 1;
                int gx = (col + c - 1 + cols) % cols;
                int gy = (row + r - 1 + rows) % rows;
                
                // This checks if the grid is an exact match for the phase:
                if (getBit(grid, gx, gy, rows, cols) != bit) {
                    match = false;
                }
            }
        }

        if (match) {
            currentPhase = phase;
            break;
        }
    }

    // 2️⃣ Mark only the live cells of the detected phase
    for (int r = 0; r < 3; r++) {
        for (int c = 0; c < 3; c++) {
            boolean bit = ((currentPhase[r][0] >> (2 - c)) & 1) == 1;
            if (bit) {
                int gx = (col + c - 1 + cols) % cols;
                int gy = (row + r - 1 + rows) % rows;
                buffer[gy][gx] = true;
            }
        }
    }
}
}