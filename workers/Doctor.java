package workers;

import models.LabResult;
import models.Patient;
import services.PatientRepository;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class Doctor implements Runnable {

    private final int id;
    private final PatientRepository repo;
    private final BlockingQueue<LabResult> completed;

    private final AtomicBoolean labsDone;
    private final AtomicInteger writes;
    private final AtomicInteger retrieved;

    
    public Doctor(int id,
                  PatientRepository repo,
                  BlockingQueue<LabResult> completed,
                  AtomicBoolean labsDone,
                  AtomicInteger writes,
                  AtomicInteger retrieved) {

        this.id = id;
        this.repo = repo;
        this.completed = completed;
        this.labsDone = labsDone;
        this.writes = writes;
        this.retrieved = retrieved;
    }

    @Override
    public void run() {

        Random random = new Random();

        try {

            while (true) {

                
                boolean done = labsDone.get();

            
                int patientCount = repo.size();

                if (patientCount > 0) {

                    int patientId = 1 + random.nextInt(patientCount);

                    Patient patient = repo.get(patientId);

                    if (patient != null) {

                        patient.addNote(
                            "Dr-" + id,
                            "Routine review"
                        );

                        writes.incrementAndGet();
                    }
                }

                
                LabResult result = completed.poll(
                    50, TimeUnit.MILLISECONDS
                );

                if (result != null) {

                    Patient patient = repo.get(result.patientId);

                    if (patient != null) {

                        patient.addNote(
                            "Dr-" + id,
                            "Reviewed -> " + result.text
                        );

                        writes.incrementAndGet();
                        retrieved.incrementAndGet();

                    } else {
                        System.out.println(
                            "Patient record not found for ID: " + result.patientId
                        );
                    }

                } else if (done && completed.isEmpty()) {

                
                    break;
                }
            }

        } catch (InterruptedException e) {

            System.out.println(
                "Doctor " + id + " was interrupted."
            );

            Thread.currentThread().interrupt();
        }

        System.out.println("Dr-" + id + " finished.");
    }
}
