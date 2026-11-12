package workers;

import models.LabRequest;
import models.Patient;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;

public class Nurse implements Runnable {

    private int nurseId;
    private String name;
    private Patient patient;
    private final BlockingQueue<LabRequest> labRequests;

    // Constructor
    public Nurse(int nurseId, String name, Patient patient,
                 BlockingQueue<LabRequest> labRequests) {
        this.nurseId = nurseId;
        this.name = name;
        this.patient = patient;
        this.labRequests = labRequests;
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
        }
    }

    public void requestLabTest() {
        try {
            labRequests.put(new LabRequest(patient.getId(), "Blood Test"));
            System.out.println(name + " submitted a lab request for patient "
                    + patient.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
