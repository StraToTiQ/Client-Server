-- -----------------------------------------------------------------------------
-- BankSystem Project - Database Setup Script
-- Version: 1.1
-- Last Updated: (Date of this response or last modification)
--
-- Purpose:
-- This script creates the 'bankdb' database and the 'clients' and 'accounts'
-- tables required for the Server-Client banking application.
-- It includes table definitions, primary keys, unique constraints, foreign keys
-- with ON DELETE CASCADE, and indexes for performance.
-- -----------------------------------------------------------------------------

-- 1. Create the database if it doesn't already exist
--    Using utf8mb4 for full Unicode support (including emojis, etc.)
--    and utf8mb4_unicode_ci for case-insensitive comparisons and sorting.
CREATE DATABASE IF NOT EXISTS bankdb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Select the newly created (or existing) database to use for subsequent commands
USE bankdb;

-- 3. Drop existing tables (optional, but recommended for a clean setup during development)
--    Order is important due to foreign key constraints:
--    'accounts' table must be dropped before 'clients' because 'accounts' references 'clients'.
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS clients;

-- 4. Create the 'clients' table
--    This table stores personal information about the bank's clients.
CREATE TABLE clients (
                         id INT AUTO_INCREMENT PRIMARY KEY,          -- Unique identifier for each client (auto-generated)
                         first_name VARCHAR(100) NOT NULL,           -- Client's first name (increased length for flexibility)
                         last_name VARCHAR(100) NOT NULL,            -- Client's last name (increased length for flexibility)
                         pesel CHAR(11) UNIQUE NOT NULL,             -- Client's Polish National Identification Number (PESEL)
    -- Must be exactly 11 characters and unique across all clients.
                         password VARCHAR(255) NOT NULL              -- Client's password.
    -- For a real application, this should store a HASHED password,
    -- not plain text. Increased length for hashed passwords.
    -- Current project uses plain text for simplicity.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Note: The UNIQUE constraint on 'pesel' automatically creates an index for it.
-- If you needed to add an index manually (e.g., on a non-unique column for searching):
-- CREATE INDEX idx_clients_lastname ON clients (last_name);

-- 5. Create the 'accounts' table
--    This table stores information about bank accounts, each linked to a client.
CREATE TABLE accounts (
                          id INT AUTO_INCREMENT PRIMARY KEY,          -- Unique identifier for each account (auto-generated)
                          client_id INT NOT NULL,                     -- Foreign key: ID of the client who owns this account.
    -- References the 'id' column in the 'clients' table.
                          account_number VARCHAR(28) UNIQUE NOT NULL, -- Unique bank account number.
    -- Example format: "PL" followed by 26 digits.
                          balance DECIMAL(19,2) NOT NULL DEFAULT 0.00, -- Current balance of the account.
    -- Using DECIMAL(19,2) for precision with monetary values,
    -- allowing for large balances up to 17 digits before decimal.
    -- Defaults to 0.00 for new accounts.

    -- Foreign key constraint:
    -- Ensures that 'client_id' in this table always refers to a valid 'id' in the 'clients' table.
    -- ON DELETE CASCADE: If a client is deleted from the 'clients' table, all of their
    --                    associated accounts in this 'accounts' table will be automatically deleted.
    --                    This maintains data integrity.
                          FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Note: The UNIQUE constraint on 'account_number' automatically creates an index.
-- Adding an explicit index on 'client_id' can improve performance when querying
-- for all accounts belonging to a specific client.
CREATE INDEX idx_accounts_client_id ON accounts (client_id);

-- -----------------------------------------------------------------------------
-- End of Database Setup Script
-- -----------------------------------------------------------------------------

-- Optional: Insert some sample data for testing (uncomment if needed)
/*
-- Ensure clients are inserted first to get their auto-generated IDs
INSERT INTO clients (first_name, last_name, pesel, password) VALUES
('Jan', 'Kowalski', '11111111111', 'pass123'),
('Anna', 'Nowak', '22222222222', 'securePass'),
('Piotr', 'Zieliński', '33333333333', 'qwerty');

-- Check the IDs generated for the clients (e.g., using SELECT * FROM clients;)
-- Then use those IDs when inserting accounts. Assuming IDs 1, 2, 3:

INSERT INTO accounts (client_id, account_number, balance) VALUES
(1, 'PL10100000000000000000000001', 1500.75),
(1, 'PL10100000000000000000000002', 250.00),
(2, 'PL20100000000000000000000003', 10000.00),
(3, 'PL30100000000000000000000004', 0.00);
*/

-- Optional: Display table structures for verification after running the script
/*
SHOW CREATE TABLE clients;
SHOW CREATE TABLE accounts;

DESCRIBE clients;
DESCRIBE accounts;

SELECT * FROM clients;
SELECT * FROM accounts;
*/

-- GRANT ALL PRIVILEGES ON bankdb.* TO 'your_db_user'@'localhost'; -- If you created a specific user
-- FLUSH PRIVILEGES;