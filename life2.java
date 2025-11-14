import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
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
import javafx.stage.FileChooser;
import javafx.animation.AnimationTimer;
import javafx.scene.paint.Color;
import javafx.scene.control.TextInputDialog;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;

import java.util.Optional;
import java.nio.ByteBuffer;
import java.util.ArrayDeque; // Added
import java.util.Deque;      // Added
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class life2 extends Application {

    private int rows = 1000;
    private int cols = 1000;
    private final int maxCanvasSize = 1000;
    private int cellSize;
    private int rowBytes;

    private byte[][] grid;
    private byte[][] next;

    private final int HISTORY_SIZE = 2;
    private final Deque<byte[][]> history = new ArrayDeque<>(HISTORY_SIZE);

    private boolean[][] highlightBuffer;
    private final int COLOR_DEAD = 0xFF000000; // Black
    private final int COLOR_ALIVE = 0xFFFFFFFF; // White
    private final int COLOR_HIGHLIGHT = 0xFFFF0000; // Red (for all patterns)

    // Removed history-related fields as they were only used for pattern detection

    private WritableImage image;
    private PixelWriter writer;
    private PixelFormat<ByteBuffer> format;
    private ByteBuffer rowBuffer;

    private ExecutorService pool;
    private AnimationTimer timer;
    private volatile boolean isCalculating = false; // Prevents frame stacking
    private ListView<String> patternListView;

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
	highlightBuffer = new boolean[rows][cols];	


        image = new WritableImage(cols, rows);
        writer = image.getPixelWriter();
        format = PixelFormat.createByteIndexedInstance(new int[]{COLOR_DEAD, COLOR_ALIVE, COLOR_HIGHLIGHT});
        rowBuffer = ByteBuffer.allocate(cols);
        pool = Executors.newFixedThreadPool(THREADS);
	
	MenuBar menuBar = new MenuBar();
	Menu fileMenu = new Menu("File");
	menuBar.getMenus().addAll(fileMenu);

	MenuItem openItem = new MenuItem("Open Grid...");
	MenuItem saveAsTextItem = new MenuItem("Save As Text...");

	fileMenu.getItems().addAll(openItem, saveAsTextItem);

        initializeGrid();
	saveHistorySnapshot();
        
        // --- Removed Pattern ListView and related imports ---
	patternListView = new ListView<>();
        patternListView.setItems(FXCollections.observableArrayList(
                "Block", "Blinker", "Glider", "Beehive" // Add "Toad", "Beacon" if you have them
        ));
        patternListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(maxCanvasSize);
        view.setFitHeight(maxCanvasSize);
        view.setOnScroll(this::handleZoom);

	view.setOnMouseClicked(this::handleMouseClick);

	saveAsTextItem.setOnAction(e -> saveGridAsText());
	openItem.setOnAction(e -> openGridFromFile(stage));

        Group viewGroup = new Group(view);
        ScrollPane scrollPane = new ScrollPane(viewGroup);
        scrollPane.setPannable(true);

        Button oneGenButton = new Button("Run One Generation");
        Button startButton = new Button("Start");
        Button stopButton = new Button("Stop");
	Button clearButton = new Button("Clear Grid");
        
        // Removed patternListView from controls VBox
        VBox controls = new VBox(10, oneGenButton, startButton, stopButton,clearButton, patternListView);
        controls.setStyle("-fx-padding: 10; -fx-background-color: #DDDDDD");

        BorderPane root = new BorderPane();
	root.setTop(menuBar);
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
                // Don't stack frames if calculation is still running
                if (isCalculating || (now - lastUpdate < delayNs)) {
                    return;
                }
                
                isCalculating = true;
                lastUpdate = now;
                
                // Get selected patterns on FX thread
                final List<String> selectedPatterns = patternListView.getSelectionModel().getSelectedItems();

                // --- Run ALL heavy logic on worker threads ---
                Runnable simulationTask = () -> {
                    try {
                        // 1. Save N-1 state
                        saveHistorySnapshot();
                        
                        // 2. Calculate N state (this blocks the worker thread, not FX thread)
                        updateGridParallel(); 
                        
                        // 3. Find patterns by comparing N-1 and N
                        highlightPatternsParallel(selectedPatterns);

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // 4. When done, schedule draw on FX thread
                        Platform.runLater(() -> {
                            drawGrid();
                            isCalculating = false;
                        });
                    }
                };
                pool.submit(simulationTask);
            }
        };        
	
	// Simplified button actions
	clearButton.setOnAction(e -> clearGrid());
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
        if (isCalculating) return; // Don't run if already running
        
        isCalculating = true;
        final List<String> selectedPatterns = patternListView.getSelectionModel().getSelectedItems();

        Runnable simulationTask = () -> {
            try {
                saveHistorySnapshot();
                updateGridParallel();
		//printGridState(prevGrid, grid);
                highlightPatternsParallel(selectedPatterns);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    drawGrid();
                    isCalculating = false;
                });
            }
        };
        pool.submit(simulationTask);
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

    private void saveHistorySnapshot() {
        if (history.size() >= HISTORY_SIZE) {
            history.removeFirst();
        }
        history.addLast(deepCopyGrid(grid));
    }

    private byte[][] deepCopyGrid(byte[][] src) {
        if (src == null) return null;
        byte[][] copy = new byte[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i].clone();
        }
        return copy;
    }

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

    public boolean getBit(byte[][] g, int x, int y) {
        return (g[y][x >> 3] & (1 << (x & 7))) != 0;
    }

    private void setBit(byte[][] g, int x, int y, boolean value) {
        int bit = 1 << (x & 7);
        int byteIndex = x >> 3;
        if (value) g[y][byteIndex] |= bit;
        else g[y][byteIndex] &= ~bit;
    }

    private void printGridState(byte[][] prevGrid, byte[][] currentGrid) {
    if (rows > 50 || cols > 50) {
        System.out.println("Grid is too large for full printout (Max 50x50 recommended). Printing aborted.");
        return;
    }
    
    int printRows = rows;
    int printCols = cols;

    System.out.println("=================================================");
    System.out.println("GENERATION DEBUG (Size: " + rows + "x" + cols + ")");
    System.out.println("=================================================");

    // Print Header
    System.out.printf("%-" + (printCols * 2 + 10) + "s %s\n", "PREVIOUS GRID (N-1)", "CURRENT GRID (N)");

    for (int y = 0; y < printRows; y++) {
        StringBuilder prevLine = new StringBuilder();
        StringBuilder currLine = new StringBuilder();
        
        for (int x = 0; x < printCols; x++) {
            boolean prevAlive = getBit(prevGrid, x, y);
            boolean currAlive = getBit(currentGrid, x, y);
            
            prevLine.append(prevAlive ? "■ " : "□ ");
            currLine.append(currAlive ? "■ " : "□ ");
        }
        
        System.out.printf("%-" + (printCols * 2 + 10) + "s %s\n", prevLine.toString(), currLine.toString());
    }
    System.out.println("=================================================");
}

    /** Draws the current grid state */
    private void drawGrid() {
        int imgWidth = cols;
        int imgHeight = rows;

        for (int y = 0; y < imgHeight; y++) {
            rowBuffer.clear();
            for (int x = 0; x < imgWidth; x++) {
                byte pixelValue;
                if (highlightBuffer[y][x]) {
                    pixelValue = 2; // Index for COLOR_HIGHLIGHT
                } else if (getBit(grid, x, y)) {
                    pixelValue = 1; // Index for COLOR_ALIVE
                } else {
                    pixelValue = 0; // Index for COLOR_DEAD
                }
                rowBuffer.put(pixelValue);
            }
            rowBuffer.flip();
            writer.setPixels(0, y, imgWidth, 1, format, rowBuffer, imgWidth);
        }
    }
    
    private void highlightPatternsParallel(List<String> selectedPatterns) throws InterruptedException {
        // 1. Clear the buffer
        for (boolean[] row : highlightBuffer) java.util.Arrays.fill(row, false);
        if (selectedPatterns == null || selectedPatterns.isEmpty()) {
            return;
        }

        // 2. Get previous grid state
        byte[][] prevGrid = (history.size() >= 2) ? history.getLast() : grid;
	//printGridState(prevGrid, grid);

        CountDownLatch latch = new CountDownLatch(selectedPatterns.size());

        for (String patternName : selectedPatterns) {
            pool.submit(() -> {
                try {
                    LifePattern pattern = null;
                    switch (patternName) {
                        case "Block":   pattern = new BlockPattern(); break;
                        case "Blinker": pattern = new BlinkerPattern(); break;
                        case "Glider":  pattern = new GliderPattern(); break;
			case "Beehive": pattern = new BeehivePattern();break;
                        // Add more patterns here
                    }
                    if (pattern == null) return;

                    // Iterate all cells to check for this pattern
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            boolean match = false;
                            if (pattern instanceof EvolvingPattern ep) {
                                // Evolving patterns need 'prevGrid' and 'grid'
                                match = ep.matchesAfterEvolution(prevGrid, grid, r, c, rows, cols);
                            } else {
                                // Still lifes only need the current 'grid'
                                match = pattern.matches(grid, r, c, rows, cols);
                            }

                            if (match) {
                                // Mark the cells in the shared buffer
                                // This is thread-safe because it only writes 'true'
                                pattern.markPattern(highlightBuffer, grid, r, c, rows, cols);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 3. Wait for all pattern-search threads to finish
        latch.await();
    }

    private void saveGridAsText() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Save Grid As Text");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
    File file = fileChooser.showSaveDialog(null);

    if (file != null) {
        try (PrintWriter writer = new PrintWriter(file)) {
            // Write rows and columns header first
            writer.println(rows + "x" + cols); 

            // Write the grid state row by row
            for (int y = 0; y < rows; y++) {
                StringBuilder rowString = new StringBuilder();
                for (int x = 0; x < cols; x++) {
                    // Use '1' for alive and '0' for dead
                    rowString.append(getBit(grid, x, y) ? '1' : '0');
                }
                writer.println(rowString.toString());
            }
            showError("Grid successfully saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Error saving file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
 
   private void openGridFromFile(Stage stage) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Open Grid File");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
    File file = fileChooser.showOpenDialog(stage);

    if (file != null) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            if (lines.isEmpty()) throw new IOException("File is empty.");

            // 1. Parse Dimensions from the first line (e.g., "1000x1000")
            String[] dimParts = lines.get(0).split("x");
            int newRows = Integer.parseInt(dimParts[0].trim());
            int newCols = Integer.parseInt(dimParts[1].trim());

            if (newRows <= 0 || newCols <= 0 || newRows > 5000 || newCols > 5000) {
                throw new IOException("Invalid grid dimensions specified in file.");
            }

            // 2. Stop/Reset Current Simulation
            timer.stop(); 
            isSimulationRunning = false;
            
            // 3. Re-initialize Grid and related structures
            rows = newRows;
            cols = newCols;
            rowBytes = (cols + 7) / 8;
            grid = new byte[rows][rowBytes];
            next = new byte[rows][rowBytes];
            highlightBuffer = new boolean[rows][cols]; // Assuming you changed this to int[][] for multiple colors, otherwise make it boolean[][]
            history.clear();

            // 4. Load Grid Data
            for (int y = 0; y < rows; y++) {
                if (y + 1 >= lines.size()) throw new IOException("File data incomplete.");
                String dataLine = lines.get(y + 1).trim();
                if (dataLine.length() != cols) throw new IOException("Row " + y + " has incorrect length.");
                
                for (int x = 0; x < cols; x++) {
                    boolean isAlive = dataLine.charAt(x) == '1';
                    setBit(grid, x, y, isAlive);
                }
            }

            // 5. Update UI and Draw
            // NOTE: You'll need to reconfigure the ImageView/WritableImage if the size changed drastically
            // For now, we only update the core grid, assuming the canvas scales.
            drawGrid(); 
            saveHistorySnapshot(); // Start a new history
            
            showError("Grid loaded successfully (" + rows + "x" + cols + ").");

        } catch (Exception e) {
            showError("Error loading grid file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

	// life2.java

/** Resets the entire grid, stopping the simulation and clearing history. */
private void clearGrid() {
    // 1. Stop the animation timer if running
    if (timer != null) {
        timer.stop();
        isSimulationRunning = false;
    }

    // 2. Clear the main grid arrays (setting all bytes to 0)
    for (int y = 0; y < rows; y++) {
        // Since the initial state of a byte array is 0, Arrays.fill is the fastest way
        java.util.Arrays.fill(grid[y], (byte) 0);
        java.util.Arrays.fill(next[y], (byte) 0);
    }
    
    // 3. Clear the highlight buffer (resetting all indices/colors to 0)
    if (highlightBuffer != null) {
        for (int y = 0; y < rows; y++) {
            java.util.Arrays.fill(highlightBuffer[y], false);
        }
    }

    // 4. Clear history
    history.clear();
    saveHistorySnapshot(); // Save the new, empty state as the starting point

    // 5. Redraw the grid to show a blank canvas
    drawGrid();
}
   

    @Override
    public void stop() throws Exception {
        if (pool != null && !pool.isShutdown()) pool.shutdownNow();
        super.stop();
    }

	

    public static void main(String[] args) { launch(); }
}
