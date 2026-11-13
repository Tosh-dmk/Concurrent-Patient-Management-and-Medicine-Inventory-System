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
        synchronized (patient) {

            patient.updateVitals(new Patient.VitalSigns(
                    75, 36.5, 120, 80, LocalDateTime.now()));

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
