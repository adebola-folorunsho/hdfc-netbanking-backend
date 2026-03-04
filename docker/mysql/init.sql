-- ─── Create databases for each microservice ───────────────────────────────
CREATE DATABASE IF NOT EXISTS hdfc_user_db;
CREATE DATABASE IF NOT EXISTS hdfc_account_db;
CREATE DATABASE IF NOT EXISTS hdfc_transaction_db;
CREATE DATABASE IF NOT EXISTS hdfc_audit_db;
CREATE DATABASE IF NOT EXISTS hdfc_notification_db;
CREATE DATABASE IF NOT EXISTS hdfc_scheduler_db;
CREATE DATABASE IF NOT EXISTS hdfc_currency_db;

-- ─── Grant permissions to hdfc_user on all databases ──────────────────────
GRANT ALL PRIVILEGES ON hdfc_user_db.* TO 'hdfc_user'@'%';
GRANT ALL PRIVILEGES ON hdfc_account_db.* TO 'hdfc_user'@'%';
GRANT ALL PRIVILEGES ON hdfc_transaction_db.* TO 'hdfc_user'@'%';
GRANT ALL PRIVILEGES ON hdfc_audit_db.* TO 'hdfc_user'@'%';
GRANT ALL PRIVILEGES ON hdfc_notification_db.* TO 'hdfc_user'@'%';
GRANT ALL PRIVILEGES ON hdfc_scheduler_db.* TO 'hdfc_user'@'%';
GRANT ALL PRIVILEGES ON hdfc_currency_db.* TO 'hdfc_user'@'%';

FLUSH PRIVILEGES;