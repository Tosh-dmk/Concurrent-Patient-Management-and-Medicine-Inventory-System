package models;

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

    public static LabRequest stopRequest() {
        return new LabRequest();
    }

    public boolean isStopSignal() {
        return stopSignal;
    }
}
