-- Run this ONCE in MySQL before running the simulation:
--   mysql -u root -p < database/hospital_db.sql
-- (or open this file in MySQL Workbench and execute it)

CREATE DATABASE IF NOT EXISTS hospital_db;
USE hospital_db;

CREATE TABLE IF NOT EXISTS hospital_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id      VARCHAR(30)  NOT NULL,   -- one value per program run
    event_time  DATETIME(3)  NOT NULL,   -- when the event happened
    worker_type VARCHAR(30)  NOT NULL,   -- DOCTOR, NURSE, LAB_TECHNICIAN, PHARMACIST, SYSTEM
    worker_name VARCHAR(50)  NOT NULL,
    patient_id  VARCHAR(20)  NULL,
    action      VARCHAR(40)  NOT NULL,
    details     VARCHAR(255) NULL,
    INDEX idx_run (run_id)
);