package pt.ulisboa.tecnico.cnv.imageproc;

import java.net.InetSocketAddress;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.ec2.AmazonEC2;
import com.sun.net.httpserver.HttpServer;

public class LoadBalancerServer {

    public static void main(String[] args) throws Exception {
        String ipAddress = args[0];
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        AmazonEC2 ec2 = EC2Utility.getEC2Client();
        AmazonCloudWatch cloudWatch = EC2Utility.getCloudWatch();
        server.createContext("/blurimage", new LoadBalancer("/blurimage", ec2));
        server.createContext("/enhanceimage", new LoadBalancer("/enhanceimage", ec2));
        server.createContext("/detectqrcode", new LoadBalancer("/detectqrcode", ec2));
        server.createContext("/classifyimage", new LoadBalancer("/classifyimage", ec2));
        server.start();

        AutoScaler autoScaler = new AutoScaler(ec2, cloudWatch, ipAddress);
        Thread daemon = new Thread(autoScaler);
        daemon.setDaemon(true);
        daemon.run();
    }
}