package models;

import java.time.LocalDateTime;

/** Completed laboratory result, produced by technicians and read by doctors. */
public class LabResult {

    public final int patientId;
    public final String testName;
    public final String text;
    public final LocalDateTime completedAt;

    public LabResult(int patientId, String testName, String text) {
        this.patientId = patientId;
        this.testName = testName;
        this.text = text;
        this.completedAt = LocalDateTime.now();
    }
}
