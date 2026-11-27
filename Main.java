import integration.HospitalIntegration;
import models.DispenseRequest;
import models.Patient;
import services.DatabaseLogger;
import services.LaboratoryService;
import services.MedicineInventory;
import services.PatientRepository;
import workers.Doctor;
import workers.LaboratoryTechnician;
import workers.Nurse;
import workers.Pharmacist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    static final int PATIENTS = 100, DOCTORS = 5, NURSES = 10,
                     TECHNICIANS = 3, PHARMACISTS = 2, INITIAL_DOSES = 50;

    public static void main(String[] args) throws InterruptedException {

        // ---- shared resources: each created ONCE and passed to everyone ----
        PatientRepository repo = PatientRepository.withSamplePatients(PATIENTS);
        MedicineInventory inventory = new MedicineInventory("Paracetamol", INITIAL_DOSES);
        LaboratoryService lab = new LaboratoryService();
        BlockingQueue<DispenseRequest> dispenseQueue = new LinkedBlockingQueue<>();
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger retrieved = new AtomicInteger();

        // ---- database (JDBC): settings come from environment variables ----
        // DB_URL, DB_USER, DB_PASSWORD (defaults: local MySQL, user root, empty password)
        DatabaseLogger db = DatabaseLogger.connect(
            env("DB_URL", "jdbc:mysql://localhost:3306/hospital_db"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"),
            env("DB_USER", "root"),
            env("DB_PASSWORD", ""));
        db.log("SYSTEM", "Main", null, "RUN_STARTED",
            PATIENTS + " patients, " + DOCTORS + " doctors, " + NURSES + " nurses, "
            + TECHNICIANS + " technicians, " + PHARMACISTS + " pharmacists, "
            + INITIAL_DOSES + " doses");

        HospitalIntegration hospital = new HospitalIntegration();
        AtomicBoolean labsDone = lab.labsDone();

        List<Future<?>> doctors = new ArrayList<>();
        List<Future<?>> nurses = new ArrayList<>();
        List<Future<?>> technicians = new ArrayList<>();
        List<Future<?>> pharmacists = new ArrayList<>();

        // ---- submit all 20 workers to the thread pool ----
        for (int i = 1; i <= DOCTORS; i++) {
            doctors.add(hospital.submitDoctor(
                new Doctor(i, repo, lab.completed(), labsDone, writes, retrieved)
                    .withLogger(db)));
        }
        for (int i = 1; i <= TECHNICIANS; i++) {
            technicians.add(hospital.submitLaboratoryTechnician(
                new LaboratoryTechnician(i, lab.requests(), lab.completed(),
                                         repo, hospital.getLaboratoryMachines())
                    .withLogger(db)));
        }
        for (int i = 1; i <= PHARMACISTS; i++) {
            pharmacists.add(hospital.submitPharmacist(
                new Pharmacist("Pharmacist " + i, inventory, dispenseQueue)
                    .withLogger(db)));
        }
        for (int i = 1; i <= NURSES; i++) {
            Patient patient = repo.getPatient(i);
            nurses.add(hospital.submitNurse(
                new Nurse(i, "Nurse-" + i, patient, lab.requests())
                    .withLogger(db)));
        }

        // ---- patients request the same medication: 20 x 5 doses vs 50 in stock ----
        for (int i = 1; i <= 20; i++) {
            dispenseQueue.put(new DispenseRequest("P" + i, 5));
        }
        for (int i = 0; i < PHARMACISTS; i++) {
            dispenseQueue.put(DispenseRequest.stopRequest());
        }

        // ---- orderly shutdown: nurses -> technicians -> doctors, pharmacists ----
        hospital.await(nurses);                  // all lab requests are now queued
        lab.stopTechnicians(TECHNICIANS);        // pills sit behind the real requests
        hospital.await(technicians);
        lab.markLabsDone();                      // doctors may now finish
        hospital.await(doctors);
        hospital.await(pharmacists);
        hospital.shutdown();

        // ---- finish the database: last event, then wait until everything is saved ----
        db.log("SYSTEM", "Main", null, "RUN_FINISHED",
            "doses dispensed " + inventory.getTotalDispensed()
            + ", doses left " + inventory.getAvailableDoses()
            + ", lab results reviewed " + retrieved.get()
            + ", doctor writes " + writes.get());
        db.close();

        // ---- final report ----
        System.out.println("\n========== FINAL REPORT ==========");
        int labResultsStored = 0;
        for (Patient p : repo.getAllPatients()) {
            labResultsStored += p.getLabResults().size();
        }
        System.out.println("Lab requests (nurses)  : " + NURSES);
        System.out.println("Lab results stored     : " + labResultsStored);
        System.out.println("Lab results reviewed   : " + retrieved.get());
        System.out.println("Doctor record writes   : " + writes.get());
        System.out.println("Medicine remaining     : " + inventory.getAvailableDoses()
                           + " (never negative)");
        int notesStored = 0, inconsistent = 0;
        for (Patient p : repo.getAllPatients()) {
            notesStored += p.getNoteCount();
            if (!p.isConsistent()) inconsistent++;
        }
        boolean stockOk = inventory.getAvailableDoses() >= 0
            && inventory.getTotalDispensed() + inventory.getAvailableDoses() == INITIAL_DOSES;
        System.out.println("Doses dispensed        : " + inventory.getTotalDispensed());
        System.out.println("CHECK stock balanced   : " + (stockOk ? "PASS" : "FAIL"));
        System.out.println("CHECK no lost writes   : "
            + (notesStored == writes.get() ? "PASS" : "FAIL")
            + " (notes stored " + notesStored + " / doctor writes " + writes.get() + ")");
        System.out.println("CHECK records consistent: "
            + (inconsistent == 0 ? "PASS" : "FAIL (" + inconsistent + " bad)"));
        if (db.isEnabled()) {
            int rows = db.countRowsInDatabase();
            boolean dbOk = rows == db.getQueued() && db.getInserted() == db.getQueued()
                && db.getFailed() == 0 && db.getDropped() == 0;
            System.out.println("Database events saved  : " + rows + " rows in hospital_events ("
                + db.getRunId() + ")");
            System.out.println("CHECK database rows    : " + (dbOk ? "PASS" : "FAIL")
                + " (queued " + db.getQueued() + ", saved " + db.getInserted()
                + ", failed " + db.getFailed() + ", dropped " + db.getDropped() + ")");
        } else {
            System.out.println("Database               : not connected (events were not saved)");
        }
        System.out.println("Sample record          : " + repo.getPatient(1).getSummary());

        db.printRunSummary();   // read the saved rows back from MySQL
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
