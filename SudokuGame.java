import javax.swing.*;
import java.awt.*;
import java.util.Random;

/**
 * SudokuGame.java
 * Single-file implementation:
 * - Basic backtracking solver/generator
 * - Puzzle generator (hapus sel acak sesuai difficulty)
 * - GUI with timer, hint (1x per stage), mistake counter (max 3)
 *
 * Saat mistakeCount >= 3 -> generateSudoku(currentDifficulty) dipanggil,
 * sehingga reset menggunakan backtracking generator.
 */
public class SudokuGame extends JFrame {
    private int[][] board = new int[9][9];         // puzzle yang tampil (0 = kosong)
    private JTextField[][] cells = new JTextField[9][9];
    private int[][] solution = new int[9][9];      // solusi penuh (filled)
    private int mistakeCount = 0;
    private boolean hintUsed = false;
    private Timer gameTimer;
    private int elapsedSeconds = 0;
    private JLabel timerLabel;
    private JLabel mistakeLabel;
    private String currentDifficulty = "Easy";

    public SudokuGame() {
        setTitle("Sudoku Klasik");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(1, 3));
        timerLabel = new JLabel("Waktu: 0 detik", SwingConstants.CENTER);
        mistakeLabel = new JLabel("Kesalahan: 0/3", SwingConstants.CENTER);
        JButton hintButton = new JButton("Hint (1x per stage)");
        topPanel.add(timerLabel);
        topPanel.add(mistakeLabel);
        topPanel.add(hintButton);
        add(topPanel, BorderLayout.NORTH);

        JPanel boardPanel = new JPanel(new GridLayout(9, 9));
        Font cellFont = new Font("Arial", Font.BOLD, 20);
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                JTextField tf = new JTextField();
                tf.setHorizontalAlignment(SwingConstants.CENTER);
                tf.setFont(cellFont);
                tf.setBorder(BorderFactory.createLineBorder(Color.BLACK));

                int row = i;
                int col = j;

                // action on enter
                tf.addActionListener(e -> handleUserInput(tf, row, col));

                // also update when focus lost (user types then clicks away)
                tf.addFocusListener(new java.awt.event.FocusAdapter() {
                    public void focusLost(java.awt.event.FocusEvent evt) {
                        handleUserInput(tf, row, col);
                    }
                });

