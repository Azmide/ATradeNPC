package io.github.AzmiiD.aTradeNpc.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Appends one line per transaction to data/transactions.log (written off the main thread). */
public class TransactionLogger {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Logger logger;
    private final File file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATradeNPC-log");
        t.setDaemon(true);
        return t;
    });
    private boolean enabled;

    public TransactionLogger(Logger logger, File dataFolder) {
        this.logger = logger;
        this.file = new File(dataFolder, "data/transactions.log");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String message) {
        if (!enabled) {
            return;
        }
        String line = "[" + LocalDateTime.now().format(FORMAT) + "] " + message + System.lineSeparator();
        executor.execute(() -> {
            try {
                file.getParentFile().mkdirs();
                Files.writeString(file.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not write transaction log", e);
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
