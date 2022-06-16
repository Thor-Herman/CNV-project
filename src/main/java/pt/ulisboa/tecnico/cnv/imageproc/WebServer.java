package pt.ulisboa.tecnico.cnv.imageproc;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/blurimage", new BlurImageHandler());
        server.createContext("/enhanceimage", new EnhanceImageHandler());
        server.createContext("/detectqrcode", new DetectQrCodeHandler());
        server.createContext("/classifyimage", new ImageClassificationHandler());
        server.createContext("/healthcheck", new HealthCheck());
        server.start();
    }
}
