package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;

import javassist.tools.web.Webserver;

public class MSSConnector implements Runnable {

    public static final int PUSH_TO_MSS_INTERVAL = 1000;

    private AmazonDynamoDB dynamoDB;

    public void run() {
        dynamoDB = DynamoDBUtil.getDynamoDB();

        while (true) {
            for (Long threadId : WebServer.processingThreads.keySet()) {
                List<InstrumentationInfo> threadInfo = WebServer.processingThreads.get(threadId);
                threadInfo.stream()
                        .filter(x -> x.done)
                        .forEach(info -> {
                            System.out.println(info);
                            DynamoDBUtil.putNewResult(dynamoDB, "vms2", info.id, info.path, info.pixels, info.bbls);
                        });
                List<InstrumentationInfo> newThreadInfo = threadInfo.stream()
                        .filter(x -> !x.done)
                        .collect(Collectors.toList());
                WebServer.processingThreads.put(threadId, newThreadInfo);
            }

            try {
                Thread.sleep(PUSH_TO_MSS_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
