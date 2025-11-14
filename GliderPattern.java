import javafx.scene.paint.Color;

public class GliderPattern implements EvolvingPattern {

    private final byte[][][] phases = {
        {   // Phase 0
            {(byte)0b00000},
            {(byte)0b00100},
            {(byte)0b00010},
            {(byte)0b01110},
            {(byte)0b00000}
        },
        {   // Phase 1
            {(byte)0b00000},
            {(byte)0b01010},
            {(byte)0b00110},
            {(byte)0b00100},
            {(byte)0b00000}
        },
        {   // Phase 2
            {(byte)0b00000},
            {(byte)0b00010},
            {(byte)0b01010},
            {(byte)0b00110},
            {(byte)0b00000}
        },
        {   // Phase 3
            {(byte)0b00000},
            {(byte)0b01000},
            {(byte)0b00110},
            {(byte)0b01100},
            {(byte)0b00000}
        }
    };

    @Override public String getName() { return "Glider"; }
    @Override public byte[][] getPattern() { return phases[0]; }
    @Override public int getPatternWidth() { return 5; }
    @Override public int getPatternHeight() { return 5; }
    @Override public Color getHighlightColor() { return Color.BLUE; }
    @Override public int getPeriod() { return 4; }
    @Override public int getDx() { return 1; }
    @Override public int getDy() { return 1; }

    // ─────────────────────────────
    // Helper: Simplified getBit (Assumes coordinates are already wrapped)
    // ─────────────────────────────
    private boolean getBit(byte[][] grid, int x, int y, int rows, int cols) {
        // Note: The logic in the grid access method handles wrapping
        return (grid[y][x >> 3] & (1 << (x & 7))) != 0;
    }

    // ─────────────────────────────
    // Pattern Matching Logic
    // ─────────────────────────────
    @Override
    public boolean matchesAfterEvolution(byte[][] oldGrid, byte[][] newGrid,
                                         int row, int col, int rows, int cols) {
        for (int newPhaseIndex = 0; newPhaseIndex < 4; newPhaseIndex++) {
            if (matchesPhaseTransition(oldGrid, newGrid, row, col, rows, cols, newPhaseIndex)) {
                // System.out.println("Glider detected at TOP-LEFT (" + row + ", " + col + ")");
                return true;
            }
        }
        return false;
    }

    private boolean matchesPhaseTransition(byte[][] oldGrid, byte[][] newGrid,
                                           int row, int col, int rows, int cols,
                                           int newPhaseIndex) {
        int oldPhaseIndex = (newPhaseIndex - 1 + 4) % 4;
        byte[][] oldPhase = phases[oldPhaseIndex];
        byte[][] newPhase = phases[newPhaseIndex];

        int rowOffset = 0;
        int colOffset = 0;
        
        // 🛑 CRITICAL USER REQUEST: P3 -> P0 transition offset ELIMINATED 🛑
        if (oldPhaseIndex == 3 && newPhaseIndex == 0) { 
            rowOffset = 0; // Was typically -1
            colOffset = 0; // Was typically -1
        } 
        else if (oldPhaseIndex == 0 && newPhaseIndex == 1) { 
            rowOffset = -1; 
        } 
        else if (oldPhaseIndex == 2 && newPhaseIndex == 3) { 
            colOffset = -1; 
        }
        
        int patternWidth = getPatternWidth();

        for (int r = 0; r < getPatternHeight(); r++) {
            for (int c = 0; c < patternWidth; c++) {
                
                // Calculate Old Grid coordinates: shifted by the offset
                int oldY = (row + r + rowOffset + rows) % rows;
                int oldX = (col + c + colOffset + cols) % cols;
                
                // Calculate New Grid coordinates: anchored at (row, col)
                int newY = (row + r + rows) % rows;
                int newX = (col + c + cols) % cols;

                // Get actual state from grid
                boolean oldAlive = getBit(oldGrid, oldX, oldY, rows, cols);
                boolean newAlive = getBit(newGrid, newX, newY, rows, cols);

                // Get expected state from pattern definition
                boolean oldExpected = (oldPhase[r][0] & (1 << (patternWidth - 1 - c))) != 0;
                boolean newExpected = (newPhase[r][0] & (1 << (patternWidth - 1 - c))) != 0;

                // Must be an exact match
                if (oldAlive != oldExpected || newAlive != newExpected) return false;
            }
        }
        return true;
    }

    // ─────────────────────────────
    // Highlighting/Marking Logic
    // ─────────────────────────────
    @Override
    public void markPattern(boolean[][] buffer,byte[][] grid, int row, int col, int rows, int cols) {
        
        // Find the phase that *exactly* matches the current grid state (N)
        int matchedPhase = 0;
        for (int phase = 0; phase < 4; phase++) {
            if (matchesCurrentPhase(grid, row, col, phases[phase], rows, cols)) {
                matchedPhase = phase;
                break;
            }
        }

        byte[][] shape = phases[matchedPhase];
        int patternHeight = getPatternHeight();
        int patternWidth = getPatternWidth();

        // Mark only the live cells of the matched phase
        for (int r = 0; r < patternHeight; r++) {
            for (int c = 0; c < patternWidth; c++) {
                boolean cellAlive = (shape[r][0] & (1 << (patternWidth - 1 - c))) != 0;
                if (cellAlive) {
                    int gx = (col + c + cols) % cols;
                    int gy = (row + r + rows) % rows;
                    buffer[gy][gx] = true;
                }
            }
        }
    }

    // ─────────────────────────────
    // Helper: Exact match for the current phase (used by markPattern)
    // ─────────────────────────────
    private boolean matchesCurrentPhase(byte[][] grid, int row, int col,
                                       byte[][] phase, int rows, int cols) {
        int patternHeight = getPatternHeight();
        int patternWidth = getPatternWidth();

        for (int r = 0; r < patternHeight; r++) {
            for (int c = 0; c < patternWidth; c++) {
                
                int gx = (col + c + cols) % cols;
                int gy = (row + r + rows) % rows;
                
                // Expected state from pattern byte
                boolean expected = (phase[r][0] & (1 << (patternWidth - 1 - c))) != 0;
                
                // Actual state from grid (must be an exact match)
                boolean actual = getBit(grid, gx, gy, rows, cols);
                
                if (actual != expected) {
                    return false;
                }
            }
        }
        return true;
    }
}

