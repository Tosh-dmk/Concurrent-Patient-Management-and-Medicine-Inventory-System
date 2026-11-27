# Concurrent Patient Management and Medicine Inventory System

**OOP 2 Practical - Threads, Problem 2 (Group 11-15)**

## 1. The problem

A hospital runs a centralized patient management system used at the same time by
doctors, nurses, laboratory technicians and pharmacists. The system must make sure
that concurrent operations never corrupt patient records and never produce a wrong
medicine inventory level.

Setup required by the question, and used in this program:

| Resource                  | Count    |
| ------------------------- | -------- |
| Registered patients       | 100      |
| Doctors                   | 5        |
| Nurses                    | 10       |
| Laboratory technicians    | 3        |
| Pharmacists               | 2        |
| Shared medicine inventory | 50 doses |

## 2. How the system works

All 20 healthcare workers run as separate threads in one `ExecutorService` pool.
`Main` creates every shared resource once and passes the same instances to everyone.

- **Nurses** update a patient's vital signs, then submit a laboratory request.
- **Laboratory technicians** take requests from the queue, run the test on a laboratory
  machine, store the result in the patient's record and publish it for doctors.
- **Doctors** add notes to patient records and retrieve completed laboratory results.
- **Pharmacists** take dispense requests from patients and dispense medicine from the
  shared inventory.

```
Nurses --LabRequest--> [lab request queue] --> Technicians --LabResult--> [completed queue] --> Doctors
                                                    |
                                       Semaphore (2 lab machines)

Patients --DispenseRequest--> [dispense queue] --> Pharmacists --> MedicineInventory (ReentrantLock)

Doctors, Nurses, Technicians --> PatientRepository --> Patient (ReadWriteLock)
```

## 4. Project structure

```
Main.java                 wires everything together and prints the report
models/                   Patient, LabRequest, LabResult, DispenseRequest
workers/                  Doctor, Nurse, LaboratoryTechnician, Pharmacist
services/                 PatientRepository, MedicineInventory, LaboratoryService, DatabaseLogger
integration/              HospitalIntegration (thread pool and lab machine semaphore)
database/                 hospital_db.sql (creates the MySQL database and table)
lib/                      put the MySQL Connector/J .jar here
```

## 5. Build and run (without the database)

Requires JDK 16 or newer (`Patient` uses Java records).

```bash
javac -d out $(find . -name "*.java")
java -cp out Main
```

If MySQL is not reachable the program prints a `[DB] WARNING` and runs normally without saving.

## 6. Saving the simulation to MySQL (JDBC)

Every action of the workers is saved as a row in the table `hospital_events`
(nurse vitals and lab requests, technician results, doctor notes and reviews,
pharmacist dispensing, plus a start and a finish row for each run).

**How it works.** `DatabaseLogger` uses JDBC (`DriverManager`, `Connection`,
`PreparedStatement`, `ResultSet`). Workers only put an event on a BlockingQueue, so the
20 threads never wait for the database. One writer thread (a single-thread
ExecutorService) takes the events and inserts them in batches inside a transaction.
A JDBC Connection is not thread-safe, so only that thread uses it. If a batch fails the
logger reconnects once and retries; if the database is down the simulation still runs.
Each run has its own `run_id`, so runs can be told apart.

**Setup (once):**

1. Run the script: `mysql -u root -p < database/hospital_db.sql` (or run it in MySQL Workbench).
2. Download MySQL Connector/J and put the `.jar` in `lib/` (see `lib/PUT_MYSQL_CONNECTOR_JAR_HERE.txt`).

**Run (PowerShell, Windows):**

```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java).FullName
$env:DB_PASSWORD = "your_mysql_password"
java -cp "out;lib/mysql-connector-j-9.1.0.jar" Main
```

Use your real jar file name. On Linux or macOS the classpath separator is `:` instead of `;`.

Settings are read from environment variables, so no password is written in the code:
`DB_URL` (default `jdbc:mysql://localhost:3306/hospital_db?...`), `DB_USER` (default `root`)
and `DB_PASSWORD` (default empty).

**Check the result in MySQL:**

```sql
USE hospital_db;
SELECT worker_type, action, COUNT(*) FROM hospital_events
WHERE run_id = (SELECT run_id FROM hospital_events ORDER BY id DESC LIMIT 1)
GROUP BY worker_type, action;
```

The final report also prints `CHECK database rows`, which compares the rows found in the
table with the events the program saved. After the report, `printRunSummary()` reads the
rows back with `SELECT` and a `ResultSet` and prints how many events of each kind this run
saved, plus the last 5 events.
