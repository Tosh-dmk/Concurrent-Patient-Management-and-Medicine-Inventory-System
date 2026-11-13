package services;

import models.LabRequest;
import models.LabResult;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LaboratoryService {

    private final BlockingQueue<LabRequest> requests = new LinkedBlockingQueue<>();
    private final BlockingQueue<LabResult> completed = new LinkedBlockingQueue<>();
    private final AtomicBoolean labsDone = new AtomicBoolean(false);

    public BlockingQueue<LabRequest> requests() {
        return requests;
    }

    public BlockingQueue<LabResult> completed() {
        return completed;
    }

    public AtomicBoolean labsDone() {
        return labsDone;
    }

    public void stopTechnicians(int technicianCount) throws InterruptedException {
        for (int i = 0; i < technicianCount; i++) {
            requests.put(LabRequest.stopRequest());
        }
    }

    public void markLabsDone() {
        labsDone.set(true);
    }
}
