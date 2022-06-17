package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.UUID;

public class InstrumentationInfo {

    public UUID id;
    public boolean done = false;
    public long pixels = 0;
    public long bbls = 0;

    public InstrumentationInfo(long pixels) {
        this.pixels = pixels;
        id = UUID.randomUUID();
    }

    @Override
    public String toString() {
        return String.format("id: %s\tdone: %s\tpixels: %s\tbbls: %s", id, done, pixels, bbls);
    }

}
