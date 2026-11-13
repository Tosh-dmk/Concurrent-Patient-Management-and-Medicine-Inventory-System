package workers;

import models.LabRequest;
import models.LabResult;
import models.Patient;
import services.PatientRepository;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

public class LaboratoryTechnician implements Runnable {

    private final int id;
    private final BlockingQueue<LabRequest> requestQueue;
    private final BlockingQueue<LabResult> resultQueue;
    private final PatientRepository repo;
    private final Semaphore laboratoryMachines;

    public LaboratoryTechnician(
            int id,
            BlockingQueue<LabRequest> requests,
            BlockingQueue<LabResult> results,
            PatientRepository repo,
            Semaphore laboratoryMachines) {

        this.id = id;
        this.requestQueue = requests;
        this.resultQueue = results;
        this.repo = repo;
        this.laboratoryMachines = laboratoryMachines;
    }

    @Override
    public void run() {
        try {
            while (true) {
                LabRequest request = requestQueue.take();
                if (request.isStopSignal()) {
                    break;
                }

                laboratoryMachines.acquire();
                try {
                    System.out.println("Technician " + id + " processing "
                            + request.testName + " for patient " + request.patientId);
                    Thread.sleep(100); // simulate the machine running the test

                    LabResult result = new LabResult(
                            request.patientId, request.testName, request.testName + ": normal");

                    Patient patient = repo.get(request.patientId);
                    if (patient != null) {
                        patient.addLabResult(result);
                    }
                    resultQueue.put(result);
                } finally {
                    laboratoryMachines.release();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Technician " + id + " was interrupted.");
        }
        System.out.println("Technician " + id + " finished.");
    }
}
