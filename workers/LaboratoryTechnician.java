class LabRequest {
    int patientId;
    String testName;

    public LabRequest(int patientId, String testName) {
        this.patientId = patientId;
        this.testName = testName;
    }
}

// LABORATORY RESULT
class LabResult {   int patientId;
    String result;

    public LabResult(int patientId, String result) {
        this.patientId = patientId;
        this.result = result;
    }
}

class LaboratoryTechnician implements Runnable {  private BlockingQueue<LabRequest> requestQueue;
    private BlockingQueue<LabResult> resultQueue;

    public LaboratoryTechnician(
        BlockingQueue<LabRequest> requests,
        BlockingQueue<LabResult> results) {

        requestQueue = requests;
        resultQueue = results;
    }