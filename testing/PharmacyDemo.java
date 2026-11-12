package testing;

import models.DispenseRequest;
import services.MedicineInventory;
import workers.Pharmacist;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PharmacyDemo {

    public static void main(String[] args) throws InterruptedException {
        MedicineInventory inventory =
            new MedicineInventory("Paracetamol", 50);

        BlockingQueue<DispenseRequest> requests =
            new LinkedBlockingQueue<DispenseRequest>();

        Pharmacist pharmacist1 =
            new Pharmacist("Pharmacist 1", inventory, requests);
        Pharmacist pharmacist2 =
            new Pharmacist("Pharmacist 2", inventory, requests);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(pharmacist1);
        executor.submit(pharmacist2);

        requests.put(new DispenseRequest("P001", 30));
        requests.put(new DispenseRequest("P002", 30));

        // One stop request for each pharmacist worker.
        requests.put(DispenseRequest.stopRequest());
        requests.put(DispenseRequest.stopRequest());

        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        System.out.println(
            "\nFINAL INVENTORY: " + inventory.getAvailableDoses()
        );
    }
}
