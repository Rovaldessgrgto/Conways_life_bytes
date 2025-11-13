import javafx.application.Application;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.Group;
import javafx.stage.Stage;
import javafx.animation.AnimationTimer;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class life2 extends Application {

    private int rows = 1000;
    private int cols = 1000;
    private final int maxCanvasSize = 1000;
    private int cellSize;
    private int rowBytes;

    private byte[][] grid;
    private byte[][] next;

    // Removed history-related fields as they were only used for pattern detection

    private WritableImage image;
    private PixelWriter writer;
    private PixelFormat<ByteBuffer> format;
    private ByteBuffer rowBuffer;

    private ExecutorService pool;
    private AnimationTimer timer;

    private double scaleFactor = 1.0;
    private final double zoomStep = 0.1;

    private boolean isSimulationRunning = false;

    private final int THREADS = Runtime.getRuntime().availableProcessors();

    @Override
    public void start(Stage stage) {
        if (!askGridSize()) {
            System.out.println("Invalid grid size or cancelled.");
            return;
        }

        rowBytes = (cols + 7) / 8;
        grid = new byte[rows][rowBytes];
        next = new byte[rows][rowBytes];

        image = new WritableImage(cols, rows);
        writer = image.getPixelWriter();
        format = PixelFormat.createByteIndexedInstance(new int[]{0xFF000000, 0xFFFFFFFF});
        rowBuffer = ByteBuffer.allocate(cols);
        pool = Executors.newFixedThreadPool(THREADS);

        initializeGrid();
        
        // --- Removed Pattern ListView and related imports ---

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(maxCanvasSize);
        view.setFitHeight(maxCanvasSize);
        view.setOnScroll(this::handleZoom);

	view.setOnMouseClicked(this::handleMouseClick);

        Group viewGroup = new Group(view);
        ScrollPane scrollPane = new ScrollPane(viewGroup);
        scrollPane.setPannable(true);

        Button oneGenButton = new Button("Run One Generation");
        Button startButton = new Button("Start");
        Button stopButton = new Button("Stop");
        
        // Removed patternListView from controls VBox
        VBox controls = new VBox(10, oneGenButton, startButton, stopButton);
        controls.setStyle("-fx-padding: 10; -fx-background-color: #DDDDDD");

        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setRight(controls);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Game of Life"); // Title simplified
        stage.show();

        timer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                long delayNs = 100_000_000L; // 0.1 sec
                if (now - lastUpdate >= delayNs) {
                    try {
                        updateGridParallel();
                        // Removed saveHistorySnapshot()
                    } catch (Exception e) {
                        e.printStackTrace();
                        stop();
                    }
                    drawGrid();
                    // Removed highlightPatternsParallel()
                    lastUpdate = now;
                }
            }
        };

        // Simplified button actions
        oneGenButton.setOnAction(e -> runOnce());
        startButton.setOnAction(e -> {
		timer.start();
		isSimulationRunning = true;
		});
        stopButton.setOnAction(e -> {
		timer.stop();
		isSimulationRunning = false;
	});

        drawGrid();
    }

    private void runOnce() {
        try {
            updateGridParallel();
            // Removed saveHistorySnapshot()
            drawGrid();
            // Removed highlightPatternsParallel()
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // Add this method to your life2 class:

private void handleMouseClick(javafx.scene.input.MouseEvent event) {
    // 1. **GUARD**: Check your state flag (as we discussed)
    if (isSimulationRunning) {
        event.consume();
        return; 
    }

    // 2. **COORDINATES**: Get click position relative to the ImageView
    double clickX = event.getX();
    double clickY = event.getY();
    
    ImageView view = (ImageView) event.getSource();
    double effectiveWidth = view.getBoundsInLocal().getWidth();
    double effectiveHeight = view.getBoundsInLocal().getHeight();
    
    // 3. **TRANSLATE**: Convert view coordinates (e.g., 0-1000) to grid coordinates (e.g., 0-5000)
    int gridX = (int) Math.floor((clickX / effectiveWidth) * cols);
    int gridY = (int) Math.floor((clickY / effectiveHeight) * rows);
    
    // Bounds check
    if (gridX >= 0 && gridX < cols && gridY >= 0 && gridY < rows) {
        
        // 4. **TOGGLE**: Use YOUR getBit/setBit functions
        boolean isAlive = getBit(grid, gridX, gridY);
        setBit(grid, gridX, gridY, !isAlive);

        // 5. **REDRAW**: Use YOUR drawGrid function
        drawGrid(); 
    }
    
    event.consume();
}    private boolean askGridSize() {
        TextInputDialog dialog = new TextInputDialog("1000x1000");
        dialog.setTitle("Grid Size");
        dialog.setHeaderText("Enter grid size (format: rows x columns, up to 5000x5000)");
        dialog.setContentText("Example: 2500x3000");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return false;

        String input = result.get().toLowerCase().replace(" ", "");
        if (!input.matches("\\d+x\\d+")) return false;

        String[] parts = input.split("x");
        rows = Integer.parseInt(parts[0]);
        cols = Integer.parseInt(parts[1]);

        if (rows <= 0 || cols <= 0 || rows > 5000 || cols > 5000) {
            showError("Grid size must be between 1x1 and 5000x5000.");
            return false;
        }

        int cellWidth = maxCanvasSize / cols;
        int cellHeight = maxCanvasSize / rows;
        cellSize = Math.max(1, Math.min(cellWidth, cellHeight));

        return true;
    }

    private void handleZoom(ScrollEvent event) {
        if (event.getDeltaY() == 0) return;

        double zoomFactor = (event.getDeltaY() > 0) ? (1 + zoomStep) : (1 - zoomStep);
        scaleFactor *= zoomFactor;
        scaleFactor = Math.max(0.1, Math.min(scaleFactor, 10));

        ImageView view = (ImageView) event.getSource();
        view.setScaleX(scaleFactor);
        view.setScaleY(scaleFactor);
        event.consume();
    }

    private void initializeGrid() {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (Math.random() < 0.2)
                    setBit(grid, x, y, true);
            }
        }
        // Removed saveHistorySnapshot()
    }

    private void updateGridParallel() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(THREADS);
        int chunk = rows / THREADS;

        for (int t = 0; t < THREADS; t++) {
            final int start = t * chunk;
            final int end = (t == THREADS - 1) ? rows : start + chunk;

            pool.submit(() -> {
                for (int y = start; y < end; y++) {
                    for (int x = 0; x < cols; x++) {
                        int neighbors = countNeighbors(x, y);
                        boolean alive = getBit(grid, x, y);
                        boolean nextState = alive ? (neighbors == 2 || neighbors == 3) : (neighbors == 3);
                        setBit(next, x, y, nextState);
                    }
                }
                latch.countDown();
            });
        }

        latch.await();

        for (int y = 0; y < rows; y++) {
            System.arraycopy(next[y], 0, grid[y], 0, rowBytes);
        }
    }

    // Removed saveHistorySnapshot()
    // Removed deepCopyGrid()

    private int countNeighbors(int x, int y) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            int ny = (y + dy + rows) % rows;
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = (x + dx + cols) % cols;
                if (getBit(grid, nx, ny)) count++;
            }
        }
        return count;
    }

    private boolean getBit(byte[][] g, int x, int y) {
        return (g[y][x >> 3] & (1 << (x & 7))) != 0;
    }

    private void setBit(byte[][] g, int x, int y, boolean value) {
        int bit = 1 << (x & 7);
        int byteIndex = x >> 3;
        if (value) g[y][byteIndex] |= bit;
        else g[y][byteIndex] &= ~bit;
    }

    /** Draws the current grid state */
    private void drawGrid() {
        int imgWidth = cols;
        int imgHeight = rows;

        for (int y = 0; y < imgHeight; y++) {
            rowBuffer.clear();
            for (int x = 0; x < imgWidth; x++) {
                rowBuffer.put((byte) (getBit(grid, x, y) ? 1 : 0));
            }
            rowBuffer.flip();
            writer.setPixels(0, y, imgWidth, 1, format, rowBuffer, imgWidth);
        }
    }

    // Removed highlightPatternsParallel()

    @Override
    public void stop() throws Exception {
        if (pool != null && !pool.isShutdown()) pool.shutdownNow();
        super.stop();
    }

    public static void main(String[] args) { launch(); }
}

// --- Removed all LifePattern interfaces and classes (BlockPattern, BlinkerPattern, etc.) ---