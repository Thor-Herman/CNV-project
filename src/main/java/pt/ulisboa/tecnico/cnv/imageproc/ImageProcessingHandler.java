package pt.ulisboa.tecnico.cnv.imageproc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javassist.CannotCompileException;
import javassist.tools.web.Webserver;

public abstract class ImageProcessingHandler implements HttpHandler, RequestHandler<Map<String, String>, String> {

    abstract BufferedImage process(BufferedImage bi) throws IOException;

    private String handleRequest(String inputEncoded, String format, String pathInfo) {
        byte[] decoded = Base64.getDecoder().decode(inputEncoded);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);

            long pixels = bi.getWidth() * bi.getHeight();
            Long threadId = Thread.currentThread().getId();
            if (!WebServer.processingThreads.containsKey(threadId))
                WebServer.processingThreads.put(threadId, new ArrayList<InstrumentationInfo>());
            InstrumentationInfo instrumentationInfo = new InstrumentationInfo(pixels, pathInfo);
            WebServer.processingThreads.get(threadId).add(instrumentationInfo);

            bi = process(bi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, format, baos);
            byte[] outputEncoded = Base64.getEncoder().encode(baos.toByteArray());

            instrumentationInfo.done = true;

            return new String(outputEncoded);
        } catch (Exception e) {
            System.out.println(e);
            return e.toString();
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        System.out.println("GOT A REQUEST");
        if (t.getRequestHeaders().getFirst("Origin") != null) {
            t.getResponseHeaders().add("Access-Control-Allow-Origin", t.getRequestHeaders().getFirst("Origin"));
        }
        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "POST");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,API-Key");
            t.sendResponseHeaders(204, -1);
        } else {
            try {
                InputStream stream = t.getRequestBody();
                // Result syntax: data:image/<format>;base64,<encoded image>
                String pathInfo = t.getHttpContext().getPath();
                String result = new BufferedReader(new InputStreamReader(stream)).lines()
                        .collect(Collectors.joining("\n"));
                String[] resultSplits = result.split(",");
                String format = resultSplits[0].split("/")[1].split(";")[0];
                String output = handleRequest(resultSplits[1], format, pathInfo);
                t.sendResponseHeaders(200, output.length());
                OutputStream os = t.getResponseBody();
                os.write(output.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public String handleRequest(Map<String, String> event, Context context) {
        return handleRequest(event.get("body"), event.get("fileFormat"), null);
    }
}
