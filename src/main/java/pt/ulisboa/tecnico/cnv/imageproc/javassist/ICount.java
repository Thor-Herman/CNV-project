package pt.ulisboa.tecnico.cnv.imageproc.javassist;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import pt.ulisboa.tecnico.cnv.imageproc.InstrumentationInfo;
import pt.ulisboa.tecnico.cnv.imageproc.WebServer;

public class ICount extends AbstractJavassistTool {

    /**
     * Number of executed basic blocks.
     */
    private static long nblocks = 0;

    /**
     * Number of executed methods.
     */
    private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length) {
        nblocks++;
        ninsts += length;
    }

    public static void incBehavior(String name) {
        nmethods++;
    }

    public static void resetBBLs() {
        nblocks = 0;
    }

    public static void printStatistics() {
        System.out
                .println(String.format("[%s] Number of executed methods: %s", ICount.class.getSimpleName(), nmethods));
        System.out.println(
                String.format("[%s] Number of executed basic blocks: %s", ICount.class.getSimpleName(), nblocks));
        System.out.println(
                String.format("[%s] Number of executed instructions: %s", ICount.class.getSimpleName(), ninsts));
    }

    public static void addBBLs() {
        List<InstrumentationInfo> info = WebServer.processingThreads.get(Thread.currentThread().getId());
        info.get(info.size() - 1).bbls = nblocks; // Last added instrumentationInfo in this thread will be the one we created 
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));

        if (behavior.getName().equals("main")) {
            behavior.insertAfter(String.format("%s.printStatistics();", ICount.class.getName()));
        }
        if (behavior.getName().equals("handleRequest")) {
            behavior.insertBefore(String.format("%s.resetBBLs();", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.addBBLs();", ICount.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength()));
    }

}
