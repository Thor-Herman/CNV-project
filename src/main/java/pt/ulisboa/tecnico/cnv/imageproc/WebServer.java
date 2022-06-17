package pt.ulisboa.tecnico.cnv.imageproc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static final Map<Long, List<InstrumentationInfo>> processingThreads = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {

        DynamoDBUtil.createNewTable(DynamoDBUtil.getDynamoDB(), "vms2", "id");

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/blurimage", new BlurImageHandler());
        server.createContext("/enhanceimage", new EnhanceImageHandler());
        server.createContext("/detectqrcode", new DetectQrCodeHandler());
        server.createContext("/classifyimage", new ImageClassificationHandler());
        server.createContext("/healthcheck", new HealthCheck());
        server.start();

        MSSConnector mssConnector = new MSSConnector();
        Thread dynamoDBThread = new Thread(mssConnector);
        dynamoDBThread.setDaemon(true);
        dynamoDBThread.run();

    }
}
