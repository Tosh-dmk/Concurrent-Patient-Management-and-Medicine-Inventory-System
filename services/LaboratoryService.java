package services;

import models.LabRequest;
import models.LabResult;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the laboratory queues. ONE instance is created in Main.
 *   Nurses      -> requests()  -> Technicians
 *   Technicians -> completed() -> Doctors
 */
public class LaboratoryService {

    private final BlockingQueue<LabRequest> requests = new LinkedBlockingQueue<>();
    private final BlockingQueue<LabResult> completed = new LinkedBlockingQueue<>();
    private final AtomicBoolean labsDone = new AtomicBoolean(false);

    public BlockingQueue<LabRequest> requests() { return requests; }

    public BlockingQueue<LabResult> completed() { return completed; }

    public AtomicBoolean labsDone() { return labsDone; }

    /** One poison pill per technician, queued behind all real requests. */
    public void stopTechnicians(int technicianCount) throws InterruptedException {
        for (int i = 0; i < technicianCount; i++) {
            requests.put(LabRequest.stopRequest());
        }
    }

    /** Call after all technicians have finished so doctors know no more results will arrive. */
    public void markLabsDone() { labsDone.set(true); }
}
