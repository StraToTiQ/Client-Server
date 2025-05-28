// File: src/main/java/com/bank/server/BankServer.java
package com.bank.server; // Zgodnie z uproszczoną strukturą

// Nie importujemy już com.bank.server.commands, bo logika jest w ClientHandler

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class BankServer {
    private static final Logger logger = Logger.getLogger(BankServer.class.getName());
    private static DBManager dbManager;
    private static ExecutorService clientExecutor;

    public static void main(String[] args) {
        setupLogger(); // Ustaw logowanie jako pierwsze

        logger.info("BankServer application starting...");

        Properties config = loadConfiguration();
        if (config == null) return; // loadConfiguration loguje i kończy w razie krytycznego błędu

        if (!initializeDBManager(config)) return; // initializeDBManager loguje i kończy

        addShutdownHook(); // Ustaw hook do czyszczenia zasobów

        int port = Integer.parseInt(config.getProperty("server.port", "5000"));
        // Używamy puli wątków, która dynamicznie dostosowuje liczbę wątków
        clientExecutor = Executors.newCachedThreadPool();

        logger.info("Attempting to start server on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("BankServer started successfully on port " + port + ". Waiting for client connections...");

            // Pętla akceptująca połączenia, dopóki serwer nie zostanie przerwany lub gniazdo zamknięte
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Blokuje do momentu połączenia
                    logger.info("Accepted connection from: " + clientSocket.getRemoteSocketAddress());
                    // Przekaż współdzielony DBManager i logger do każdego nowego ClientHandler
                    // ClientHandler teraz zawiera więcej logiki, ale sposób tworzenia pozostaje ten sam
                    clientExecutor.submit(new ClientHandler(clientSocket, dbManager, logger));
                } catch (IOException e) {
                    if (serverSocket.isClosed() || Thread.currentThread().isInterrupted()) {
                        logger.info("Server socket closed or thread interrupted, stopping accept loop.");
                        break; // Wyjdź z pętli, jeśli gniazdo serwera jest zamknięte lub serwer jest zamykany
                    }
                    // Loguj niekrytyczny błąd podczas akceptacji, serwer kontynuuje pracę
                    logger.log(Level.WARNING, "Error accepting client connection: " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "FATAL: Could not start server on port " + port + ". " +
                    "Possible reasons: port already in use, insufficient permissions.", e);
            shutdownServerComponents(); // Spróbuj zamknąć zasoby w razie błędu startu
            System.exit(1); // Zakończ z powodu błędu krytycznego
        } finally {
            logger.info("BankServer main loop has exited.");
            // Hook zamykający zajmie się ostatecznym czyszczeniem, ale można spróbować również tutaj,
            // jeśli wyjście nie nastąpiło z powodu błędu krytycznego
            if (clientExecutor != null && !clientExecutor.isTerminated() && !clientExecutor.isShutdown()) {
                shutdownClientExecutor();
            }
            // dbManager jest zamykany przez hook lub jawnie w przypadku błędu
        }
        logger.info("BankServer application finished.");
    }

    private static void setupLogger() {
        logger.setUseParentHandlers(false); // Zapobiegaj podwójnemu logowaniu do konsoli
        logger.setLevel(Level.INFO);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.INFO);
        logger.addHandler(consoleHandler);

        try {
            FileHandler fileHandler = new FileHandler("BankServer.log", true); // Dopisywanie do istniejącego logu
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.INFO);
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not initialize file logger. File logging will be unavailable.", e);
        }
    }

    private static Properties loadConfiguration() {
        Properties config = new Properties();
        try (InputStream input = BankServer.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.severe("FATAL: Unable to find 'config.properties' in classpath. Server cannot start.");
                System.exit(1);
                return null; // Nie powinno być osiągnięte
            }
            config.load(input);
            logger.info("Configuration file 'config.properties' loaded successfully.");
            return config;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "FATAL: Error loading configuration file 'config.properties'. Server cannot start.", e);
            System.exit(1);
            return null; // Nie powinno być osiągnięte
        }
    }

    private static boolean initializeDBManager(Properties config) {
        try {
            String dbUrl = config.getProperty("db.url");
            String dbUser = config.getProperty("db.user");
            String dbPassword = config.getProperty("db.password");

            if (dbUrl == null || dbUser == null || dbPassword == null) {
                logger.severe("FATAL: Database configuration (url, user, or password) missing. Server cannot start.");
                System.exit(1);
                return false;
            }
            // Zakładamy, że DBManager w konstruktorze obsługuje Class.forName()
            dbManager = new DBManager(dbUrl, dbUser, dbPassword);
            logger.info("Database Manager initialized successfully.");
            return true;
        } catch (Exception e) { // Łapanie szerszych wyjątków z konstruktora DBManager (np. ClassNotFoundException)
            logger.log(Level.SEVERE, "FATAL: Failed to initialize DBManager. Server cannot start.", e);
            System.exit(1);
            return false;
        }
    }

    private static void shutdownClientExecutor() {
        if (clientExecutor != null && !clientExecutor.isShutdown()) {
            logger.info("Attempting to shut down client executor service...");
            clientExecutor.shutdown();
            try {
                if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Client executor did not terminate in 5 seconds, forcing shutdown...");
                    clientExecutor.shutdownNow();
                    if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.severe("Client executor did not terminate even after forcing.");
                    } else {
                        logger.info("Client executor forced shutdown complete.");
                    }
                } else {
                    logger.info("Client executor shut down gracefully.");
                }
            } catch (InterruptedException ie) {
                logger.warning("Shutdown of client executor was interrupted. Forcing now.");
                clientExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeDBManager() {
        if (dbManager != null) {
            try {
                dbManager.close(); // DBManager implementuje AutoCloseable
                logger.info("Database connection closed successfully via DBManager.close().");
            } catch (Exception e) { // Łapanie ogólnego Exception z dbManager.close()
                logger.log(Level.SEVERE, "Error closing database connection during shutdown.", e);
            }
        }
    }

    private static void shutdownServerComponents() {
        logger.info("Initiating shutdown of server components...");
        shutdownClientExecutor(); // Najpierw zakończ obsługę klientów
        closeDBManager();         // Następnie zamknij połączenie z bazą
        logger.info("Server components shutdown process completed.");
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered: Closing server resources...");
            shutdownServerComponents();
            logger.info("Server shutdown hook finished execution.");
        }, "BankServer-ShutdownHook"));
    }
}