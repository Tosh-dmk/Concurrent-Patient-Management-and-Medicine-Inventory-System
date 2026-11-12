package integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class HospitalIntegration {

    // The assignment scenario has 20 healthcare workers in total.
    private final ExecutorService executor =
        Executors.newFixedThreadPool(20);

    // Assumption for the simulation: the hospital has two lab machines.
    // Change the permit count if the group agrees on a different resource.
    private final Semaphore laboratoryMachines =
        new Semaphore(2, true);

    private final List<Future<?>> tasks =
        new ArrayList<Future<?>>();

    public void submitDoctor(Runnable doctor) {
        tasks.add(executor.submit(doctor));
    }

    public void submitNurse(Runnable nurse) {
        tasks.add(executor.submit(nurse));
    }

    public void submitPharmacist(Runnable pharmacist) {
        tasks.add(executor.submit(pharmacist));
    }

    public void submitLaboratoryTechnician(Runnable technician) {
        tasks.add(executor.submit(() -> {
            boolean acquired = false;
            try {
                laboratoryMachines.acquire();
                acquired = true;

                System.out.println(
                    Thread.currentThread().getName()
                    + " acquired a laboratory machine."
                );

                technician.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    laboratoryMachines.release();
                    System.out.println(
                        Thread.currentThread().getName()
                        + " released a laboratory machine."
                    );
                }
            }
        }));
    }

    // Wait for submitted one-shot tasks. Long-running workers should be
    // stopped (for example, with poison-pill queue messages) before this call.
    public void awaitCompletion() {
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                System.err.println("Worker task failed: " + e.getCause());
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
