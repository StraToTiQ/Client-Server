// File: src/main/java/com/bank/common/Protocol.java
package com.bank.common; // Zgodnie z uproszczoną strukturą

/**
 * Klasa przechowująca stałe związane z protokołem komunikacyjnym
 * klient-serwer oraz potencjalnie proste metody pomocnicze.
 */
public final class Protocol {

    // Prywatny konstruktor, aby zapobiec tworzeniu instancji
    private Protocol() {
        throw new UnsupportedOperationException("Ta klasa jest klasą narzędziową i nie można tworzyć jej instancji.");
    }

    // --- Ogólne Elementy Protokołu ---
    public static final String SEPARATOR = ";"; // Separator dla części wiadomości

    // --- Typy Głównych Poleceń Klienta ---
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_LOGOUT = "LOGOUT";
    public static final String CMD_ADMIN = "ADMIN"; // Prefiks dla wszystkich poleceń administracyjnych
    public static final String CMD_BALANCE = "BALANCE";
    public static final String CMD_DEPOSIT = "DEPOSIT";
    public static final String CMD_WITHDRAW = "WITHDRAW";
    public static final String CMD_TRANSFER = "TRANSFER";
    public static final String CMD_LIST_MY_ACCOUNTS = "LIST_MY_ACCOUNTS";

    // --- Podpolecenia Administracyjne (używane jako drugi token po CMD_ADMIN) ---
    public static final String SUB_CMD_ADD_CLIENT = "ADD_CLIENT";
    public static final String SUB_CMD_ADD_ACCOUNT_TO_CLIENT = "ADD_ACCOUNT_TO_CLIENT";
    public static final String SUB_CMD_GET_CLIENT_INFO_BY_ID = "GET_CLIENT_INFO_BY_ID";
    public static final String SUB_CMD_GET_ACCOUNT_DETAILS = "GET_ACCOUNT_DETAILS";
    public static final String SUB_CMD_UPDATE_CLIENT_INFO_BY_ID = "UPDATE_CLIENT_INFO_BY_ID";
    public static final String SUB_CMD_DELETE_CLIENT = "DELETE_CLIENT";
    public static final String SUB_CMD_DELETE_ACCOUNT = "DELETE_ACCOUNT";

    // --- Prefiksy / Statusy Odpowiedzi Serwera ---
    public static final String RES_OK = "OK";                       // Ogólny wskaźnik sukcesu
    public static final String RES_ERROR = "ERROR";                 // Ogólny wskaźnik błędu
    public static final String RES_CLIENT_INFO = "CLIENT_INFO";     // Admin: Odpowiedź zawierająca szczegóły klienta
    public static final String RES_ACCOUNT_DETAILS = "ACCOUNT_DETAILS"; // Admin: Odpowiedź zawierająca szczegóły konta
    public static final String RES_BALANCE_IS = "BALANCE_IS";       // Klient: Odpowiedź z saldem konta
    public static final String RES_MY_ACCOUNTS = "MY_ACCOUNTS";     // Klient: Odpowiedź listująca konta klienta
    public static final String RES_INFO = "INFO";                   // Generyczna wiadomość informacyjna od serwera

    // --- Szczegółowe Pod-Statusy OK (zazwyczaj parts[1] po RES_OK) ---
    public static final String OK_LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";
    public static final String OK_LOGOUT_SUCCESSFUL = "LOGOUT_SUCCESSFUL";
    public static final String OK_CLIENT_ADDED = "CLIENT_ADDED";
    public static final String OK_ACCOUNT_ADDED_TO_CLIENT = "ACCOUNT_ADDED_TO_CLIENT";
    public static final String OK_CLIENT_INFO_UPDATED = "CLIENT_INFO_UPDATED";
    public static final String OK_CLIENT_DELETED = "CLIENT_DELETED";
    public static final String OK_ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String OK_DEPOSIT_SUCCESSFUL = "DEPOSIT_SUCCESSFUL";
    public static final String OK_WITHDRAWAL_SUCCESSFUL = "WITHDRAWAL_SUCCESSFUL";
    public static final String OK_TRANSFER_SUCCESSFUL = "TRANSFER_SUCCESSFUL";

