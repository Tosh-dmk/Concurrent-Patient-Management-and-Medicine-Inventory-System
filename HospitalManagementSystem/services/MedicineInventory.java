package services;

import java.util.concurrent.locks.ReentrantLock;

public class MedicineInventory {

    private final String medicineName;
    private int availableDoses;

    // A fair lock helps waiting pharmacists acquire access in queue order.
    private final ReentrantLock lock = new ReentrantLock(true);

    public MedicineInventory(String medicineName, int initialDoses) {
        if (initialDoses < 0) {
            throw new IllegalArgumentException(
                "Initial inventory cannot be negative."
            );
        }

        this.medicineName = medicineName;
        this.availableDoses = initialDoses;
    }

    // The stock check and deduction happen in one critical section.
    public boolean dispenseMedicine(int requestedDoses) {
        if (requestedDoses <= 0) {
            throw new IllegalArgumentException(
                "Requested doses must be greater than zero."
            );
        }

        lock.lock();
        try {
            System.out.println(
                Thread.currentThread().getName()
                + " attempting to dispense " + requestedDoses + " doses."
            );

            if (availableDoses < requestedDoses) {
                System.out.println("Insufficient medicine available.");
                return false;
            }

            availableDoses -= requestedDoses;

            System.out.println(
                "Dispensed " + requestedDoses + " doses of "
                + medicineName + ". Remaining: " + availableDoses
            );
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int getAvailableDoses() {
        lock.lock();
        try {
            return availableDoses;
        } finally {
            lock.unlock();
        }
    }

    public String getMedicineName() {
        return medicineName;
    }
}
