package pt.ulisboa.tecnico.cnv.imageproc;

public class VM {
    public boolean markedForDeletion;
    public String ipAddress;
    public String id;
    public int currentAmountOfRequests;

    public VM(String id, String ipAddress, boolean markedForDeletion) {
        this.id = id;
        this.markedForDeletion = markedForDeletion;
        this.ipAddress = ipAddress;
        this.currentAmountOfRequests = 0; // TODO: This is not necessarily true if the autoscaler is started later
    }

    @Override
    public String toString() {
        return id + "  ip: " + ipAddress + "  marked for deletion: " + Boolean.toString(markedForDeletion);
    }
}
