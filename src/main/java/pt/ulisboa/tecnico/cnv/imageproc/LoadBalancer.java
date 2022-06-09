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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Instance;

import com.sun.net.httpserver.HttpServer;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;

public class LoadBalancer implements HttpHandler {

    AmazonEC2 ec2;
    List<Instance> instances;

    int roundRobinIndex = -1;
    String endpoint;

    public LoadBalancer(String endpoint, AmazonEC2 ec2) {
        this.ec2 = ec2;
        this.endpoint = endpoint;
    }

    private void handleRequest(HttpExchange t) throws IOException {
        try {
            // VM vm = getNextVM();
            // vm.currentAmountOfRequests++;
            // String response = forwardRequest(t, vm.ipAddress);
            String response = launchLambda(t);
            // vm.currentAmountOfRequests--;
            // System.out.println(response);
            returnResponse(t, response);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
    }

    private VM getNextVM() {
        List<VM> vms = AutoScaler.getVMsRunning();
        roundRobinIndex = roundRobinIndex >= vms.size() - 1 ? 0 : roundRobinIndex + 1;
        return vms.get(roundRobinIndex);
    }

    private String launchLambda(HttpExchange t) throws IOException {
        LambdaClient awsLambda = LambdaClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
        String result = new BufferedReader(new InputStreamReader(t.getRequestBody())).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];
        String json = String.format("{\"fileFormat\":\"%s\", \"body\":\"%s\"}", format, resultSplits[1]);
        SdkBytes payload = SdkBytes.fromUtf8String(json);
        InvokeRequest request = InvokeRequest.builder().functionName(endpoint.substring(1)).payload(payload).build();
        InvokeResponse res = awsLambda.invoke(request);
        String value = res.payload().asUtf8String();
        awsLambda.close();
        System.out.println(value);
        return value.substring(1, value.length() - 1);
    }

    private String forwardRequest(HttpExchange t, String ip) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + ":" + 8000 + endpoint))
                .POST(HttpRequest.BodyPublishers.ofByteArray(t.getRequestBody().readAllBytes()))
                .build();
        System.out.println(request);
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private void returnResponse(HttpExchange t, String response) throws Exception {
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
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