                cells[i][j] = tf;
                boardPanel.add(tf);
            }
        }
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        JButton newGameBtn = new JButton("Tingkat Kesulitan");
        bottomPanel.add(newGameBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        // Tombol Hint
        hintButton.addActionListener(e -> useHint());

        // Tombol New Game
        newGameBtn.addActionListener(e -> showDifficultyDialog());

        // Timer untuk menghitung waktu (count-up)
        gameTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            timerLabel.setText("Waktu: " + elapsedSeconds + " detik");
        });

        // Mulai permainan pertama kali
        generateSudoku("Easy");
    }

    // Pop-up untuk memilih tingkat kesulitan
    private void showDifficultyDialog() {
        String[] levels = {"Easy", "Medium", "Hard"};
        String choice = (String) JOptionPane.showInputDialog(
                this,
                "Pilih tingkat kesulitan:",
                "Mode Klasik",
                JOptionPane.PLAIN_MESSAGE,
                null,
                levels,
                currentDifficulty
        );

        if (choice != null) {
            currentDifficulty = choice;
            generateSudoku(choice);
        }
    }

    private void handleUserInput(JTextField cell, int row, int col) {
        String text = cell.getText().trim();
        if (text.isEmpty()) return;

        // Jika sel bukan editable (sudah terisi dari awal), jangan ubah
        if (!cell.isEditable()) {
            // memastikan tampilan kembali sesuai solusi jika user mengedit non-editable
            cell.setText(String.valueOf(board[row][col]));
            return;
        }

        try {
            int val = Integer.parseInt(text);
            if (val < 1 || val > 9) throw new NumberFormatException();

            if (solution[row][col] == val) {
                board[row][col] = val;
                cell.setForeground(Color.BLACK);
                cell.setEditable(false);

                if (checkWin()) {
                    gameTimer.stop();
                    JOptionPane.showMessageDialog(this,
                            "Selamat! Kamu menyelesaikan Sudoku dalam " + elapsedSeconds + " detik 🎉");
                }
            } else {
                mistakeCount++;
                mistakeLabel.setText("Kesalahan: " + mistakeCount + "/3");
                flashRed(cell);
                cell.setText("");
                if (mistakeCount >= 3) {
                    JOptionPane.showMessageDialog(this, "Salah 3 kali! Stage direset ulang.");
                    // RESET menggunakan backtracking generator: generateSudoku akan membuat solusi valid baru
                    generateSudoku(currentDifficulty);
                }
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Masukkan angka 1-9 saja!");
            cell.setText("");
        }
    }

    // Efek berkedip merah jika salah
    private void flashRed(JTextField cell) {
        Color original = cell.getBackground();
        cell.setBackground(Color.PINK);
        Timer t = new Timer(300, e -> cell.setBackground(original));
        t.setRepeats(false);
        t.start();
    }

    // Fungsi untuk Hint (1x per stage) — isi sel kosong pertama dengan solusi
    private void useHint() {
        if (hintUsed) {
            JOptionPane.showMessageDialog(this, "Hint sudah digunakan untuk stage ini!");
            return;
        }

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] == 0) {
                    board[i][j] = solution[i][j];
                    cells[i][j].setText(String.valueOf(solution[i][j]));
                    cells[i][j].setEditable(false);
                    cells[i][j].setForeground(Color.BLUE);
                    hintUsed = true;
                    return;
                }
            }
        }
    }

    /* ===========================
       BACKTRACKING SOLVER & GENERATOR
       =========================== */

    // Cek apakah num bisa ditempatkan di grid[row][col]
    private boolean isSafe(int[][] grid, int row, int col, int num) {
        // Cek baris & kolom
        for (int x = 0; x < 9; x++) {
            if (grid[row][x] == num) return false;
            if (grid[x][col] == num) return false;
        }

        // Cek subgrid 3x3
        int startRow = row - row % 3;
        int startCol = col - col % 3;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (grid[startRow + i][startCol + j] == num) return false;
            }
        }
        return true;
    }

    // Backtracking solver (mengisi 0) — return true jika solved
    private boolean solveSudoku(int[][] grid) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (grid[row][col] == 0) {
                    for (int num = 1; num <= 9; num++) {
                        if (isSafe(grid, row, col, num)) {
                            grid[row][col] = num;
                            if (solveSudoku(grid)) {
                                return true;
                            }
                            grid[row][col] = 0;
                        }
                    }
                    return false; // tidak ada angka valid untuk posisi ini
                }
            }
        }
        return true; // semua terisi
    }

    // Buat solusi penuh secara acak: isi diagonal 3x3 lalu solve dengan backtracking
    private void generateFullSolution(int[][] grid) {
        // kosongkan grid
        for (int i = 0; i < 9; i++)
            for (int j = 0; j < 9; j++)
                grid[i][j] = 0;

        Random rand = new Random();

        // Isi diagonal 3x3 sehingga solver mendapat beberapa angka awal acak
        for (int blockStart = 0; blockStart < 9; blockStart += 3) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    int num;
                    do {
                        num = rand.nextInt(9) + 1;
                    } while (!isSafe(grid, blockStart + i, blockStart + j, num));
                    grid[blockStart + i][blockStart + j] = num;
                }
            }
        }

        // Selesaikan sisa menggunakan backtracking
        solveSudoku(grid);
    }

    // Copy fullSolution ke puzzle, lalu hapus 'blanks' sel secara acak
    private void createPuzzle(int[][] fullSolution, int[][] puzzle, int blanks) {
        // copy
        for (int i = 0; i < 9; i++)
            for (int j = 0; j < 9; j++)
                puzzle[i][j] = fullSolution[i][j];

        Random rand = new Random();
        while (blanks > 0) {
            int r = rand.nextInt(9);
            int c = rand.nextInt(9);
            if (puzzle[r][c] != 0) {
                puzzle[r][c] = 0;
                blanks--;
            }
        }
    }

    /**
     * generateSudoku sekarang:
     * - reset counters
     * - generateFullSolution(solution) menggunakan backtracking
     * - createPuzzle(solution, board, blanks)
     * - tampilkan board
     *
     * Saat dipanggil oleh reset (mistakeCount >= 3), ini menghasilkan puzzle baru
     * menggunakan backtracking.
     */
    private void generateSudoku(String difficulty) {
        mistakeCount = 0;
        hintUsed = false;
        elapsedSeconds = 0;

        timerLabel.setText("Waktu: 0 detik");
        mistakeLabel.setText("Kesalahan: 0/3");

        // pastikan timer dimulai ulang
        if (gameTimer.isRunning()) {
            gameTimer.stop();
        }
        gameTimer.start();

        int blanks;
        switch (difficulty) {
            case "Medium":
                blanks = 40;
                break;
            case "Hard":
                blanks = 55;
                break;
            default:
                blanks = 30;
        }

        // 1) Generate solusi penuh valid dengan backtracking
        generateFullSolution(solution);

        // 2) Generate puzzle dengan menghapus angka sesuai blanks (basic)
        createPuzzle(solution, board, blanks);

        // 3) Tampilkan puzzle di GUI
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] == 0) {
                    cells[i][j].setText("");
                    cells[i][j].setEditable(true);
                    cells[i][j].setForeground(Color.BLACK);
                    cells[i][j].setBackground(Color.WHITE);
                } else {
                    cells[i][j].setText(String.valueOf(board[i][j]));
                    cells[i][j].setEditable(false);
                    cells[i][j].setForeground(Color.DARK_GRAY);
                    cells[i][j].setBackground(new Color(235, 235, 235));
                }
            }
        }
    }

    private boolean checkWin() {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] == 0 || board[i][j] != solution[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SudokuGame().setVisible(true));
    }
}
