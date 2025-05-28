// File: src/main/java/com/bank/client/ClientApp.java
package com.bank.client; // Zgodnie z uproszczoną strukturą

import com.bank.common.Protocol; // Używamy naszej nowej klasy Protocol

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ClientApp {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static Scanner consoleIn; // Scanner do odczytu danych od użytkownika
    private static String loggedInUserFirstName = null;
    private static int loggedInUserId = -1;

    public static void main(String[] args) {
        System.out.println("Client Terminal (TK) - Initializing...");
        consoleIn = new Scanner(System.in);

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Successfully connected to Bank Server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

            // --- Krok Logowania ---
            if (!performLogin(serverOut, serverIn)) {
                System.out.println("Login failed. Exiting application.");
                return; // Zakończ, jeśli logowanie nie powiodło się
            }

            // --- Główna Pętla Aplikacji (po pomyślnym zalogowaniu) ---
            System.out.println("\nWelcome, " + loggedInUserFirstName + "! (Client ID: " + loggedInUserId + ")");

            String userInput;
            boolean running = true;
            while (running) {
                displayUserMenu();
                userInput = consoleIn.nextLine().trim().toUpperCase();
                String request = null;
                String serverResponse;

                if ("X".equals(userInput)) { // Obsługa wyjścia/wylogowania
                    request = Protocol.buildMessage(Protocol.CMD_LOGOUT);
                    System.out.println("Sending to server: " + request);
                    serverOut.println(request);
                    serverResponse = serverIn.readLine();
                    System.out.println("Server response: " + formatUserServerResponse(serverResponse));
                    System.out.println("Exiting Client Terminal...");
                    running = false;
                    continue;
                }

                switch (userInput) {
                    case "1": // Check Balance
                        request = handleCheckBalance();
                        break;
                    case "2": // Deposit Funds
                        request = handleDeposit();
                        break;
                    case "3": // Withdraw Funds
                        request = handleWithdraw();
                        break;
                    case "4": // Transfer Funds
                        request = handleTransfer();
                        break;
                    case "5": // List My Accounts
                        request = Protocol.buildMessage(Protocol.CMD_LIST_MY_ACCOUNTS); // Bez dodatkowych parametrów
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                        continue;
                }

                if (request != null) {
                    System.out.println("Sending to server: " + request);
                    serverOut.println(request);
                    serverResponse = serverIn.readLine();
                    System.out.println("Server response: " + formatUserServerResponse(serverResponse));
                } else if (running) {
                    System.out.println("Operation cancelled or no request generated.");
                }
                if (running) {
                    System.out.println("--------------------------------------------------");
                }
            }

        } catch (UnknownHostException e) {
            System.err.println("Error: Server not found at " + SERVER_ADDRESS + ":" + SERVER_PORT);
        } catch (IOException e) {
            System.err.println("Error: Could not connect or I/O error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred in ClientApp: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (consoleIn != null) {
                // consoleIn.close(); // Ostrożnie z zamykaniem System.in
            }
            System.out.println("Client Terminal has shut down.");
        }
    }

    // --- Metody pomocnicze do obsługi konsoli (uproszczony ConsoleHelper) ---
    private static String promptString(String message) {
        System.out.print(message);
        return consoleIn.nextLine().trim();
    }

    private static String promptClientId(String message) {
        String clientIdStr;
        while (true) {
            clientIdStr = promptString(message);
            if (clientIdStr.matches("\\d+")) return clientIdStr;
            System.out.println("Invalid Client ID format. Must be a number.");
        }
    }

    private static String promptPassword(String message) { // Hasło to po prostu niepusty string
        String pass;
        while (true) {
            pass = promptString(message);
            if (!pass.isEmpty()) return pass;
            System.out.println("Password cannot be empty.");
        }
    }

    private static String promptAccountNumber(String message) {
        String accNum;
        while (true) {
            accNum = promptString(message).toUpperCase();
            if (accNum.matches("^PL\\d{26}$")) return accNum;
            System.out.println("Invalid Account Number format (e.g., PL01234567890123456789012345).");
        }
    }

    private static String promptPositiveAmount(String message) {
        String amountStr;
        while (true) {
            amountStr = promptString(message);
            if (amountStr.matches("^\\d*\\.?\\d+$")) {
                try {
                    if (Double.parseDouble(amountStr) > 0) return amountStr;
                    else System.out.println("Amount must be positive and greater than zero.");
                } catch (NumberFormatException e) { System.out.println("Invalid amount format."); }
            } else {
                System.out.println("Invalid amount format (e.g., 100.50).");
            }
        }
    }

    // --- Logika Logowania ---
    private static boolean performLogin(PrintWriter serverOut, BufferedReader serverIn) throws IOException {
        System.out.println("\n--- Login Required ---");
        int attempts = 0;
        while (attempts < 3) { // 3 próby logowania
            String clientIdStr = promptClientId("Enter your Client ID: ");
            String password = promptPassword("Enter your Password: ");

            String loginRequest = Protocol.buildMessage(Protocol.CMD_LOGIN, clientIdStr, password);
            // System.out.println("Sending to server: " + loginRequest); // Opcjonalny log
            serverOut.println(loginRequest);
            String response = serverIn.readLine();
            // System.out.println("Raw login response: " + response); // Opcjonalny log

            if (response != null) {
                String[] parts = Protocol.parseMessage(response);
                if (parts.length > 1 && Protocol.RES_OK.equals(parts[0]) && Protocol.OK_LOGIN_SUCCESSFUL.equals(parts[1])) {
                    if (parts.length > 3) { // OK;LOGIN_SUCCESSFUL;FirstName;ClientID
                        loggedInUserFirstName = parts[2];
                        loggedInUserId = Integer.parseInt(parts[3]);
                    } else { // Awaryjnie, jeśli serwer nie wysłał pełnych danych
                        loggedInUserFirstName = "User";
                        loggedInUserId = Integer.parseInt(clientIdStr);
                    }
                    return true; // Logowanie pomyślne
                } else {
                    System.out.println("Login failed: " + formatUserServerResponse(response));
                }
            } else {
                System.out.println("Login failed. No response from server.");
            }
            attempts++;
            if (attempts < 3) System.out.println((3 - attempts) + " login attempt(s) remaining.");
        }
        return false; // Logowanie nie powiodło się po wielu próbach
    }

    // --- Metody do wyświetlania menu i formatowania/parsowania (wchłonięta logika) ---
    private static void displayUserMenu() {
        System.out.println("\nClient Terminal Menu (Logged in as: " + loggedInUserFirstName + " - ID: " + loggedInUserId + "):");
        System.out.println("1. Check Account Balance");
        System.out.println("2. Deposit Funds");
        System.out.println("3. Withdraw Funds");
        System.out.println("4. Transfer Funds to Another Account");
        System.out.println("5. List My Accounts");
        System.out.println("X. Logout and Exit");
        System.out.print("Enter your choice: ");
    }

    // Metody obsługujące poszczególne opcje menu i formatujące żądania
    private static String handleCheckBalance() {
        String accountNumber = promptAccountNumber("Enter Account Number to check balance: ");
        return Protocol.buildMessage(Protocol.CMD_BALANCE, accountNumber);
    }

    private static String handleDeposit() {
        String accountNumber = promptAccountNumber("Enter Account Number to deposit into: ");
        String amount = promptPositiveAmount("Enter amount to deposit: ");
        return Protocol.buildMessage(Protocol.CMD_DEPOSIT, accountNumber, amount);
    }

    private static String handleWithdraw() {
        String accountNumber = promptAccountNumber("Enter Account Number to withdraw from: ");
        String amount = promptPositiveAmount("Enter amount to withdraw: ");
        return Protocol.buildMessage(Protocol.CMD_WITHDRAW, accountNumber, amount);
    }

    private static String handleTransfer() {
        String fromAccount = promptAccountNumber("Enter YOUR Account Number (from which to transfer): ");
        String toAccount = promptAccountNumber("Enter DESTINATION Account Number (to which to transfer): ");
        String amount = promptPositiveAmount("Enter amount to transfer: ");
        if (fromAccount.equals(toAccount)) {
            System.out.println("Cannot transfer funds to the same account.");
            return null; // Nie wysyłaj żądania
        }
        return Protocol.buildMessage(Protocol.CMD_TRANSFER, fromAccount, toAccount, amount);
    }

    // Parsowanie odpowiedzi serwera
    private static String formatUserServerResponse(String response) {
        if (response == null || response.trim().isEmpty()) return "Error: No response from server.";
        String[] parts = Protocol.parseMessage(response);
        if (parts.length == 0) return "Error: Malformed empty response.";

        String commandOrStatus = parts[0];
        switch (commandOrStatus) {
            case Protocol.RES_OK:
                if (parts.length < 2) return "Success: Operation completed.";
                String subStatus = parts[1];
                String message = subStatus.replace("_", " ");
                if ((Protocol.OK_DEPOSIT_SUCCESSFUL.equals(subStatus) || Protocol.OK_WITHDRAWAL_SUCCESSFUL.equals(subStatus)) && parts.length > 2)
                    return "Success: " + message + ". New balance: " + parts[2];
                if (Protocol.OK_LOGOUT_SUCCESSFUL.equals(subStatus) && parts.length > 2) // Wiadomość pożegnalna
                    return "Success: " + message + " " + parts[2];
                if (Protocol.OK_LOGIN_SUCCESSFUL.equals(subStatus)) // Logowanie obsługiwane w performLogin
                    return "Success: Login successful (details processed).";
                return "Success: " + message;

            case Protocol.RES_BALANCE_IS:
                return parts.length > 1 ? "Your current account balance is: " + parts[1] : "Malformed balance response.";

            case Protocol.RES_MY_ACCOUNTS:
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    StringBuilder sb = new StringBuilder("Your Accounts:\n");
                    String[] accounts = parts[1].split(",");
                    for (String acc : accounts) {
                        sb.append("  - ").append(acc.replace(":", " (Balance: ")).append(")\n");
                    }
                    return sb.toString();
                }
                return "Info: No accounts listed by server or malformed response.";

            case Protocol.RES_INFO:
                return parts.length > 2 ? "Info: " + parts[2].replace("_"," ") : (parts.length > 1 ? "Info: " + parts[1].replace("_"," ") : "Info from server.");

            case Protocol.RES_ERROR:
                if (parts.length < 2) return "Server Error: Unknown error.";
                String errorType = parts[1].replace("_", " ");
                String details = (parts.length > 2 && !parts[2].isEmpty()) ? parts[2] : "No additional details.";
                return "Server Error: " + errorType + (parts.length > 2 ? " - " + details : ".");

            case Protocol.ERR_ACCOUNT_NOT_FOUND: return "Error: Account not found. Please check the account number.";
            case Protocol.ERR_INSUFFICIENT_FUNDS: return "Error: Insufficient funds for this operation.";
            case Protocol.ERR_AUTH_REQUIRED: return "Error: Authentication required. Please log in.";
            case Protocol.ERR_UNKNOWN_COMMAND: return "Error: Server did not recognize the command.";

            default:
                return "Raw/Unknown Server Response: " + response;
        }
    }
}