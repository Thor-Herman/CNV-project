package pt.ulisboa.tecnico.cnv.imageproc;

public class VM {
    public boolean markedForDeletion;
    public String ipAddress;
    public String id;
    public int currentAmountOfRequests;
    public VMState state;
    public int cyclesSinceHealthCheck;
    public long bblsAssumedToBeProcessing;
    public double cpuUtilization;

    public VM(String id, String ipAddress, boolean markedForDeletion, VMState state) {
        this.id = id;
        this.markedForDeletion = markedForDeletion;
        this.ipAddress = ipAddress;
        this.state = state;
        this.currentAmountOfRequests = 0; // TODO: This is not necessarily true if the autoscaler is started later
        this.bblsAssumedToBeProcessing = 0;
        this.cyclesSinceHealthCheck = 0;
        this.cpuUtilization = 0;
    }

    @Override
    public String toString() {
        return String.format("id:%s  ip:%s  marked for deletion:%s  no. requests:%s   state:%s", id, ipAddress,
                Boolean.toString(markedForDeletion), currentAmountOfRequests, state);
    }
}
