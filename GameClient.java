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
 * GameClient: the networking half of one player's screen.
 *
 * It owns a single socket to the host and runs one background reader thread. Every
 * message the server sends is handed to a {@link Listener} callback; every request is
 * written straight to the socket under a small write lock. Nothing here touches Swing,
 * so the caller can safely run connect() off the Event Dispatch Thread and push the
 * callback results onto the UI with SwingUtilities.invokeLater().
 *
 * The message framing is the exact mirror of GameServer.Message (writeUTF/readInt/writeUTF),
 * so the two sides always agree on where one message ends and the next begins.
 */
public class GameClient {

    /** Receives everything the server sends. Implementations must be fast and must not block. */
    public interface Listener {
        void onMessage(String type, String[] args);

        void onDisconnected(String reason);
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int SOCKET_READ_TIMEOUT_MS = 0; // 0 = wait indefinitely for server messages

    private final String host;
    private final int port;
    private final Listener listener;
    private final Object writeLock = new Object();

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread readerThread;

    private volatile boolean connected;
    private volatile boolean closing;

    public GameClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Opens the socket and starts the reader thread. This blocks for at most
     * CONNECT_TIMEOUT_MS, so call it from a worker thread, never from the EDT.
     * Throws IOException (unknown host / refused / timed out) so the caller can explain it.
     */
    public void connect() throws IOException {
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            newSocket.setTcpNoDelay(true);
            newSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
            in = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
        } catch (IOException e) {
            try {
                newSocket.close();
            } catch (IOException ignored) {
                // nothing to clean up
            }
            throw e;
        }

        socket = newSocket;
        connected = true;
        closing = false;

        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "GameClient-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try {
            while (connected) {
                GameServer.Message message = GameServer.Message.readFrom(in);
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
        if (closing) {
            return;
        }
        closing = true;
        if (listener != null) {
            listener.onDisconnected(reason);
        }
    }

    /** Sends one protocol message. Thread-safe: many screens may call this at once. */
    public void send(String type, String... args) {
        if (!connected) {
            return;
        }
        synchronized (writeLock) {
            if (out == null) {
                return;
            }
            try {
                new GameServer.Message(type, args).writeTo(out);
            } catch (IOException e) {
                notifyDisconnected("Could not reach the host: " + e.getMessage());
            }
        }
    }

    // ===== Request shortcuts (thin wrappers so screens do not hard-code protocol strings) =====

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

    /**
     * Sends the questioner's secret. The exact count for the round is random (1-5) and is
     * enforced by the server, so this only sanity-checks the global bounds here.
     */
    public void submitSecret(List<String> shapes) {
        if (shapes == null || shapes.size() < GameServer.MIN_SHAPES || shapes.size() > GameServer.MAX_SHAPES) {
            return;
        }
        send(GameServer.SECRET, shapes.toArray(new String[0]));
    }

    /** Sends the full guess. Its count must equal the secret count the server announced. */
    public void submitGuess(List<String> shapes) {
        if (shapes == null || shapes.isEmpty() || shapes.size() > GameServer.MAX_SHAPES) {
            return;
        }
        send(GameServer.GUESS, shapes.toArray(new String[0]));
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
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // already closed
        }
    }
}
