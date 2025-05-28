// File: src/main/java/com/bank/server/ClientHandler.java
package com.bank.server; // Zgodnie z uproszczoną strukturą

import com.bank.common.Account;
import com.bank.common.Client;
import com.bank.common.Protocol; // Używamy naszej nowej, uproszczonej klasy Protocol

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final DBManager dbManager;
    private final Logger logger;

    private Client loggedInClient; // Stan sesji: zalogowany klient (zamiast ClientHandlerContext)

    public ClientHandler(Socket socket, DBManager dbManager, Logger logger) {
        this.socket = socket;
        this.dbManager = dbManager;
        this.logger = logger;
        this.loggedInClient = null; // Początkowo nikt nie jest zalogowany
    }

    @Override
    public void run() {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        logger.info("Handler thread started for client: " + clientAddress);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine;
            while ((requestLine = in.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                String logPrefix = (loggedInClient != null) ?
                        "[User:" + loggedInClient.getId() + "@" + clientAddress + "]" :
                        "[" + clientAddress + "]";
                logger.info(logPrefix + " Received: " + requestLine);

                String[] requestParts = Protocol.parseMessage(requestLine); // Użycie metody pomocniczej
                String response;

                if (requestParts.length == 0 || requestParts[0].isEmpty()) {
                    response = Protocol.buildMessage(Protocol.RES_ERROR, "EMPTY_COMMAND", "Empty command received.");
                    logger.warning(logPrefix + " Empty command received.");
                } else {
                    try {
                        response = processRequest(requestParts);
                    } catch (IllegalArgumentException e) { // Używamy IllegalArgumentException zamiast ParameterException
                        response = Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_TYPE_PARAM, e.getMessage().replace(Protocol.SEPARATOR, ","));
                        logger.log(Level.WARNING, logPrefix + " Parameter/Argument error: " + requestLine, e);
                    } catch (SQLException e) {
                        response = Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_TYPE_DB, "Database error: " + e.getMessage().replace(Protocol.SEPARATOR, ","));
                        logger.log(Level.SEVERE, logPrefix + " Database error: " + requestLine, e);
                    } catch (Exception e) { // Ogólny
                        response = Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_TYPE_UNEXPECTED, "Unexpected server error: " + e.getMessage().replace(Protocol.SEPARATOR, ","));
                        logger.log(Level.SEVERE, logPrefix + " Unexpected error: " + requestLine, e);
                    }
                }
                out.println(response);
                logger.info(logPrefix + " Sent: " + response);
            }
        } catch (SocketException e) {
            String msg = (e.getMessage() != null) ? e.getMessage().toLowerCase() : "";
            if (msg.contains("connection reset") || msg.contains("broken pipe") || msg.contains("socket closed")) {
                logger.info("[" + clientAddress + "] Client connection ended abruptly: " + e.getMessage());
            } else {
                logger.log(Level.WARNING, "[" + clientAddress + "] SocketException: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[" + clientAddress + "] IOException: " + e.getMessage(), e);
        } finally {
            logger.info("Handler thread finished for client: " + clientAddress +
                    (loggedInClient != null ? " (User: " + loggedInClient.getId() + ")" : " (Not logged in)"));
            loggedInClient = null; // Wyczyść stan sesji
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing socket for " + clientAddress, e);
            }
        }
    }

    // Główna metoda przetwarzająca żądanie - zastępuje CommandDispatcher i indywidualne Command.execute()
    private String processRequest(String[] parts) throws SQLException, IllegalArgumentException {
        String command = parts[0];

        // Sprawdzenie autoryzacji dla większości komend
        if (loggedInClient == null &&
                !command.equals(Protocol.CMD_LOGIN) &&
                !command.equals(Protocol.CMD_ADMIN)) {
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_AUTH_REQUIRED, "Please login first.");
        }

        switch (command) {
            // Komendy klienta
            case Protocol.CMD_LOGIN:    return handleLogin(parts);
            case Protocol.CMD_LOGOUT:   return handleLogout();
            case Protocol.CMD_BALANCE:  return handleBalance(parts);
            case Protocol.CMD_DEPOSIT:  return handleDeposit(parts);
            case Protocol.CMD_WITHDRAW: return handleWithdraw(parts);
            case Protocol.CMD_TRANSFER: return handleTransfer(parts);
            case Protocol.CMD_LIST_MY_ACCOUNTS: return handleListMyAccounts();

            // Komendy admina (dispatch na podstawie drugiego tokenu)
            case Protocol.CMD_ADMIN:
                if (parts.length < 2 || parts[1].isEmpty()) {
                    return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_UNKNOWN_ADMIN_SUBCOMMAND, "Missing admin subcommand.");
                }
                String adminSubCommand = parts[1];
                switch (adminSubCommand) {
                    case Protocol.SUB_CMD_ADD_CLIENT:                return adminAddClient(parts);
                    case Protocol.SUB_CMD_ADD_ACCOUNT_TO_CLIENT:     return adminAddAccountToClient(parts);
                    case Protocol.SUB_CMD_GET_CLIENT_INFO_BY_ID:     return adminGetClientInfoById(parts);
                    case Protocol.SUB_CMD_GET_ACCOUNT_DETAILS:       return adminGetAccountDetails(parts);
                    case Protocol.SUB_CMD_UPDATE_CLIENT_INFO_BY_ID:  return adminUpdateClientInfoById(parts);
                    case Protocol.SUB_CMD_DELETE_CLIENT:             return adminDeleteClient(parts);
                    case Protocol.SUB_CMD_DELETE_ACCOUNT:            return adminDeleteAccount(parts);
                    default:
                        logger.warning("Unknown admin subcommand: " + adminSubCommand);
                        return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_UNKNOWN_ADMIN_SUBCOMMAND, "Unknown admin action: " + adminSubCommand);
                }
            default:
                logger.warning("Unknown command: " + command);
                return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_UNKNOWN_COMMAND, "Unknown command: " + command);
        }
    }

    // --- Metody pomocnicze do parsowania parametrów (uproszczony RequestParser) ---
    private String getRequiredPart(String[] parts, int index, String fieldName, String commandName) throws IllegalArgumentException {
        if (index < 0 || parts.length <= index || parts[index] == null || parts[index].trim().isEmpty()) {
            throw new IllegalArgumentException(commandName + ": Missing required parameter '" + fieldName + "' at index " + index + ".");
        }
        return parts[index].trim();
    }

    private int getRequiredInt(String[] parts, int index, String fieldName, String commandName) throws IllegalArgumentException {
        String strVal = getRequiredPart(parts, index, fieldName, commandName);
        try {
            return Integer.parseInt(strVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(commandName + ": Invalid integer for '" + fieldName + "' (value: " + strVal + ").");
        }
    }

    private BigDecimal getRequiredBigDecimal(String[] parts, int index, String fieldName, String commandName) throws IllegalArgumentException {
        String strVal = getRequiredPart(parts, index, fieldName, commandName);
        try {
            return new BigDecimal(strVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(commandName + ": Invalid decimal for '" + fieldName + "' (value: " + strVal + ").");
        }
    }

    private BigDecimal getRequiredPositiveBigDecimal(String[] parts, int index, String fieldName, String commandName) throws IllegalArgumentException {
        BigDecimal val = getRequiredBigDecimal(parts, index, fieldName, commandName);
        if (val.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(commandName + ": Parameter '" + fieldName + "' must be positive (value: " + val + ").");
        }
        return val;
    }

    private String getRequiredPesel(String[] parts, int index, String fieldName, String commandName) throws IllegalArgumentException {
        String pesel = getRequiredPart(parts, index, fieldName, commandName);
        if (!pesel.matches("\\d{11}")) {
            throw new IllegalArgumentException(commandName + ": Invalid PESEL for '" + fieldName + "'. Must be 11 digits.");
        }
        return pesel;
    }

    private String getRequiredAccountNumber(String[] parts, int index, String fieldName, String commandName) throws IllegalArgumentException {
        String accNum = getRequiredPart(parts, index, fieldName, commandName).toUpperCase();
        if (!accNum.matches("^PL\\d{26}$")) {
            throw new IllegalArgumentException(commandName + ": Invalid Account Number for '" + fieldName + "'. Expected PL + 26 digits.");
        }
        return accNum;
    }

    // --- Implementacje logiki poleceń ---

    // LOGIN;clientId;password
    private String handleLogin(String[] parts) throws SQLException, IllegalArgumentException {
        if (loggedInClient != null) {
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_ALREADY_LOGGED_IN, "Client ID " + loggedInClient.getId());
        }
        int clientId = getRequiredInt(parts, 1, "clientId", Protocol.CMD_LOGIN);
        String password = getRequiredPart(parts, 2, "password", Protocol.CMD_LOGIN);

        Optional<Client> clientOpt = dbManager.authenticateClient(clientId, password);
        if (clientOpt.isPresent()) {
            loggedInClient = clientOpt.get();
            logger.info("Client ID " + loggedInClient.getId() + " successfully logged in.");
            return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_LOGIN_SUCCESSFUL, loggedInClient.getFirstName(), String.valueOf(loggedInClient.getId()));
        } else {
            logger.warning("Login failed for client ID " + clientId);
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_LOGIN_FAILED, "Invalid Client ID or password.");
        }
    }

    // LOGOUT
    private String handleLogout() {
        if (loggedInClient == null) return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_NOT_LOGGED_IN);
        logger.info("Client ID " + loggedInClient.getId() + " logged out.");
        String clientName = loggedInClient.getFirstName();
        loggedInClient = null;
        return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_LOGOUT_SUCCESSFUL, "Goodbye " + clientName + "!");
    }

    // BALANCE;accountNumber
    private String handleBalance(String[] parts) throws SQLException, IllegalArgumentException {
        String accNum = getRequiredAccountNumber(parts, 1, "accountNumber", Protocol.CMD_BALANCE);
        Optional<Account> accOpt = dbManager.findAccountByNumber(accNum);
        if (!accOpt.isPresent()) return Protocol.ERR_ACCOUNT_NOT_FOUND;
        Account acc = accOpt.get();
        if (acc.getClientId() != loggedInClient.getId()) {
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_ACCESS_DENIED, "Account does not belong to you.");
        }
        return Protocol.buildMessage(Protocol.RES_BALANCE_IS, acc.getBalance().toPlainString());
    }

    // DEPOSIT;accountNumber;amount
    private String handleDeposit(String[] parts) throws SQLException, IllegalArgumentException {
        String accNum = getRequiredAccountNumber(parts, 1, "accountNumber", Protocol.CMD_DEPOSIT);
        BigDecimal amount = getRequiredPositiveBigDecimal(parts, 2, "amount", Protocol.CMD_DEPOSIT);
        Optional<Account> accOpt = dbManager.findAccountByNumber(accNum);
        if (!accOpt.isPresent()) return Protocol.ERR_ACCOUNT_NOT_FOUND;
        Account acc = accOpt.get();
        // W tej wersji pozwalamy na wpłatę na dowolne konto, jeśli klient jest zalogowany
        BigDecimal newBalance = acc.getBalance().add(amount);
        dbManager.updateAccountBalance(accNum, newBalance);
        logger.info("User " + loggedInClient.getId() + " deposited " + amount + " to " + accNum);
        return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_DEPOSIT_SUCCESSFUL, newBalance.toPlainString());
    }

    // WITHDRAW;accountNumber;amount
    private String handleWithdraw(String[] parts) throws SQLException, IllegalArgumentException {
        String accNum = getRequiredAccountNumber(parts, 1, "accountNumber", Protocol.CMD_WITHDRAW);
        BigDecimal amount = getRequiredPositiveBigDecimal(parts, 2, "amount", Protocol.CMD_WITHDRAW);
        Optional<Account> accOpt = dbManager.findAccountByNumber(accNum);
        if (!accOpt.isPresent()) return Protocol.ERR_ACCOUNT_NOT_FOUND;
        Account acc = accOpt.get();
        if (acc.getClientId() != loggedInClient.getId()) {
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_ACCESS_DENIED, "Account does not belong to you.");
        }
        if (acc.getBalance().compareTo(amount) < 0) return Protocol.ERR_INSUFFICIENT_FUNDS;
        BigDecimal newBalance = acc.getBalance().subtract(amount);
        dbManager.updateAccountBalance(accNum, newBalance);
        logger.info("User " + loggedInClient.getId() + " withdrew " + amount + " from " + accNum);
        return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_WITHDRAWAL_SUCCESSFUL, newBalance.toPlainString());
    }

    // TRANSFER;fromAccountNumber;toAccountNumber;amount
    private String handleTransfer(String[] parts) throws SQLException, IllegalArgumentException {
        String fromAccNum = getRequiredAccountNumber(parts, 1, "fromAccountNumber", Protocol.CMD_TRANSFER);
        String toAccNum = getRequiredAccountNumber(parts, 2, "toAccountNumber", Protocol.CMD_TRANSFER);
        BigDecimal amount = getRequiredPositiveBigDecimal(parts, 3, "amount", Protocol.CMD_TRANSFER);

        if (fromAccNum.equals(toAccNum)) throw new IllegalArgumentException("TRANSFER: Cannot transfer to the same account.");

        Optional<Account> fromAccOpt = dbManager.findAccountByNumber(fromAccNum);
        Optional<Account> toAccOpt = dbManager.findAccountByNumber(toAccNum);

        if (!fromAccOpt.isPresent()) return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_SOURCE_ACCOUNT_NOT_FOUND);
        if (!toAccOpt.isPresent()) return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_DESTINATION_ACCOUNT_NOT_FOUND);

        Account fromAcc = fromAccOpt.get();
        if (fromAcc.getClientId() != loggedInClient.getId()) {
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_ACCESS_DENIED, "Source account does not belong to you.");
        }
        if (fromAcc.getBalance().compareTo(amount) < 0) return Protocol.ERR_INSUFFICIENT_FUNDS;

        dbManager.executeTransferTransaction(fromAcc, toAccOpt.get(), amount);
        logger.info("User " + loggedInClient.getId() + " transferred " + amount + " from " + fromAccNum + " to " + toAccNum);
        return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_TRANSFER_SUCCESSFUL);
    }

    // LIST_MY_ACCOUNTS
    private String handleListMyAccounts() throws SQLException {
        List<Account> accounts = dbManager.findAllAccountsByClientId(loggedInClient.getId());
        if (accounts.isEmpty()) {
            return Protocol.buildMessage(Protocol.RES_INFO, Protocol.INFO_NO_ACCOUNTS_FOUND, "You have no active accounts.");
        }
        String accString = accounts.stream()
                .map(a -> a.getAccountNumber() + ":" + a.getBalance().toPlainString())
                .collect(Collectors.joining(","));
        return Protocol.buildMessage(Protocol.RES_MY_ACCOUNTS, accString);
    }

    // --- Implementacje logiki poleceń Admina ---
    // ADMIN;ADD_CLIENT;firstName;lastName;pesel;password
    private String adminAddClient(String[] parts) throws SQLException, IllegalArgumentException {
        String fn = getRequiredPart(parts, 2, "firstName", Protocol.SUB_CMD_ADD_CLIENT);
        String ln = getRequiredPart(parts, 3, "lastName", Protocol.SUB_CMD_ADD_CLIENT);
        String pesel = getRequiredPesel(parts, 4, "pesel", Protocol.SUB_CMD_ADD_CLIENT);
        String pass = getRequiredPart(parts, 5, "password", Protocol.SUB_CMD_ADD_CLIENT);

        if (dbManager.findClientByPesel(pesel).isPresent()) {
            return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_PESEL_EXISTS, "Client with this PESEL already exists.");
        }
        int newClientId = dbManager.addClient(fn, ln, pesel, pass);
        Account firstAcc = dbManager.addAccountToClient(newClientId, BigDecimal.ZERO);
        logger.info("Admin added client ID " + newClientId + " with account " + firstAcc.getAccountNumber());
        return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_CLIENT_ADDED, String.valueOf(newClientId), firstAcc.getAccountNumber());
    }

    // ADMIN;ADD_ACCOUNT_TO_CLIENT;clientId;initialBalance
    private String adminAddAccountToClient(String[] parts) throws SQLException, IllegalArgumentException {
        int clientId = getRequiredInt(parts, 2, "clientId", Protocol.SUB_CMD_ADD_ACCOUNT_TO_CLIENT);
        BigDecimal balance = getRequiredBigDecimal(parts, 3, "initialBalance", Protocol.SUB_CMD_ADD_ACCOUNT_TO_CLIENT);
        if (balance.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Initial balance cannot be negative.");
        if (!dbManager.findClientById(clientId).isPresent()) return Protocol.ERR_CLIENT_NOT_FOUND;

        Account newAcc = dbManager.addAccountToClient(clientId, balance);
        logger.info("Admin added account " + newAcc.getAccountNumber() + " for client " + clientId);
        return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_ACCOUNT_ADDED_TO_CLIENT, newAcc.getAccountNumber());
    }

    // ADMIN;GET_CLIENT_INFO_BY_ID;clientId
    private String adminGetClientInfoById(String[] parts) throws SQLException, IllegalArgumentException {
        int clientId = getRequiredInt(parts, 2, "clientId", Protocol.SUB_CMD_GET_CLIENT_INFO_BY_ID);
        Optional<Client> clientOpt = dbManager.findClientById(clientId);
        if (!clientOpt.isPresent()) return Protocol.ERR_CLIENT_NOT_FOUND;
        Client c = clientOpt.get();
        List<Account> accounts = dbManager.findAllAccountsByClientId(c.getId());
        String accStr = accounts.isEmpty() ? Protocol.NO_ACCOUNTS_MARKER :
                accounts.stream().map(a->a.getAccountNumber()+":"+a.getBalance().toPlainString()).collect(Collectors.joining(","));
        return Protocol.buildMessage(Protocol.RES_CLIENT_INFO, String.valueOf(c.getId()), c.getFirstName(), c.getLastName(), c.getPesel(), accStr);
    }

    // ADMIN;GET_ACCOUNT_DETAILS;accountNumber
    private String adminGetAccountDetails(String[] parts) throws SQLException, IllegalArgumentException {
        String accNum = getRequiredAccountNumber(parts, 2, "accountNumber", Protocol.SUB_CMD_GET_ACCOUNT_DETAILS);
        Optional<Account> accOpt = dbManager.findAccountByNumber(accNum);
        if (!accOpt.isPresent()) return Protocol.ERR_ACCOUNT_NOT_FOUND;
        Account acc = accOpt.get();
        Client owner = dbManager.findClientById(acc.getClientId()).orElse(new Client(acc.getClientId(),"N/A","N/A","N/A",null));
        return Protocol.buildMessage(Protocol.RES_ACCOUNT_DETAILS, String.valueOf(acc.getId()), acc.getAccountNumber(),
                acc.getBalance().toPlainString(), String.valueOf(owner.getId()), owner.getFirstName(),
                owner.getLastName(), owner.getPesel());
    }

    // ADMIN;UPDATE_CLIENT_INFO_BY_ID;clientId;newFirstName;newLastName;newPesel
    private String adminUpdateClientInfoById(String[] parts) throws SQLException, IllegalArgumentException {
        int clientId = getRequiredInt(parts, 2, "clientId", Protocol.SUB_CMD_UPDATE_CLIENT_INFO_BY_ID);
        String fn = getRequiredPart(parts, 3, "newFirstName", Protocol.SUB_CMD_UPDATE_CLIENT_INFO_BY_ID);
        String ln = getRequiredPart(parts, 4, "newLastName", Protocol.SUB_CMD_UPDATE_CLIENT_INFO_BY_ID);
        String pesel = getRequiredPesel(parts, 5, "newPesel", Protocol.SUB_CMD_UPDATE_CLIENT_INFO_BY_ID);

        Optional<Client> currentOpt = dbManager.findClientById(clientId);
        if(!currentOpt.isPresent()) return Protocol.ERR_CLIENT_NOT_FOUND;
        if(!currentOpt.get().getPesel().equals(pesel)){ // PESEL is changing
            Optional<Client> existingWithNewPesel = dbManager.findClientByPesel(pesel);
            if(existingWithNewPesel.isPresent() && existingWithNewPesel.get().getId() != clientId){
                return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_PESEL_EXISTS_OTHER, "New PESEL belongs to another client.");
            }
        }
        boolean updated = dbManager.updateClientInfo(clientId, fn, ln, pesel);
        if(updated){
            logger.info("Admin updated info for client " + clientId);
            return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_CLIENT_INFO_UPDATED);
        }
        return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_UPDATE_FAILED_GENERIC, "Update failed or no changes made.");
    }

    // ADMIN;DELETE_CLIENT;clientId
    private String adminDeleteClient(String[] parts) throws SQLException, IllegalArgumentException {
        int clientId = getRequiredInt(parts, 2, "clientId", Protocol.SUB_CMD_DELETE_CLIENT);
        if (dbManager.deleteClientById(clientId)) {
            logger.info("Admin deleted client " + clientId);
            return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_CLIENT_DELETED, String.valueOf(clientId));
        }
        return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_CLIENT_DELETION_FAILED, String.valueOf(clientId));
    }

    // ADMIN;DELETE_ACCOUNT;accountNumber
    private String adminDeleteAccount(String[] parts) throws SQLException, IllegalArgumentException {
        String accNum = getRequiredAccountNumber(parts, 2, "accountNumber", Protocol.SUB_CMD_DELETE_ACCOUNT);
        if (dbManager.deleteAccountByNumber(accNum)) {
            logger.info("Admin deleted account " + accNum);
            return Protocol.buildMessage(Protocol.RES_OK, Protocol.OK_ACCOUNT_DELETED, accNum);
        }
        return Protocol.buildMessage(Protocol.RES_ERROR, Protocol.ERR_ACCOUNT_DELETION_FAILED, accNum);
    }
}