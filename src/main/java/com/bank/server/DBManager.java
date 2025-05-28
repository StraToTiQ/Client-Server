// File: src/main/java/com/bank/server/DBManager.java
package com.bank.server; // Zgodnie z uproszczoną strukturą

import com.bank.common.Account;
import com.bank.common.Client;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class DBManager implements AutoCloseable {
    private Connection conn;
    private final Random rand = new Random();

    public DBManager(String url, String user, String pass) throws SQLException, ClassNotFoundException {
        // Jawne załadowanie sterownika, chociaż nowoczesne JDBC może tego nie wymagać
        Class.forName("com.mysql.cj.jdbc.Driver");
        conn = DriverManager.getConnection(url, user, pass);
        // Domyślnie auto-commit jest włączony (true). Zarządzamy nim jawnie dla transakcji.
    }

    // --- Metody Zarządzania Klientami ---

    public int addClient(String firstName, String lastName, String pesel, String password) throws SQLException {
        String sql = "INSERT INTO clients(first_name, last_name, pesel, password) VALUES (?, ?, ?, ?)";
        try (PreparedStatement st = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, firstName);
            st.setString(2, lastName);
            st.setString(3, pesel);
            st.setString(4, password); // Przechowywanie jawnego hasła zgodnie z zakresem projektu
            int affectedRows = st.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Tworzenie klienta nie powiodło się, nie zmodyfikowano żadnych wierszy.");
            }
            try (ResultSet generatedKeys = st.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Tworzenie klienta nie powiodło się, nie uzyskano ID.");
                }
            }
        }
    }

    public Optional<Client> authenticateClient(int clientId, String password) throws SQLException {
        String sql = "SELECT id, first_name, last_name, pesel, password AS stored_password FROM clients WHERE id = ?";
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, clientId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("stored_password");
                    if (password.equals(storedPassword)) {
                        return Optional.of(new Client(
                                rs.getInt("id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("pesel"),
                                null // Hasło null w zwracanym obiekcie dla bezpieczeństwa
                        ));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Client> findClientById(int clientId) throws SQLException {
        String sql = "SELECT id, first_name, last_name, pesel FROM clients WHERE id = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, clientId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Client(rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("pesel"), null));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Client> findClientByPesel(String pesel) throws SQLException {
        String sql = "SELECT id, first_name, last_name, pesel FROM clients WHERE pesel = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, pesel);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Client(rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("pesel"), null));
                }
            }
        }
        return Optional.empty();
    }

    public boolean updateClientInfo(int clientId, String newFirstName, String newLastName, String newPesel) throws SQLException {
        String sql = "UPDATE clients SET first_name = ?, last_name = ?, pesel = ? WHERE id = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, newFirstName);
            st.setString(2, newLastName);
            st.setString(3, newPesel);
            st.setInt(4, clientId);
            int affectedRows = st.executeUpdate();
            return affectedRows > 0;
        }
    }

    public boolean deleteClientById(int clientId) throws SQLException {
        String sql = "DELETE FROM clients WHERE id = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, clientId);
            int affectedRows = st.executeUpdate();
            return affectedRows > 0;
        }
    }

    // --- Metody Zarządzania Kontami ---

    private String generateAccountNumber() throws SQLException {
        String accountNumber;
        boolean unique;
        do {
            unique = false;
            StringBuilder sb = new StringBuilder("PL");
            for (int i = 0; i < 26; i++) sb.append(rand.nextInt(10));
            accountNumber = sb.toString();
            if (!findAccountByNumber(accountNumber).isPresent()) {
                unique = true;
            }
        } while (!unique);
        return accountNumber;
    }

    public Account addAccountToClient(int clientId, BigDecimal initialBalance) throws SQLException {
        if (!findClientById(clientId).isPresent()) {
            throw new SQLException("Nie można dodać konta: Klient o ID " + clientId + " nie został znaleziony.");
        }
        String accountNumber = generateAccountNumber();
        String sql = "INSERT INTO accounts(client_id, account_number, balance) VALUES (?, ?, ?)";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setInt(1, clientId);
            st.setString(2, accountNumber);
            st.setBigDecimal(3, initialBalance);
            int affectedRows = st.executeUpdate();
            if (affectedRows == 0) throw new SQLException("Tworzenie konta nie powiodło się, nie zmodyfikowano żadnych wierszy.");
            try (ResultSet generatedKeys = st.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int accountId = generatedKeys.getInt(1);
                    return new Account(accountId, clientId, accountNumber, initialBalance);
                } else throw new SQLException("Tworzenie konta nie powiodło się, nie uzyskano ID konta.");
            }
        }
    }

    public Optional<Account> findAccountByNumber(String accountNumber) throws SQLException {
        String sql = "SELECT id, client_id, account_number, balance FROM accounts WHERE account_number = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, accountNumber);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Account(rs.getInt("id"), rs.getInt("client_id"), rs.getString("account_number"), rs.getBigDecimal("balance")));
                }
            }
        }
        return Optional.empty();
    }

    public List<Account> findAllAccountsByClientId(int clientId) throws SQLException {
        List<Account> clientAccounts = new ArrayList<>();
        String sql = "SELECT id, client_id, account_number, balance FROM accounts WHERE client_id = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, clientId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    clientAccounts.add(new Account(rs.getInt("id"), rs.getInt("client_id"), rs.getString("account_number"), rs.getBigDecimal("balance")));
                }
            }
        }
        return clientAccounts;
    }

    private void updateAccountBalanceInTransaction(String accountNumber, BigDecimal newBalance, Connection activeConnection) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = activeConnection.prepareStatement(sql)) {
            st.setBigDecimal(1, newBalance);
            st.setString(2, accountNumber);
            int affectedRows = st.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Aktualizacja salda nie powiodła się dla konta " + accountNumber + " w ramach transakcji.");
            }
        }
    }

    public void executeTransferTransaction(Account fromAccount, Account toAccount, BigDecimal amount) throws SQLException {
        // Sprawdzenie warunku wstępnego (choć powinno być też w logice wywołującej)
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new SQLException("Niewystarczające środki do przelewu. Konto źródłowe: " + fromAccount.getAccountNumber() +
                    ", Żądane: " + amount + ", Dostępne: " + fromAccount.getBalance());
        }
        boolean originalAutoCommitState = false;
        try {
            originalAutoCommitState = conn.getAutoCommit();
            if (originalAutoCommitState) {
                conn.setAutoCommit(false); // Rozpocznij transakcję
            }

            BigDecimal newFromBalance = fromAccount.getBalance().subtract(amount);
            BigDecimal newToBalance = toAccount.getBalance().add(amount);

            updateAccountBalanceInTransaction(fromAccount.getAccountNumber(), newFromBalance, conn);
            updateAccountBalanceInTransaction(toAccount.getAccountNumber(), newToBalance, conn);

            if (originalAutoCommitState) {
                conn.commit(); // Zatwierdź transakcję
            }
        } catch (SQLException e) {
            if (conn != null && originalAutoCommitState) {
                try {
                    conn.rollback();
                } catch (SQLException exRollback) {
                    e.addSuppressed(exRollback); // Dodaj błąd rollbacku jako stłumiony
                }
            }
            throw e; // Rzuć oryginalny wyjątek
        } finally {
            if (conn != null && originalAutoCommitState) {
                try {
                    conn.setAutoCommit(originalAutoCommitState); // Przywróć oryginalny stan auto-commit
                } catch (SQLException exRestore) {
                    // Loguj ten błąd, ale jest mniej krytyczny
                }
            }
        }
    }

    public void updateAccountBalance(String accountNumber, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setBigDecimal(1, newBalance);
            st.setString(2, accountNumber);
            int affectedRows = st.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Aktualizacja salda nie powiodła się dla konta " + accountNumber + ". Konto nie znalezione lub saldo niezmienione.");
            }
        }
    }

    public boolean deleteAccountByNumber(String accountNumber) throws SQLException {
        String sql = "DELETE FROM accounts WHERE account_number = ?";
        // Implementacja jak w poprzedniej wersji...
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, accountNumber);
            int affectedRows = st.executeUpdate();
            return affectedRows > 0;
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}