import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JPanel;
import javax.swing.Timer;

public class GamePanel extends JPanel implements ActionListener, MouseListener {

    private ExecutorService exec;
    private Socket clientSocket;
    private DataInputStream fromServer;
    private DataOutputStream toServer;

    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 800;
    private static final int UNIT_SIZE = 15;
    private static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / UNIT_SIZE;
    private static final int DELAY = 55;

    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private int bodyParts = 3;
    private int applesEaten = 0;
    private String receivedMessage;

    private HashMap<String, Integer> scores = new HashMap<>();

    private int appleX;
    private int appleY;

    private char direction = 'R';
    private boolean running = false;
    private boolean started = false;
    private boolean paused = false;
    private boolean gameOver = false;
    private Timer timer = new Timer(DELAY, this);
    private Random random;

    public GamePanel(String host, int port) {
        random = new Random();
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter());
        this.addMouseListener(this);
        client(host, port);
        startGame();
    }

    private void client(String host, int port) {
        try {
            clientSocket = new Socket(host, port);
            fromServer = new DataInputStream(clientSocket.getInputStream());
            toServer = new DataOutputStream(clientSocket.getOutputStream());

            exec = Executors.newCachedThreadPool();
            exec.execute(new ClientSocket());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ClientSocket implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    receivedMessage = fromServer.readUTF();
                    topdata(receivedMessage);
                    System.out.println(receivedMessage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (clientSocket != null || started == false) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void startGame() {
        if (started && paused) {
            return;
        }
        newApple();
        running = true;
        timer.start();
    }

    public void newApple() {
        appleX = random.nextInt((int) (SCREEN_WIDTH / UNIT_SIZE)) * UNIT_SIZE;
        appleY = random.nextInt((int) (SCREEN_HEIGHT / UNIT_SIZE)) * UNIT_SIZE;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        if (running) {
            move();
            checkApple();
            checkCollisions();
        }
        repaint();
    }

    public class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    if (direction != 'R') {
                        direction = 'L';
                    }
                    break;

                case KeyEvent.VK_RIGHT:
                    if (direction != 'L') {
                        direction = 'R';
                    }
                    break;

                case KeyEvent.VK_UP:
                    if (direction != 'D') {
                        direction = 'U';
                    }
                    break;

                case KeyEvent.VK_DOWN:
                    if (direction != 'U') {
                        direction = 'D';
                    }
                    break;

                case KeyEvent.VK_SPACE:
                    if (paused) {
                        resume();
                    } else if (running) {
                        pause();
                    }
                    break;
                case KeyEvent.VK_ENTER:
                    if(!started) {
                        started = true;
                        startGame();
                    } else if (gameOver) {
                        started = false;
                        restartGame();
                    }
                    break;
            }
        }
    }

    public void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }
        switch (direction) {
            case 'U':
                y[0] = y[0] - UNIT_SIZE;
                break;
            case 'D':
                y[0] = y[0] + UNIT_SIZE;
                break;
            case 'L':
                x[0] = x[0] - UNIT_SIZE;
                break;
            case 'R':
                x[0] = x[0] + UNIT_SIZE;
                break;
        }
    }

    public void checkApple() {
        if ((x[0] == appleX) && (y[0] == appleY)) {
            try {
                bodyParts++;
                applesEaten++;
                toServer.writeUTF(applesEaten);
                newApple();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void checkCollisions() {
        for (int i = bodyParts; i > 0; i--) {
            if ((x[0] == x[i]) && (y[0] == y[i])) {
                running = false;
            }
        }
        if (x[0] < 0 || x[0] > SCREEN_WIDTH || y[0] < 0 || y[0] > SCREEN_HEIGHT) {
            running = false;
        }
        if (!running) {
            timer.stop();
            paused = false;
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    public void topdata(String receivedMessage) {
        String[] play_data = receivedMessage.split(":");
        scores.put(play_data[0], Integer.parseInt(play_data[1]));
    }

    public void draw(Graphics g) {

        if (!started) {
            g.setColor(Color.RED);
            g.setFont(new Font("SAN_SERIF", Font.BOLD, 30));
            FontMetrics metrics = getFontMetrics(g.getFont());
            g.drawString("[Enter] => 開始遊戲", (SCREEN_WIDTH - metrics.stringWidth("[Enter] => 開始遊戲")) / 2,
                    SCREEN_HEIGHT / 2);
            g.drawString("[Space] => 暫停遊戲", (SCREEN_WIDTH - metrics.stringWidth("[Space] => 暫停遊戲")) / 2,
                    (SCREEN_HEIGHT / 2) + 50);
        } else if (running) {
            g.setColor(Color.RED);
            g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

            for (int i = 0; i < bodyParts; i++) {
                if (i == 0) {
                    g.setColor(Color.BLUE);
                } else {
                    g.setColor(Color.GREEN);
                }
                g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
            }
            // Score text
            g.setColor(Color.CYAN);
            g.setFont(new Font("Ink Free", Font.BOLD, 30));
            FontMetrics metrics = getFontMetrics(g.getFont());
            int text_y = 0;
            for (String name : scores.keySet()) {
                int score = scores.get(name);
                g.drawString(name + score, (SCREEN_WIDTH - metrics.stringWidth(name + score)) / 2,
                        g.getFont().getSize() + text_y * 50);
                text_y += 1;
            }

            if (paused) {
                g.setFont(new Font("SAN_SERIF", Font.BOLD, 30));
                metrics = getFontMetrics(g.getFont());
                g.drawString("遊戲暫停中...", (SCREEN_WIDTH - metrics.stringWidth("遊戲暫停中...")) / 2, SCREEN_HEIGHT / 2);
            }
        } else {
            gameOver(g);
        }
    }

    public void gameOver(Graphics g) {
        g.setColor(Color.RED);
        g.setFont(new Font("SAN_SERIF", Font.BOLD, 45));
        FontMetrics metrics1 = getFontMetrics(g.getFont());
        g.drawString("Score: " + applesEaten,
                (SCREEN_WIDTH - metrics1.stringWidth("Score: " + applesEaten)) / 2, (SCREEN_HEIGHT / 2) + 50);

        g.setColor(Color.RED);
        g.setFont(new Font("SAN_SERIF", Font.BOLD, 75));
        FontMetrics metrics = getFontMetrics(g.getFont());
        g.drawString("遊戲結束", (SCREEN_WIDTH - metrics.stringWidth("遊戲結束")) / 2, SCREEN_HEIGHT / 2);

        g.setColor(Color.BLUE);
        g.setFont(new Font("SAN_SERIF", Font.BOLD, 45));
        FontMetrics metrics2 = getFontMetrics(g.getFont());
        g.drawString("按下 Enter 重新開始遊戲...",
                (SCREEN_WIDTH - metrics2.stringWidth("按下 Enter 重新開始遊戲....")) / 2, (SCREEN_HEIGHT / 2) + 100);

        gameOver = true;
    }

    public void restartGame() {
        running = false;
        started = false;
        paused = false;
        gameOver = false;
        bodyParts = 3;
        applesEaten = 0;
        receivedMessage = "";
        scores.clear();
        direction = 'R';
        timer.stop();
        random = new Random();

        for (int i = 0; i < bodyParts; i++) {
            x[i] = 0;
            y[i] = 0;
        }

        repaint();
        startGame();
    }

    public void pause() {
        paused = true;
        repaint();
        timer.stop();
    }

    public void resume() {
        paused = false;
        timer.start();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }
}