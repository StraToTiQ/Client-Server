// File: src/main/java/com/bank/common/Client.java
package com.bank.common; // Zgodnie z uproszczoną strukturą

import java.util.Objects;

public class Client {
    private int id; // Klucz główny klienta w tabeli 'clients'
    private String firstName;
    private String lastName;
    private String pesel; // Polski numer identyfikacyjny PESEL
    private String password; // Hasło klienta (używane w logice uwierzytelniania)

    public Client(int id, String firstName, String lastName, String pesel, String password) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("Imię nie może być puste.");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Nazwisko nie może być puste.");
        }
        if (pesel == null || pesel.trim().isEmpty()) {
            // Dalsza walidacja (np. 11 cyfr) powinna być obsługiwana przez logikę biznesową/walidację wejścia,
            // ale podstawowe sprawdzenie null/pusty jest tutaj dobre.
            throw new IllegalArgumentException("PESEL nie może być pusty.");
        }
        // Hasło może być null, jeśli ten obiekt reprezentuje klienta, którego hasło nie jest obsługiwane
        // (np. przy pobieraniu szczegółów klienta do wyświetlenia, nie do uwierzytelniania).

        this.id = id;
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.pesel = pesel.trim();
        this.password = password; // Przechowuj tak, jak jest (może być null)
    }

    // Gettery
    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPesel() {
        return pesel;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        // WAŻNE: Ze względów bezpieczeństwa NIE umieszczaj hasła w domyślnej metodzie toString().
        return "Client{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", pesel='" + pesel + '\'' +
                // Jeśli chcesz zaznaczyć obecność hasła bez jego eksponowania:
                // ", passwordStatus=" + (password != null && !password.isEmpty() ? "Set" : "Not Set") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Client client = (Client) o;
        // Równość jest zazwyczaj oparta na unikalnym identyfikatorze.
        // Jeśli 'id' jest kluczem głównym generowanym przez bazę danych i jest > 0, jest najlepszym kandydatem.
        // PESEL jest również unikalny.
        // Hasło NIE powinno być częścią porównania równości dla tożsamości obiektu.
        if (id > 0 && client.id > 0) { // Jeśli oba mają ID z bazy danych
            return id == client.id;
        }
        // Awaryjnie do PESEL, jeśli ID nie są ustawione (np. przed zapisaniem do bazy)
        return Objects.equals(pesel, client.pesel);
    }

    @Override
    public int hashCode() {
        // Zgodne z equals().
        // Jeśli 'id' jest głównym wyznacznikiem równości dla obiektów utrwalonych:
        if (id > 0) {
            return Objects.hash(id);
        }
        // Awaryjnie do PESEL:
        return Objects.hash(pesel);
    }
}