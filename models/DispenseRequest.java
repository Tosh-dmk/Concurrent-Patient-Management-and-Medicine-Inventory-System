package models;

public class DispenseRequest {

    private final String patientId;
    private final int requestedDoses;
    private final boolean stopSignal;

    public DispenseRequest(String patientId, int requestedDoses) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID is required.");
        }
        if (requestedDoses <= 0) {
            throw new IllegalArgumentException(
                    "Requested doses must be greater than zero.");
        }

        this.patientId = patientId;
        this.requestedDoses = requestedDoses;
        this.stopSignal = false;
    }

    private DispenseRequest() {
        this.patientId = null;
        this.requestedDoses = 0;
        this.stopSignal = true;
    }

    public static DispenseRequest stopRequest() {
        return new DispenseRequest();
    }

    public String getPatientId() {
        return patientId;
    }

    public int getRequestedDoses() {
        return requestedDoses;
    }

    public boolean isStopSignal() {
        return stopSignal;
    }
}
