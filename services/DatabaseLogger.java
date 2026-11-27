package services;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records everything that happens in the simulation into a MySQL table (JDBC).
 *
 * Design:
 *  - Workers call log(...), which only puts the event on a BlockingQueue (fast, never
 *    touches the database), so the 20 worker threads never wait for MySQL.
 *  - ONE writer thread (a single-thread ExecutorService) takes events from the queue and
 *    inserts them in batches with a PreparedStatement. A JDBC Connection is not
 *    thread-safe, so only this thread ever uses it.
 *  - If the database is unavailable, the logger switches itself off and the simulation
 *    carries on normally.
 */
public class DatabaseLogger implements AutoCloseable {

    private static final String CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS hospital_events ("
        + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
        + " run_id VARCHAR(30) NOT NULL,"
        + " event_time DATETIME(3) NOT NULL,"
        + " worker_type VARCHAR(30) NOT NULL,"
        + " worker_name VARCHAR(50) NOT NULL,"
        + " patient_id VARCHAR(20) NULL,"
        + " action VARCHAR(40) NOT NULL,"
        + " details VARCHAR(255) NULL,"
        + " INDEX idx_run (run_id))";

    private static final String INSERT =
        "INSERT INTO hospital_events "
        + "(run_id, event_time, worker_type, worker_name, patient_id, action, details) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 50;
    private static final int QUEUE_CAPACITY = 10_000;

    // Shared do-nothing instance: workers use it when no database is configured.
    private static final DatabaseLogger DISABLED = new DatabaseLogger();

    private static class Event {
        final LocalDateTime time = LocalDateTime.now();
        final String workerType, workerName, patientId, action, details;

        Event(String workerType, String workerName, String patientId,
              String action, String details) {
            this.workerType = workerType;
            this.workerName = workerName;
            this.patientId = patientId;
            this.action = action;
            this.details = details;
        }
    }

    // Poison pill that tells the writer thread to finish.
    private static final Event STOP = new Event("", "", null, "", null);

    private final boolean enabled;
    private final String url, user, password;
    private final String runId;
    private final BlockingQueue<Event> queue;
    private final ExecutorService writer;
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger inserted = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();
    private volatile boolean closed = false;
    private boolean errorShown = false;       // only touched by the writer thread
    private Connection connection;            // only touched by the writer thread

    /** Disabled logger. */
    private DatabaseLogger() {
        this.enabled = false;
        this.url = null;
        this.user = null;
        this.password = null;
        this.runId = "NONE";
        this.queue = null;
        this.writer = null;
    }

    private DatabaseLogger(String url, String user, String password, Connection first) {
        this.enabled = true;
        this.url = url;
        this.user = user;
        this.password = password;
        this.connection = first;
        this.runId = "RUN-" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        this.queue = new LinkedBlockingQueue<Event>(QUEUE_CAPACITY);
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "database-writer");
            t.setDaemon(true); // never keeps the JVM alive if something goes wrong
            return t;
        });
        this.writer.submit(this::writeLoop);
    }

    public static DatabaseLogger disabled() {
        return DISABLED;
    }

    /**
     * Connects to MySQL. If anything goes wrong (MySQL not running, wrong password,
     * driver jar missing, database not created) it prints the reason and returns a
     * disabled logger, so the simulation still runs.
     */
    public static DatabaseLogger connect(String url, String user, String password) {
        try {
            Connection con = DriverManager.getConnection(url, user, password);
            try (Statement st = con.createStatement()) {
                st.execute(CREATE_TABLE);
                con.setAutoCommit(false);
            } catch (SQLException e) {
                closeQuietly(con);
                throw e;
            }
            System.out.println("[DB] Connected. Events will be saved to table hospital_events.");
            return new DatabaseLogger(url, user, password, con);
        } catch (SQLException e) {
            System.out.println("[DB] WARNING: database not available, running WITHOUT saving ("
                + e.getMessage() + ")");
            System.out.println("[DB] Check: MySQL running? database/hospital_db.sql executed? "
                + "connector .jar on the classpath? user/password correct?");
            return DISABLED;
        }
    }

    public boolean isEnabled() { return enabled; }

    public String getRunId() { return runId; }

    /** Called by the workers. Never blocks for long and never throws. */
    public void log(String workerType, String workerName, Object patientId,
                    String action, String details) {
        if (!enabled || closed) {
            return;
        }
        Event e = new Event(workerType, workerName,
            patientId == null ? null : String.valueOf(patientId),
            action, cut(details, 255));
        try {
            if (queue.offer(e, 1, TimeUnit.SECONDS)) {
                queued.incrementAndGet();
            } else {
                dropped.incrementAndGet();  // queue full for a whole second: skip the event
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            dropped.incrementAndGet();
        }
    }

    // ---------------- writer thread ----------------

    private void writeLoop() {
        List<Event> batch = new ArrayList<Event>();
        boolean stop = false;
        while (!stop) {
            try {
                Event first = queue.take();
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);
            } catch (InterruptedException e) {
                // Interrupted: write what is still waiting, then finish.
                queue.drainTo(batch);
                batch.add(STOP);
            }
            if (batch.remove(STOP)) {
                stop = true;
                queue.drainTo(batch); // anything queued behind the pill
                batch.remove(STOP);
            }
            if (!batch.isEmpty()) {
                flush(batch);
                batch.clear();
            }
        }
    }

    /** Inserts one batch in a single transaction. On failure it reconnects once and retries. */
    private void flush(List<Event> batch) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                    reconnect();
                }
                try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
                    for (Event e : batch) {
                        ps.setString(1, runId);
                        ps.setObject(2, e.time);
                        ps.setString(3, e.workerType);
                        ps.setString(4, e.workerName);
                        ps.setString(5, e.patientId);
                        ps.setString(6, e.action);
                        ps.setString(7, e.details);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
                inserted.addAndGet(batch.size());
                return;
            } catch (SQLException ex) {
                rollbackQuietly();
                if (attempt == 2) {
                    failed.addAndGet(batch.size());
                    if (!errorShown) {
                        errorShown = true;
                        String why = ex instanceof BatchUpdateException && ex.getNextException() != null
                            ? ex.getNextException().getMessage() : ex.getMessage();
                        System.out.println("[DB] ERROR: could not save events (" + why
                            + "). The simulation continues.");
                    }
                }
            }
        }
    }

    private void reconnect() throws SQLException {
        closeQuietly(connection);
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
    }

    private void rollbackQuietly() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
            // nothing more we can do
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (SQLException ignored) {
            // nothing more we can do
        }
    }

    // ---------------- finishing and checking ----------------

    /** Waits (max 15 s) until every queued event has been written, then closes the connection. */
    @Override
    public void close() {
        if (!enabled || closed) {
            return;
        }
        closed = true; // later log() calls are ignored
        try {
            queue.offer(STOP, 5, TimeUnit.SECONDS);
            writer.shutdown();
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                System.out.println("[DB] WARNING: writer did not finish in time.");
            }
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(connection);
        }
    }

    public int getQueued()   { return queued.get(); }
    public int getInserted() { return inserted.get(); }
    public int getFailed()   { return failed.get(); }
    public int getDropped()  { return dropped.get(); }

    /** Counts this run's rows with a SELECT on a fresh connection (-1 if that fails). */
    public int countRowsInDatabase() {
        if (!enabled) {
            return -1;
        }
        String sql = "SELECT COUNT(*) FROM hospital_events WHERE run_id = ?";
        try (Connection con = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            return -1;
        }
    }

    /** Reads this run's rows back from MySQL (SELECT + ResultSet) and prints a summary. */
    public void printRunSummary() {
        if (!enabled) {
            return;
        }
        String summarySql = "SELECT worker_type, action, COUNT(*) AS total "
            + "FROM hospital_events WHERE run_id = ? "
            + "GROUP BY worker_type, action ORDER BY worker_type, action";
        String lastSql = "SELECT id, event_time, worker_name, patient_id, action "
            + "FROM hospital_events WHERE run_id = ? ORDER BY id DESC LIMIT 5";

        try (Connection con = DriverManager.getConnection(url, user, password)) {

            System.out.println("\n===== EVENTS READ BACK FROM MYSQL (" + runId + ") =====");
            try (PreparedStatement ps = con.prepareStatement(summarySql)) {
                ps.setString(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("%-15s %-22s %d%n",
                            rs.getString("worker_type"),
                            rs.getString("action"),
                            rs.getInt("total"));
                    }
                }
            }

            System.out.println("--- last 5 events saved ---");
            try (PreparedStatement ps = con.prepareStatement(lastSql)) {
                ps.setString(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("#" + rs.getLong("id") + "  "
                            + rs.getString("event_time") + "  "
                            + rs.getString("worker_name") + "  patient "
                            + rs.getString("patient_id") + "  "
                            + rs.getString("action"));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Could not read the events back: " + e.getMessage());
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
