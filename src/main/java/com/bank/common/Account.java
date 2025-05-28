// File: src/main/java/com/bank/common/Account.java
package com.bank.common; // Zgodnie z uproszczoną strukturą

import java.math.BigDecimal;
import java.util.Objects;

public class Account {
    private int id; // Klucz główny konta w tabeli 'accounts'
    private int clientId; // Klucz obcy łączący z tabelą 'clients' (właściciel)
    private String accountNumber; // Unikalny numer konta (np. PLxxxxxxxx)
    private BigDecimal balance; // Bieżące saldo konta

    public Account(int id, int clientId, String accountNumber, BigDecimal balance) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Numer konta nie może być pusty.");
        }
        if (balance == null) {
            throw new IllegalArgumentException("Saldo nie może być null.");
        }
        // Podstawowa walidacja salda, mogłaby być bardziej rygorystyczna
        // (np. brak ujemnych wartości, chyba że dozwolone są debety)
        // W tym projekcie zakładamy, że logika biznesowa zapobiega niechcianym stanom.

        this.id = id;
        this.clientId = clientId;
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    // Gettery
    public int getId() {
        return id;
    }

    public int getClientId() {
        return clientId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        if (balance == null) {
            throw new IllegalArgumentException("Saldo nie może być ustawione na null.");
        }
        // Rozważ, czy dozwolone są ujemne salda na tym etapie.
        // Jeśli nie: if (balance.compareTo(BigDecimal.ZERO) < 0) {
        // throw new IllegalArgumentException("Saldo nie może być ujemne."); }
        this.balance = balance;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", clientId=" + clientId +
                ", accountNumber='" + accountNumber + '\'' +
                // Formatowanie salda do dwóch miejsc po przecinku dla celów wyświetlania
                ", balance=" + (balance != null ? String.format("%.2f", balance) : "null") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        // Konta są jednoznacznie identyfikowane przez ich ID w bazie danych lub numer konta.
        // Uwzględnienie clientId zapewnia, że jest to kontekst tego samego właściciela,
        // jeśli ID nie zostały jeszcze wygenerowane przez bazę danych.
        return id == account.id && // Jeśli używane są ID generowane przez bazę, to często wystarcza, jeśli > 0
                clientId == account.clientId &&
                Objects.equals(accountNumber, account.accountNumber);
    }

    @Override
    public int hashCode() {
        // Zgodne z equals: używa pól definiujących unikalność.
        return Objects.hash(id, clientId, accountNumber);
    }
}