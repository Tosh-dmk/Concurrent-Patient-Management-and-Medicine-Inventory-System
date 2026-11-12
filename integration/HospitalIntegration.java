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

    public Future<?> submitDoctor(Runnable doctor) {
        Future<?> future = executor.submit(doctor);
        tasks.add(future);
        return future;
    }

    public Future<?> submitNurse(Runnable nurse) {
        Future<?> future = executor.submit(nurse);
        tasks.add(future);
        return future;
    }

    public Future<?> submitPharmacist(Runnable pharmacist) {
        Future<?> future = executor.submit(pharmacist);
        tasks.add(future);
        return future;
    }

    // The technician acquires/releases a machine permit around EACH request
    // (see LaboratoryTechnician), so the permit is never held for the whole
    // lifetime of a worker thread.
    public Future<?> submitLaboratoryTechnician(Runnable technician) {
        Future<?> future = executor.submit(technician);
        tasks.add(future);
        return future;
    }

    public Semaphore getLaboratoryMachines() {
        return laboratoryMachines;
    }

    // Wait for a specific group of tasks (e.g. all nurses).
    public void await(List<Future<?>> group) {
        for (Future<?> task : group) {
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
