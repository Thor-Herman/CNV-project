package pt.ulisboa.tecnico.cnv.imageproc;

public class VM {
    public boolean markedForDeletion;
    public String ipAddress;
    public String id;
    public int currentAmountOfRequests;
    public VMState state;

    public VM(String id, String ipAddress, boolean markedForDeletion, VMState state) {
        this.id = id;
        this.markedForDeletion = markedForDeletion;
        this.ipAddress = ipAddress;
        this.currentAmountOfRequests = 0; // TODO: This is not necessarily true if the autoscaler is started later
        this.state = state;
    }

    @Override
    public String toString() {
        return String.format("id:%s  ip:%s  marked for deletion:%s  no. requests:%s", id, ipAddress,
                Boolean.toString(markedForDeletion), currentAmountOfRequests);
    }
}
