import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

/**
 * GameClient: the networking half of one player's screen. It owns a single socket to the host and one
 * background reader thread that hands every server message to a {@link Listener}. Nothing here touches
 * Swing, so callers may run connect() off the event thread.
 */
public class GameClient {

    /** Receives everything the server sends. Implementations must be fast and must not block. */
    public interface Listener {
        void onMessage(String type, String[] args);

        void onDisconnected(String reason);
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String host;
    private final int port;
    private final Listener listener;
    private final Object writeLock = new Object();

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected;
    private volatile boolean closing;

    public GameClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    /** Opens the socket and starts the reader thread; blocks for at most CONNECT_TIMEOUT_MS. */
    public void connect() throws IOException {
        var newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            newSocket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
        } catch (IOException e) {
            closeQuietly(newSocket);
            throw e;   // unknown host, refused or timed out: the caller explains it
        }
        socket = newSocket;
        connected = true;
        closing = false;

        var reader = new Thread(this::readLoop, "GameClient-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Reads whole messages off the socket until the connection ends, then reports it once. */
    private void readLoop() {
        try {
            while (connected) {
                var message = GameServer.Message.readFrom(in);
                if (listener != null) {
                    listener.onMessage(message.type, message.args);
                }
            }
        } catch (EOFException e) {
            notifyDisconnected("The host closed the connection.");
        } catch (SocketException e) {
            if (!closing) {
                notifyDisconnected("Connection to the host was lost.");
            }
        } catch (IOException e) {
            if (!closing) {
                notifyDisconnected("Connection error: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    private void notifyDisconnected(String reason) {
        if (!closing) {
            closing = true;
            if (listener != null) {
                listener.onDisconnected(reason);
            }
        }
    }

    /** Sends one protocol message; thread-safe, because several screens may call it at once. */
    public void send(String type, String... args) {
        if (!connected || out == null) {
            return;
        }
        synchronized (writeLock) {
            try {
                new GameServer.Message(type, args).writeTo(out);
            } catch (IOException e) {
                notifyDisconnected("Could not reach the host: " + e.getMessage());
            }
        }
    }

    // ===== Request shortcuts, so the screens never spell out a protocol string =====

    public void join(String playerName) {
        send(GameServer.JOIN, playerName);
    }

    public void setReady(boolean ready) {
        send(GameServer.READY, ready ? "1" : "0");
    }

    public void requestStartGame() {
        send(GameServer.START_GAME);
    }

    public void requestNewGame() {
        send(GameServer.NEW_GAME);
    }

    /** Sends the questioner's secret; the exact count was drawn by the server for this round. */
    public void submitSecret(List<String> shapes) {
        if (shapes != null && shapes.size() >= GameServer.MIN_SHAPES && shapes.size() <= GameServer.MAX_SHAPES) {
            send(GameServer.SECRET, shapes.toArray(new String[0]));
        }
    }

    /** Sends the full guess; its count must equal the count the server announced. */
    public void submitGuess(List<String> shapes) {
        if (shapes != null && !shapes.isEmpty() && shapes.size() <= GameServer.MAX_SHAPES) {
            send(GameServer.GUESS, shapes.toArray(new String[0]));
        }
    }

    public void sendChat(String text) {
        send(GameServer.CHAT, text);
    }

    /** Tells the server we are leaving, then drops the socket. Safe to call twice. */
    public void leave() {
        closing = true;
        if (connected) {
            send(GameServer.LEAVE_ROOM);
        }
        close();
    }

    /** Closes the socket without waiting for a reply. Safe to call from any thread. */
    public void close() {
        closing = true;
        connected = false;
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // already closed
        }
    }
}
