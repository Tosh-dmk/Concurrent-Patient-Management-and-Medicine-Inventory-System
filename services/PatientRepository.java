package services;

import models.Patient;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared repository of all patients. ONE instance is created in Main
 * and passed to every Doctor, Nurse, LaboratoryTechnician and Pharmacist.
 */
public class PatientRepository {

    // Thread-safe map: safe to add/look up from many threads at once.
    private final ConcurrentHashMap<Integer, Patient> patients = new ConcurrentHashMap<>();

    /** Returns false if a patient with this id already exists (no overwrite). */
    public boolean register(Patient patient) {
        return patients.putIfAbsent(patient.getId(), patient) == null;
    }

    public Optional<Patient> find(int id) {
        return Optional.ofNullable(patients.get(id));
    }

    /** Returns null if absent (used by Doctor). */
    public Patient get(int id) {
        return patients.get(id);
    }

    public Patient getPatient(int id) {
        Patient p = patients.get(id);
        if (p == null) {
            throw new NoSuchElementException("No patient with id " + id);
        }
        return p;
    }

    /** Snapshot copy, so callers can loop without worrying about concurrent changes. */
    public List<Patient> getAllPatients() {
        return new ArrayList<>(patients.values());
    }

    public int size() {
        return patients.size();
    }

    /** Handy for simulations: workers pick a random patient to work on. */
    public Patient getRandomPatient() {
        List<Patient> all = getAllPatients();
        if (all.isEmpty()) {
            throw new IllegalStateException("Repository is empty");
        }
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    /** Factory to create the 100 patients required by the assignment. */
    public static PatientRepository withSamplePatients(int count) {
        PatientRepository repo = new PatientRepository();
        for (int i = 1; i <= count; i++) {
            repo.register(new Patient(i, "Patient-" + i));
        }
        return repo;
    }
}



