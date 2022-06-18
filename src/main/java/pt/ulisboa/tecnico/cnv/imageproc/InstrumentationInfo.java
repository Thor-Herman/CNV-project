package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.UUID;

public class InstrumentationInfo {

    public UUID id;
    public boolean done = false;
    public long pixels = 0;
    public long bbls = 0;
    public String path = "";

    public InstrumentationInfo(long pixels, String path) {
        this.pixels = pixels;
        this.path = path;
        id = UUID.randomUUID();
    }

    @Override
    public String toString() {
        return String.format("id: %s\tdone: %s\tpixels: %s\tbbls: %s", id, done, pixels, bbls);
    }

}
