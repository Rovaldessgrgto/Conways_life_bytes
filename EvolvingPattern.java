public interface EvolvingPattern extends LifePattern {
    boolean matchesAfterEvolution(byte[][] prev, byte[][] curr, int row, int col, int rows, int cols);
}
