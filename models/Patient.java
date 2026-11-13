package models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Patient {

    public record VitalSigns(int heartRate, double temperature,
            int systolicBP, int diastolicBP,
            LocalDateTime recordedAt) {
    }

    public record ClinicalNote(String doctorName, String note,
            LocalDateTime writtenAt) {
    }

    private final int id;
    private final String name;

    private VitalSigns latestVitals;
    private final List<VitalSigns> vitalsHistory = new ArrayList<>();
    private final List<LabResult> labResults = new ArrayList<>();
    private final List<ClinicalNote> notes = new ArrayList<>();
    private String diagnosis = "Pending";
    private int version = 0; // incremented on every write

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); // fair, avoids starvation
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public Patient(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void updateVitals(VitalSigns vitals) {
        writeLock.lock();
        try {
            latestVitals = vitals;
            vitalsHistory.add(vitals);
            version++;
        } finally {
            writeLock.unlock();
        }
    }

    public void updateDiagnosis(String doctorName, String newDiagnosis, String note) {
        writeLock.lock();
        try {
            diagnosis = newDiagnosis;
            notes.add(new ClinicalNote(doctorName, note, LocalDateTime.now()));
            version++;
        } finally {
            writeLock.unlock();
        }
    }

    public void addNote(String doctorName, String note) {
        writeLock.lock();
        try {
            notes.add(new ClinicalNote(doctorName, note, LocalDateTime.now()));
            version++;
        } finally {
            writeLock.unlock();
        }
    }

    public void addLabResult(LabResult result) {
        writeLock.lock();
        try {
            labResults.add(result);
            version++;
        } finally {
            writeLock.unlock();
        }
    }

    public VitalSigns getLatestVitals() {
        readLock.lock();
        try {
            return latestVitals;
        } finally {
            readLock.unlock();
        }
    }

    public String getDiagnosis() {
        readLock.lock();
        try {
            return diagnosis;
        } finally {
            readLock.unlock();
        }
    }

    public List<LabResult> getLabResults() {
        readLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(labResults)); // defensive copy
        } finally {
            readLock.unlock();
        }
    }

    public int getNoteCount() {
        readLock.lock();
        try {
            return notes.size();
        } finally {
            readLock.unlock();
        }
    }

    public boolean isConsistent() {
        readLock.lock();
        try {
            return version == vitalsHistory.size() + notes.size() + labResults.size();
        } finally {
            readLock.unlock();
        }
    }

    public int getVersion() {
        readLock.lock();
        try {
            return version;
        } finally {
            readLock.unlock();
        }
    }

    public String getSummary() {
        readLock.lock();
        try {
            return String.format(
                    "Patient[%d, %s] v%d | diagnosis=%s | vitals=%s | notes=%d | labs=%d",
                    id, name, version, diagnosis, latestVitals, notes.size(), labResults.size());
        } finally {
            readLock.unlock();
        }
    }
}
