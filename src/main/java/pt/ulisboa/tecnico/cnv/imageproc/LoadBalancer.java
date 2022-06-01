package pt.ulisboa.tecnico.cnv.imageproc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Instance;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;

public class LoadBalancer implements HttpHandler {

    AmazonEC2 ec2;
    List<Instance> instances;

    int roundRobinIndex = -1;
    String endpoint;

    public LoadBalancer(String endpoint) {
        ec2 = EC2Utility.getEC2Client();
        this.endpoint = endpoint;
    }

    private void handleRequest(HttpExchange t) throws IOException {
        try {
            instances = EC2Utility.getRunningInstances(ec2); // TODO: HANDLE CASE WITH NO RUNNING INSTANCES
            roundRobinIndex = roundRobinIndex >= instances.size() - 1 ? 0 : roundRobinIndex + 1;
            String dns = instances.get(roundRobinIndex).getPublicDnsName();
            forwardRequest(t, dns);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
    }

    private void forwardRequest(HttpExchange t, String dns) throws IOException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://" + dns.strip() + endpoint.strip())
                    .openConnection();
            connection.setRequestMethod("POST");
            for (String header : t.getRequestHeaders().keySet()) {
                connection.setRequestProperty(header, t.getRequestHeaders().getFirst(header));
            }
            System.out.println(connection);
            System.out.println("Here1");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            OutputStream outStream = connection.getOutputStream();
            InputStream is = t.getRequestBody();
            is.transferTo(outStream);
            outStream.flush();
            outStream.close();

            System.out.println(connection.getResponseMessage());
            System.out.println("Here2");
            long length = is.available();
            t.sendResponseHeaders(200, length);
            connection.getInputStream().transferTo(t.getResponseBody());

            System.out.println("Here");
            System.out.println(connection);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        if (t.getRequestHeaders().getFirst("Origin") != null) {
            t.getResponseHeaders().add("Access-Control-Allow-Origin", t.getRequestHeaders().getFirst("Origin"));
        }
        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "POST");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,API-Key");
            t.sendResponseHeaders(204, -1);
        } else {
            try {
                handleRequest(t);
            } catch (Exception e) {
                t.sendResponseHeaders(500, -1);
            }
        }
    }

}
