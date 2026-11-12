# Concurrent Patient Management and Medicine Inventory System

**OOP 2 Practical - Threads, Problem 2 (Group 11-15)**

## 1. The problem

A hospital runs a centralized patient management system used at the same time by
doctors, nurses, laboratory technicians and pharmacists. The system must make sure
that concurrent operations never corrupt patient records and never produce a wrong
medicine inventory level.

Setup required by the question, and used in this program:

| Resource | Count |
|---|---|
| Registered patients | 100 |
| Doctors | 5 |
| Nurses | 10 |
| Laboratory technicians | 3 |
| Pharmacists | 2 |
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

## 3. How it answers Question 2

### Programming tasks

| Task | Where it is answered |
|---|---|
| a. Classes Patient, Doctor, Nurse, LaboratoryTechnician, Pharmacist, MedicineInventory | `models/Patient`, `workers/Doctor`, `workers/Nurse`, `workers/LaboratoryTechnician`, `workers/Pharmacist`, `services/MedicineInventory` |
| b. Separate threads for the different healthcare workers | Every worker implements `Runnable` and runs on its own pool thread |
| c. Shared patient record repository used by multiple threads | `services/PatientRepository` (a `ConcurrentHashMap`), one instance shared by all workers |
| d. Dispensing that keeps inventory from going negative | `MedicineInventory.dispenseMedicine()`: the stock check and the deduction happen in one locked step |
| e. Java concurrency mechanisms | See the table below |
| f. Laboratory request queue | Nurses submit, technicians process concurrently, doctors retrieve results (section 2) |
| g. Patient records stay consistent under simultaneous access | `Patient` uses a fair read/write lock; every update is atomic and readers get a consistent snapshot |

### Concurrency mechanisms (task e)

| Mechanism | Where | Purpose |
|---|---|---|
| `synchronized` | `Nurse.updatePatientVitals()` | One nurse at a time works on a given patient |
| `ReentrantLock` | `MedicineInventory` | Makes check-and-deduct of medicine one atomic step |
| `ReentrantReadWriteLock` | `Patient` | Many readers or a single writer; keeps each record consistent |
| `ExecutorService` | `integration/HospitalIntegration` | Fixed pool of 20 threads for the 20 workers |
| `BlockingQueue` | `services/LaboratoryService`, dispense queue in `Main` | Producer-consumer communication between workers |
| `Semaphore` | `HospitalIntegration` / `LaboratoryTechnician` | Limits the laboratory to 2 machines |

### Questions for students

1. **Two doctors updating the same record.** Without protection, one write can overwrite
   the other (lost update). `Patient` takes a write lock for every update, so only one
   doctor changes the record at a time.
2. **Semaphore and limited resources.** A semaphore holds one permit per machine. A
   technician acquires a permit before each test and releases it afterwards, so at most
   2 of the 3 technicians use a machine at once.
3. **BlockingQueue between nurses and technicians.** Nurses `put()` requests and
   technicians `take()` them, blocking when the queue is empty. Neither side waits for
   the other directly.
4. **Manual threads vs ExecutorService.** The executor reuses a fixed pool of threads,
   returns a `Future` per task and shuts down cleanly, instead of creating and managing
   a thread per task by hand.
5. **Never dispensing more than the inventory.** `ReentrantLock` makes the "enough
   stock?" check and the deduction one atomic step, so two pharmacists can never both
   take the same doses.
6. **Preventing starvation of lab requests.** The lock, read/write lock and semaphore
   are all created fair, and the request queue is FIFO, so the longest-waiting request or
   thread is served first.

## 4. Proof that it works

When the program finishes, `Main` prints a final report with three checks:

- **Stock balanced:** doses dispensed plus doses remaining equals 50, and stock is never negative.
- **No lost writes:** the notes stored in patient records equal the number of doctor writes.
- **Records consistent:** every patient's version number matches the number of changes made to it.

## 5. Assumptions

- The question does not say how many laboratory machines exist. This system assumes **2**.
- The question does not give the medicine requests. The demo has 20 patients each
  requesting 5 doses against a stock of 50, so 10 are served and 10 are refused.

## 6. Project structure

```
Main.java                 wires everything together and prints the report
models/                   Patient, LabRequest, LabResult, DispenseRequest
workers/                  Doctor, Nurse, LaboratoryTechnician, Pharmacist
services/                 PatientRepository, MedicineInventory, LaboratoryService
integration/              HospitalIntegration (thread pool and lab machine semaphore)
```

## 7. Build and run

Requires JDK 16 or newer (`Patient` uses Java records).

```bash
javac -d out $(find . -name "*.java")
java -cp out Main
```
