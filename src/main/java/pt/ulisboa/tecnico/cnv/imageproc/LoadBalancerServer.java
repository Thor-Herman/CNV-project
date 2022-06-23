package pt.ulisboa.tecnico.cnv.imageproc;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.ec2.AmazonEC2;
import com.sun.net.httpserver.HttpServer;

public class LoadBalancerServer {

    public static void main(String[] args) throws Exception {
        String ipAddress = args[0];
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/blurimage", new LoadBalancer("/blurimage"));
        server.createContext("/enhanceimage", new LoadBalancer("/enhanceimage"));
        server.createContext("/detectqrcode", new LoadBalancer("/detectqrcode"));
        server.createContext("/classifyimage", new LoadBalancer("/classifyimage"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        AutoScaler autoScaler = new AutoScaler(ipAddress);
        Thread daemon = new Thread(autoScaler);
        daemon.setDaemon(true);
        daemon.run();
    }
}