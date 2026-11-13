import integration.HospitalIntegration;
import models.DispenseRequest;
import models.Patient;
import services.LaboratoryService;
import services.MedicineInventory;
import services.PatientRepository;
import workers.Doctor;
import workers.LaboratoryTechnician;
import workers.Nurse;
import workers.Pharmacist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    static final int PATIENTS = 100, DOCTORS = 5, NURSES = 10,
            TECHNICIANS = 3, PHARMACISTS = 2, INITIAL_DOSES = 50;

    public static void main(String[] args) throws InterruptedException {

        PatientRepository repo = PatientRepository.withSamplePatients(PATIENTS);
        MedicineInventory inventory = new MedicineInventory("Paracetamol", INITIAL_DOSES);
        LaboratoryService lab = new LaboratoryService();
        BlockingQueue<DispenseRequest> dispenseQueue = new LinkedBlockingQueue<>();
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger retrieved = new AtomicInteger();

        HospitalIntegration hospital = new HospitalIntegration();
        AtomicBoolean labsDone = lab.labsDone();

        List<Future<?>> doctors = new ArrayList<>();
        List<Future<?>> nurses = new ArrayList<>();
        List<Future<?>> technicians = new ArrayList<>();
        List<Future<?>> pharmacists = new ArrayList<>();

        for (int i = 1; i <= DOCTORS; i++) {
            doctors.add(hospital.submitDoctor(
                    new Doctor(i, repo, lab.completed(), labsDone, writes, retrieved)));
        }
        for (int i = 1; i <= TECHNICIANS; i++) {
            technicians.add(hospital.submitLaboratoryTechnician(
                    new LaboratoryTechnician(i, lab.requests(), lab.completed(),
                            repo, hospital.getLaboratoryMachines())));
        }
        for (int i = 1; i <= PHARMACISTS; i++) {
            pharmacists.add(hospital.submitPharmacist(
                    new Pharmacist("Pharmacist " + i, inventory, dispenseQueue)));
        }
        for (int i = 1; i <= NURSES; i++) {
            Patient patient = repo.getPatient(i);
            nurses.add(hospital.submitNurse(
                    new Nurse(i, "Nurse-" + i, patient, lab.requests())));
        }

        for (int i = 1; i <= 20; i++) {
            dispenseQueue.put(new DispenseRequest("P" + i, 5));
        }
        for (int i = 0; i < PHARMACISTS; i++) {
            dispenseQueue.put(DispenseRequest.stopRequest());
        }

        hospital.await(nurses);
        lab.stopTechnicians(TECHNICIANS);
        hospital.await(technicians);
        lab.markLabsDone();
        hospital.await(doctors);
        hospital.await(pharmacists);
        hospital.shutdown();

        System.out.println("\n========== FINAL REPORT ==========");
        int labResultsStored = 0;
        for (Patient p : repo.getAllPatients()) {
            labResultsStored += p.getLabResults().size();
        }
        System.out.println("Lab requests (nurses)  : " + NURSES);
        System.out.println("Lab results stored     : " + labResultsStored);
        System.out.println("Lab results reviewed   : " + retrieved.get());
        System.out.println("Doctor record writes   : " + writes.get());
        System.out.println("Medicine remaining     : " + inventory.getAvailableDoses()
                + " (never negative)");
        int notesStored = 0, inconsistent = 0;
        for (Patient p : repo.getAllPatients()) {
            notesStored += p.getNoteCount();
            if (!p.isConsistent())
                inconsistent++;
        }
        boolean stockOk = inventory.getAvailableDoses() >= 0
                && inventory.getTotalDispensed() + inventory.getAvailableDoses() == INITIAL_DOSES;
        System.out.println("Doses dispensed        : " + inventory.getTotalDispensed());
        System.out.println("CHECK stock balanced   : " + (stockOk ? "PASS" : "FAIL"));
        System.out.println("CHECK no lost writes   : "
                + (notesStored == writes.get() ? "PASS" : "FAIL")
                + " (notes stored " + notesStored + " / doctor writes " + writes.get() + ")");
        System.out.println("CHECK records consistent: "
                + (inconsistent == 0 ? "PASS" : "FAIL (" + inconsistent + " bad)"));
        System.out.println("Sample record          : " + repo.getPatient(1).getSummary());
    }
}
