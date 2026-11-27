package workers;

import models.DispenseRequest;
import services.DatabaseLogger;
import services.MedicineInventory;

import java.util.concurrent.BlockingQueue;

public class Pharmacist implements Runnable {

    private final String pharmacistId;
    private final MedicineInventory inventory;
    private final BlockingQueue<DispenseRequest> requests;
    private DatabaseLogger logger = DatabaseLogger.disabled();

    public Pharmacist(
        String pharmacistId,
        MedicineInventory inventory,
        BlockingQueue<DispenseRequest> requests
    ) {
        this.pharmacistId = pharmacistId;
        this.inventory = inventory;
        this.requests = requests;
    }

    /** Optional: record this pharmacist's actions in the database. */
    public Pharmacist withLogger(DatabaseLogger logger) {
        this.logger = logger;
        return this;
    }

    @Override
    public void run() {
        // Show the pharmacist's name (not "pool-1-thread-N") in all log lines.
        String originalName = Thread.currentThread().getName();
        Thread.currentThread().setName(pharmacistId);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                DispenseRequest request = requests.take();

                if (request.isStopSignal()) {
                    break;
                }

                System.out.println(
                    pharmacistId + " processing request for patient "
                    + request.getPatientId()
                );

                boolean successful = inventory.dispenseMedicine(
                    request.getRequestedDoses()
                );

                System.out.println(
                    pharmacistId + (successful
                        ? " successfully served patient "
                        : " could not serve patient ")
                    + request.getPatientId()
                );

                logger.log("PHARMACIST", pharmacistId, request.getPatientId(),
                    successful ? "MEDICINE_DISPENSED" : "DISPENSE_REFUSED",
                    request.getRequestedDoses() + " doses requested");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(pharmacistId + " was interrupted.");
        } finally {
            Thread.currentThread().setName(originalName);
        }
    }
}
