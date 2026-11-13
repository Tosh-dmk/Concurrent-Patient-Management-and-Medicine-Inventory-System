package models;

import java.time.LocalDateTime;

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
