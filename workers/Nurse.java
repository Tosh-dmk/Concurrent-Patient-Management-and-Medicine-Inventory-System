package workers;

import models.LabRequest;
import models.Patient;
import services.DatabaseLogger;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;

public class Nurse implements Runnable {

    private int nurseId;
    private String name;
    private Patient patient;
    private final BlockingQueue<LabRequest> labRequests;
    private DatabaseLogger logger = DatabaseLogger.disabled();

    // Constructor
    public Nurse(int nurseId, String name, Patient patient,
                 BlockingQueue<LabRequest> labRequests) {
        this.nurseId = nurseId;
        this.name = name;
        this.patient = patient;
        this.labRequests = labRequests;
    }

    /** Optional: record this nurse's actions in the database. */
    public Nurse withLogger(DatabaseLogger logger) {
        this.logger = logger;
        return this;
    }

    public int getNurseId() {
        return nurseId;
    }

    @Override
    public void run() {
        updatePatientVitals();
        requestLabTest();
    }

    public void updatePatientVitals() {

        // Intrinsic lock on the patient: one nurse at a time per patient, so
        // the console log of a vitals update is not interleaved. (Data safety
        // itself is guaranteed by the write lock inside Patient.)
        synchronized (patient) {

            patient.updateVitals(new Patient.VitalSigns(
                    75, 36.5, 120, 80, LocalDateTime.now()));

            // One line per update, so output from different nurses stays readable.
            System.out.println("Nurse ID: " + nurseId + " (" + name
                    + ") updated vitals for patient " + patient.getId());
            logger.log("NURSE", name, patient.getId(), "VITALS_UPDATED",
                    "HR 75, Temp 36.5, BP 120/80");
        }
    }

    public void requestLabTest() {
        try {
            labRequests.put(new LabRequest(patient.getId(), "Blood Test"));
            System.out.println(name + " submitted a lab request for patient "
                    + patient.getId());
            logger.log("NURSE", name, patient.getId(), "LAB_REQUESTED", "Blood Test");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
