package pt.ulisboa.tecnico.cnv.imageproc;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

public class LoadBalancerServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/blurimage", new LoadBalancer("/blurimage"));
        server.createContext("/enhanceimage", new LoadBalancer("/enhanceimage"));
        server.createContext("/detectqrcode", new LoadBalancer("/detectqrcode"));
        server.createContext("/classifyimage", new LoadBalancer("/classifyimage"));
        server.start();
    }
}