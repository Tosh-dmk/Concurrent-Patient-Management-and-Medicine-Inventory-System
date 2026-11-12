public class Nurse extends Thread {

    private int nurseId;
    private String name;
    private Patient patient;

    // Constructor
    public Nurse(int nurseId, String name, Patient patient) {
        this.nurseId = nurseId;
        this.name = name;
        this.patient = patient;
    }

    public int getNurseId() {
        return nurseId;
    }

    @Override
    public void run() {
        updatePatientVitals();
    }

    public void updatePatientVitals() {

        synchronized (patient) {

            System.out.println("Nurse ID: " + nurseId + " (" + name
                    + ") is updating patient vitals.");

            patient.setBloodPressure("120/80");
            patient.setTemperature(36.5);
            patient.setHeartRate(75);

            System.out.println(name + " successfully updated patient vitals.");
        }
    }
}