    // --- Statusy Błędów Najwyższego Poziomu lub Pod-Statusy ---
    public static final String ERR_CLIENT_NOT_FOUND = "CLIENT_NOT_FOUND";
    public static final String ERR_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String ERR_UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String ERR_UNKNOWN_ADMIN_SUBCOMMAND = "UNKNOWN_ADMIN_SUBCOMMAND";
    public static final String ERR_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String ERR_AUTH_REQUIRED = "AUTH_REQUIRED"; // Klient musi się najpierw zalogować

    // --- Szczegółowe Pod-Statusy Błędów (zazwyczaj parts[1] po RES_ERROR lub jako część szczegółów) ---
    public static final String ERR_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ERR_ALREADY_LOGGED_IN = "ALREADY_LOGGED_IN";
    public static final String ERR_NOT_LOGGED_IN = "NOT_LOGGED_IN"; // Akcja wymaga wcześniejszego zalogowania
    public static final String ERR_PESEL_EXISTS = "PESEL_EXISTS";
    public static final String ERR_PESEL_EXISTS_OTHER = "PESEL_EXISTS_OTHER";
    public static final String ERR_UPDATE_FAILED_GENERIC = "UPDATE_FAILED_OR_CLIENT_UNCHANGED";
    public static final String ERR_CLIENT_DELETION_FAILED = "CLIENT_DELETION_FAILED"; // Używane w BankerApp
    public static final String ERR_ACCOUNT_DELETION_FAILED = "ACCOUNT_DELETION_FAILED"; // Używane w BankerApp
    public static final String ERR_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ERR_SOURCE_ACCOUNT_NOT_FOUND = "SOURCE_ACCOUNT_NOT_FOUND";
    public static final String ERR_DESTINATION_ACCOUNT_NOT_FOUND = "DESTINATION_ACCOUNT_NOT_FOUND";

    // --- Wewnętrzne Typy Błędów Po Stronie Serwera (używane ze strukturą RES_ERROR;TYP;wiadomość) ---
    // Te mogą być mniej istotne dla klas klienckich, ale ClientHandler ich używa.
    public static final String ERR_TYPE_PARAM = "PARAM";         // Błąd parsowania parametrów
    public static final String ERR_TYPE_DB = "DB";               // Błąd operacji na bazie danych
    public static final String ERR_TYPE_FORMAT = "FORMAT";       // Nieprawidłowy format liczby w parametrach
    public static final String ERR_TYPE_ARG = "ARG";             // Nielegalny argument przekazany do metody
    public static final String ERR_TYPE_UNEXPECTED = "UNEXPECTED"; // Ogólny, nieoczekiwany błąd serwera

    // --- Markery i Pod-statusy Informacyjne ---
    public static final String INFO_NO_ACCOUNTS_FOUND = "NO_ACCOUNTS_FOUND";
    public static final String NO_ACCOUNTS_MARKER = "NO_ACCOUNTS"; // Marker w odpowiedzi adminGetClientInfoById

    // --- Metody Pomocnicze (Opcjonalne - można je dodać, jeśli chcemy unikać String.join/split w wielu miejscach) ---

    /**
     * Prosta metoda pomocnicza do tworzenia wiadomości protokołu.
     * @param parts Części wiadomości.
     * @return Sformatowana wiadomość protokołu.
     */
    public static String buildMessage(String... parts) {
        return String.join(SEPARATOR, parts);
    }

    /**
     * Prosta metoda pomocnicza do dzielenia wiadomości protokołu.
     * @param message Wiadomość protokołu.
     * @return Tablica stringów zawierająca części wiadomości.
     */
    public static String[] parseMessage(String message) {
        if (message == null) {
            return new String[0]; // Zwróć pustą tablicę, aby uniknąć NullPointerException
        }
        return message.split(SEPARATOR, -1); // -1 aby uwzględnić puste tokeny na końcu
    }
}