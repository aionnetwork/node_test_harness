package org.aion.harness.main.tools;

import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

/**
 * A class responsible for calling an RPC endpoint using the provided payload.
 */
public final class RpcCaller {
    private final String ip;
    private final String port;
    private final HttpClient httpClient;

    public RpcCaller(String ip, String port) {
        if (ip == null) {
            throw new NullPointerException("IP cannot be null");
        }

        if (port == null) {
            throw new NullPointerException("Port cannot be null");
        }

        this.ip = ip;
        this.port = port;
        // Create the HTTP Client once for the entire run since this creates several threads, etc.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * Returns an internal rpc result to the caller.
     *
     * The returned result is unsuccessful if either the attempt to send the RPC call failed or
     * if the kernel itself responded back with an explicit error message.
     *
     * Otherwise, the returned result is successful.
     *
     * This does mean that the kernel may still return a 'null' result here when we return success.
     * Though 'null' is usually interpreted as an error, we leave that up to the caller, since this
     * is not always the case.
     *
     * A successful result will contain the raw response of the server. It will still need to be
     * parsed.
     */
    public InternalRpcResult call(String payload, boolean verbose) {
        // We will use the JDK11 "HttpClient".
        long timeOfCallInNanos = System.nanoTime();
        URI uri = URI.create("http://" + this.ip + ":" + this.port);
        if (verbose) {
            System.out.println("Sending to " + uri + ": <payload>" + payload + "</payload>");
        }
        // We just want to send the entire payload as the data to the POST, with no additional variables and only the content-type header.
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .uri(uri)
                .header("Content-Type", "application/json")
                .build();

        final HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // We see this on connection refused, etc.
            return InternalRpcResult.unsuccessful(e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interrupt while contacting RPC URI:  " + uri, e);
        }
        int statusCode = response.statusCode();
        String output = response.body();
        if (verbose) {
            System.out.println("Received " + statusCode + ": <response>" + output + "</response>");
        }

        if (output.isEmpty()) {
            return InternalRpcResult.unsuccessful("unknown error");
        }

        final JsonStringParser outputParser;
        try {
            outputParser = new JsonStringParser(output);
        } catch (JsonSyntaxException mje) {
            throw new RuntimeException("Error parsing json: " + output);
        }

        // This is only successful if the RPC Process exited successfully, and the RPC output
        // contained no 'error' content and it does contain 'result' content.

        String error = outputParser.attributeToString("error");
        if ((statusCode == 200) && (null == error)) {
            return InternalRpcResult.successful(output, timeOfCallInNanos, TimeUnit.NANOSECONDS);
        } else {
            // We expect the content of 'error' to itself be a Json String. If it has no content
            // then the error is unknown.
            if (error == null) {
                return InternalRpcResult.unsuccessful("HTTP request failed with status: " + statusCode);
            } else {
                JsonStringParser errorParser = new JsonStringParser(error);

                // The 'data' attribute should capture the error.
                error = errorParser.attributeToString("data");

                // If there was no 'data' value then try to grab the less informative 'message'.
                error = (error == null) ? errorParser.attributeToString("message") : error;

                return InternalRpcResult.unsuccessful(error);
            }
        }

    }
}
