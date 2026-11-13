package integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class HospitalIntegration {

    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    private final Semaphore laboratoryMachines = new Semaphore(2, true);

    private final List<Future<?>> tasks = new ArrayList<Future<?>>();

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

    public Future<?> submitLaboratoryTechnician(Runnable technician) {
        Future<?> future = executor.submit(technician);
        tasks.add(future);
        return future;
    }

    public Semaphore getLaboratoryMachines() {
        return laboratoryMachines;
    }

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
