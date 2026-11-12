package models;

/** A laboratory test requested by a nurse for a patient. */
public class LabRequest {

    public final int patientId;
    public final String testName;
    private final boolean stopSignal;

    public LabRequest(int patientId, String testName) {
        this.patientId = patientId;
        this.testName = testName;
        this.stopSignal = false;
    }

    private LabRequest() {
        this.patientId = -1;
        this.testName = null;
        this.stopSignal = true;
    }

    // Poison pill used to stop a laboratory technician.
    public static LabRequest stopRequest() {
        return new LabRequest();
    }

    public boolean isStopSignal() {
        return stopSignal;
    }
}
