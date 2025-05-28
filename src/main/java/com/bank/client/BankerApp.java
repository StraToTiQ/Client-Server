// File: src/main/java/com/bank/client/BankerApp.java
package com.bank.client; // Zgodnie z uproszczoną strukturą

import com.bank.common.Protocol; // Używamy naszej nowej klasy Protocol

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class BankerApp {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static Scanner consoleIn; // Scanner do odczytu danych od użytkownika

    public static void main(String[] args) {
        System.out.println("Banker Terminal (TB) - Initializing...");
        consoleIn = new Scanner(System.in);

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Successfully connected to Bank Server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

            String userInput;
            boolean running = true;
            while (running) {
                displayAdminMenu();
                userInput = consoleIn.nextLine().trim().toUpperCase();
                String request = null;
                String serverResponse;

                switch (userInput) {
                    case "1": // Add new client
                        request = handleAddClient();
                        break;
                    case "2": // Add new account to existing client
                        request = handleAddAccountToClient();
                        break;
                    case "3": // View Client Information & Accounts (by Client ID)
                        request = handleGetClientInfoById();
                        break;
                    case "4": // View Specific Account Details (by Account Number)
                        request = handleGetAccountDetails();
                        break;
                    case "5": // Update Client's Personal Information (by Client ID)
                        request = handleUpdateClientInfo();
                        break;
                    case "6": // Delete Client (and all their accounts)
                        request = handleDeleteClient();
                        break;
                    case "7": // Delete Specific Account
                        request = handleDeleteAccount();
                        break;
                    case "X":
                        System.out.println("Exiting Banker Terminal...");
                        running = false; // Zakończ pętlę
                        // Nie ma potrzeby wysyłania komunikatu do serwera przy wyjściu z BankerApp
                        continue; // Przejdź do następnej iteracji, aby zakończyć pętlę
                    default:
                        System.out.println("Invalid option. Please try again.");
                        continue; // Pomiń wysyłanie żądania
                }

                if (request != null) {
                    System.out.println("Sending to server: " + request);
                    serverOut.println(request);
                    serverResponse = serverIn.readLine(); // Czekaj na odpowiedź serwera
                    System.out.println("Server response: " + formatAdminServerResponse(serverResponse));
                } else if (running) { // Jeśli request jest null, ale nie wychodzimy (np. anulowano akcję)
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
        } catch (Exception e) { // Ogólny wyjątek dla nieoczekiwanych problemów
            System.err.println("An unexpected error occurred in BankerApp: " + e.getMessage());
            e.printStackTrace(); // Dla debugowania
        } finally {
            if (consoleIn != null) {
                // consoleIn.close(); // Zamykanie System.in może być problematyczne w niektórych środowiskach
            }
            System.out.println("Banker Terminal has shut down.");
        }
    }

    // --- Metody pomocnicze do obsługi konsoli (uproszczony ConsoleHelper) ---
    private static String promptString(String message) {
        System.out.print(message);
        return consoleIn.nextLine().trim();
    }

    private static String promptNonEmptyString(String message, String fieldName) {
        String input;
        do {
            input = promptString(message);
            if (input.isEmpty()) {
                System.out.println(fieldName + " cannot be empty. Please try again.");
            }
        } while (input.isEmpty());
        return input;
    }

    private static String promptPesel(String message) {
        String pesel;
        while (true) {
            pesel = promptString(message);
            if (pesel.matches("\\d{11}")) {
                return pesel;
            }
            System.out.println("Invalid PESEL format. Must be 11 digits. Please try again.");
        }
    }

    private static String promptClientId(String message) {
        String clientIdStr;
        while (true) {
            clientIdStr = promptString(message);
            if (clientIdStr.matches("\\d+")) {
                return clientIdStr;
            }
            System.out.println("Invalid Client ID format. Must be a number. Please try again.");
        }
    }

    private static String promptAccountNumber(String message) {
        String accNum;
        while (true) {
            accNum = promptString(message).toUpperCase();
            if (accNum.matches("^PL\\d{26}$")) {
                return accNum;
            }
            System.out.println("Invalid Account Number format. Must be PL followed by 26 digits. Please try again.");
        }
    }

    private static String promptBalance(String message) {
        String balanceStr;
        while (true) {
            balanceStr = promptString(message);
            if (balanceStr.matches("^\\d*\\.?\\d+$")) {
                try {
                    if (Double.parseDouble(balanceStr) >= 0) return balanceStr;
                    else System.out.println("Balance cannot be negative. Please try again.");
                } catch (NumberFormatException e) { System.out.println("Invalid balance format."); }
            } else {
                System.out.println("Invalid balance format (e.g., 100.00 or 0). Please try again.");
            }
        }
    }

    private static boolean confirmAction(String message) {
        String confirmation = promptString(message + " Type 'YES' to confirm: ");
        return "YES".equalsIgnoreCase(confirmation);
    }

    // --- Metody do wyświetlania menu i formatowania/parsowania (wchłonięta logika) ---
    private static void displayAdminMenu() {
        System.out.println("\nBanker Terminal Menu:");
        System.out.println("1. Add New Client (and their first default account)");
        System.out.println("2. Add New Account to Existing Client");
        System.out.println("3. View Client Information & Accounts (by Client ID)");
        System.out.println("4. View Specific Account Details (by Account Number)");
        System.out.println("5. Update Client's Personal Information (by Client ID)");
        System.out.println("6. Delete Client (and all associated accounts)");
        System.out.println("7. Delete Specific Account");
        System.out.println("X. Exit");
        System.out.print("Enter your choice: ");
    }

    // Metody obsługujące poszczególne opcje menu i formatujące żądania
    private static String handleAddClient() {
        String firstName = promptNonEmptyString("Enter client's first name: ", "First name");
        String lastName = promptNonEmptyString("Enter client's last name: ", "Last name");
        String pesel = promptPesel("Enter client's PESEL (11 digits): ");
        String password = promptNonEmptyString("Enter initial password: ", "Password");
        return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_ADD_CLIENT, firstName, lastName, pesel, password);
    }

    private static String handleAddAccountToClient() {
        String clientId = promptClientId("Enter Client ID to add account for: ");
        String balance = promptBalance("Enter initial balance (e.g., 100.00 or 0): ");
        return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_ADD_ACCOUNT_TO_CLIENT, clientId, balance);
    }

    private static String handleGetClientInfoById() {
        String clientId = promptClientId("Enter Client ID to view details: ");
        return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_GET_CLIENT_INFO_BY_ID, clientId);
    }

    private static String handleGetAccountDetails() {
        String accNum = promptAccountNumber("Enter Account Number to view details (e.g., PLxxxxxxxx): ");
        return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_GET_ACCOUNT_DETAILS, accNum);
    }

    private static String handleUpdateClientInfo() {
        String clientId = promptClientId("Enter Client ID of the client to update: ");
        System.out.println("Enter new information (all fields required):");
        String newFirstName = promptNonEmptyString("New First Name: ", "New First Name");
        String newLastName = promptNonEmptyString("New Last Name: ", "New Last Name");
        String newPesel = promptPesel("New PESEL (11 digits): ");
        return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_UPDATE_CLIENT_INFO_BY_ID, clientId, newFirstName, newLastName, newPesel);
    }

    private static String handleDeleteClient() {
        String clientId = promptClientId("Enter Client ID to DELETE (IRREVERSIBLE): ");
        if (confirmAction("Are you absolutely sure you want to delete client ID " + clientId + "?")) {
            return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_DELETE_CLIENT, clientId);
        }
        return null; // Anulowano
    }

    private static String handleDeleteAccount() {
        String accNum = promptAccountNumber("Enter Account Number to DELETE (IRREVERSIBLE): ");
        if (confirmAction("Are you absolutely sure you want to delete account " + accNum + "?")) {
            return Protocol.buildMessage(Protocol.CMD_ADMIN, Protocol.SUB_CMD_DELETE_ACCOUNT, accNum);
        }
        return null; // Anulowano
    }

    // Parsowanie odpowiedzi serwera
    private static String formatAdminServerResponse(String response) {
        if (response == null || response.trim().isEmpty()) return "Error: No response from server.";

        String[] parts = Protocol.parseMessage(response); // Użycie metody pomocniczej z Protocol
        if (parts.length == 0) return "Error: Malformed empty response.";

        String commandOrStatus = parts[0];

        switch (commandOrStatus) {
            case Protocol.RES_OK:
                if (parts.length < 2) return "Success: Operation completed.";
                String subStatus = parts[1];
                if (Protocol.OK_CLIENT_ADDED.equals(subStatus) && parts.length > 3)
                    return "Success: Client added. Client ID: " + parts[2] + ", First Account Number: " + parts[3];
                if (Protocol.OK_ACCOUNT_ADDED_TO_CLIENT.equals(subStatus) && parts.length > 2)
                    return "Success: Account added. New Account Number: " + parts[2];
                if (Protocol.OK_CLIENT_INFO_UPDATED.equals(subStatus))
                    return "Success: Client information updated.";
                if (Protocol.OK_CLIENT_DELETED.equals(subStatus) && parts.length > 2)
                    return "Success: Client ID " + parts[2] + " and accounts deleted.";
                if (Protocol.OK_ACCOUNT_DELETED.equals(subStatus) && parts.length > 2)
                    return "Success: Account " + parts[2] + " deleted.";
                return "Success: " + subStatus.replace("_", " ");

            case Protocol.RES_CLIENT_INFO:
                if (parts.length < 5) return "Error: Malformed client info response.";
                StringBuilder sbInfo = new StringBuilder("Client Details:\n");
                sbInfo.append("  Client ID: ").append(parts[1]).append("\n");
                sbInfo.append("  First Name: ").append(parts[2]).append("\n");
                sbInfo.append("  Last Name: ").append(parts[3]).append("\n");
                sbInfo.append("  PESEL: ").append(parts[4]).append("\n  Accounts:\n");
                if (parts.length > 5 && !parts[5].isEmpty() && !Protocol.NO_ACCOUNTS_MARKER.equals(parts[5])) {
                    String[] accounts = parts[5].split(",");
                    for (String acc : accounts) {
                        sbInfo.append("    - ").append(acc.replace(":", " (Balance: ")).append(")\n");
                    }
                } else {
                    sbInfo.append("    No accounts found for this client.\n");
                }
                return sbInfo.toString();

            case Protocol.RES_ACCOUNT_DETAILS:
                if (parts.length < 8) return "Error: Malformed account details response.";
                return String.format("Account Details:\n  DB ID: %s\n  Number: %s\n  Balance: %s\n  Owner (Client ID: %s):\n    Name: %s %s\n    PESEL: %s",
                        parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], parts[7]);

            case Protocol.RES_ERROR:
                if (parts.length < 2) return "Server Error: Unknown error.";
                String errorType = parts[1].replace("_", " ");
                String details = (parts.length > 2 && !parts[2].isEmpty()) ? parts[2] : "No additional details.";
                if (Protocol.ERR_TYPE_PARAM.equals(parts[1])) return "Server Error: Invalid Parameter - " + details;
                if (Protocol.ERR_TYPE_DB.equals(parts[1])) return "Server Error: Database Operation Failed - " + details;
                return "Server Error: " + errorType + (parts.length > 2 ? " - " + details : ".");

            // Bezpośrednie statusy błędów
            case Protocol.ERR_CLIENT_NOT_FOUND: return "Error: Client not found.";
            case Protocol.ERR_ACCOUNT_NOT_FOUND: return "Error: Account not found.";
            case Protocol.ERR_UNKNOWN_ADMIN_SUBCOMMAND: return "Error: Server did not recognize admin subcommand.";
            case Protocol.ERR_PESEL_EXISTS: return "Error: Client with this PESEL already exists.";
            case Protocol.ERR_PESEL_EXISTS_OTHER: return "Error: New PESEL already belongs to another client.";

            default:
                return "Raw/Unknown Server Response: " + response;
        }
    }
}