package services;

import models.Patient;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PatientRepository {

    private final ConcurrentHashMap<Integer, Patient> patients = new ConcurrentHashMap<>();

    public boolean register(Patient patient) {
        return patients.putIfAbsent(patient.getId(), patient) == null;
    }

    public Optional<Patient> find(int id) {
        return Optional.ofNullable(patients.get(id));
    }

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

    public List<Patient> getAllPatients() {
        return new ArrayList<>(patients.values());
    }

    public int size() {
        return patients.size();
    }

    public Patient getRandomPatient() {
        List<Patient> all = getAllPatients();
        if (all.isEmpty()) {
            throw new IllegalStateException("Repository is empty");
        }
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    public static PatientRepository withSamplePatients(int count) {
        PatientRepository repo = new PatientRepository();
        for (int i = 1; i <= count; i++) {
            repo.register(new Patient(i, "Patient-" + i));
        }
        return repo;
    }
}
