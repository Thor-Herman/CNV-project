package pt.ulisboa.tecnico.cnv.imageproc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.document.Attribute;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.SourceTableDetails;
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
    public static float PIXELS_THRESHOLD_PERCENTAGE = 0.05f;
    public static float LAMBDA_CPU_UTIL_THRESHOLD = 90f;

    public LoadBalancer(String endpoint, AmazonEC2 ec2) {
        this.ec2 = ec2;
        this.endpoint = endpoint;
    }

    private void handleRequest(HttpExchange t) throws IOException {
        try {
            String response = getAreAllVMsBusy() ? launchLambda(t) : sendReqToVM(t);
            returnResponse(t, response);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e);
            return;
        }
    }

    private boolean getAreAllVMsBusy() {
        boolean noVMsRunning = AutoScaler.getVMsRunning().size() == 0;
        boolean allVMsAtCapacity = AutoScaler.getVMsRunning().stream()
                .allMatch(vm -> vm.cpuUtilization > LAMBDA_CPU_UTIL_THRESHOLD);
        // System.out.println(String.format("noVMsRunning:%s at capacity:%s",
        // noVMsRunning, allVMsAtCapacity));
        return noVMsRunning || allVMsAtCapacity;
    }

    private String sendReqToVM(HttpExchange t) throws Exception {
        String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // Can only read requestBody once! 
        long pixels = getPixelsFromHttpExchange(body);
        long estimatedBBLs = estimateBBLs(pixels, t.getHttpContext().getPath());
        System.out.println("ESTIMATED BBLS FOR PIXELS " + pixels + ": " + estimatedBBLs);
        VM vm = getNextVM();
        vm.bblsAssumedToBeProcessing += estimatedBBLs;
        vm.currentAmountOfRequests++;
        String response = forwardRequest(t, vm.ipAddress, body);
        vm.currentAmountOfRequests--;
        vm.bblsAssumedToBeProcessing -= estimatedBBLs;
        return response;
    }

    private VM getNextVM() {
        List<VM> vms = AutoScaler.getVMsRunning();
        VM vmWithLeastBBLs = vms.stream().reduce(vms.get(0),
                (acc, val) -> acc.bblsAssumedToBeProcessing < val.bblsAssumedToBeProcessing ? acc : val);
        return vmWithLeastBBLs;
    }

    private String launchLambda(HttpExchange t) throws IOException {
        System.out.println("Launching lambda...");
        LambdaClient awsLambda = LambdaClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
        SdkBytes payload = getLambdaPayload(t.getRequestBody());
        InvokeRequest request = InvokeRequest.builder().functionName(endpoint.substring(1)).payload(payload).build();
        InvokeResponse res = awsLambda.invoke(request);
        String value = res.payload().asUtf8String();
        awsLambda.close();
        System.out.println(value);
        return value.substring(1, value.length() - 1);
    }

    private SdkBytes getLambdaPayload(InputStream requestBody) {
        String result = new BufferedReader(new InputStreamReader(requestBody)).lines()
                .collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];
        String json = String.format("{\"fileFormat\":\"%s\", \"body\":\"%s\"}", format, resultSplits[1]);
        SdkBytes payload = SdkBytes.fromUtf8String(json);
        return payload;
    }

    private String forwardRequest(HttpExchange t, String ip, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + ":" + 8000 + endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        // System.out.println(request);
        String resp = "";
        try {
            resp = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resp;
    }

    private void returnResponse(HttpExchange t, String response) throws Exception {
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    public static long getPixelsFromHttpExchange(String body) {
        String[] resultSplits = body.split(",");
        System.out.println("Body: " + body.length());
        byte[] decoded = Base64.getDecoder().decode(resultSplits[1].strip());
        ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
        BufferedImage bi;
        try {
            bi = ImageIO.read(bais);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        long pixels = bi.getWidth() * bi.getHeight();
        return pixels;
    }

    private long estimateBBLs(long pixels, String path) {
        long gtValue = Math.round(pixels * (1 - PIXELS_THRESHOLD_PERCENTAGE));
        long ltValue = Math.round(pixels * (1 + PIXELS_THRESHOLD_PERCENTAGE));

        System.out.println(String.format("pixels: %s\tgt: %s\tlt: %s", pixels, gtValue, ltValue));

        ScanResult result = DynamoDBUtil.filterDBForResolution(DynamoDBUtil.getDynamoDB(), "vms2", path, gtValue,
                ltValue);

        long totalValue = 0;
        double totalHits = result.getCount();

        if (totalHits == 0)
            return heuristicBBLs(pixels, path);

        for (Map<String, AttributeValue> match : result.getItems()) {
            totalValue += match.keySet().stream()
                    .filter(x -> x.equals("bbls"))
                    .map(x -> match.get(x))
                    .map(x -> x.getN())
                    .filter(x -> x != null)
                    .mapToLong(Long::parseLong)
                    .sum();
        }
        long estimate = Math.round(totalValue / totalHits);

        return estimate;
    }

    private long heuristicBBLs(long pixels, String path) {
        switch (path) {
            case "/blurimage":
                return pixels * 395;
            case "/enhanceimage":
                return pixels * 295;
            case "/classifyimage":
                return pixels * 2;
            case "/detectqrcode":
                return pixels * 17;
            default:
                return -1;
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
