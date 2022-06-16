package pt.ulisboa.tecnico.cnv.imageproc;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HealthCheck implements HttpHandler {

    @Override
    public void handle(HttpExchange t) throws IOException {
        String response = "All good";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
