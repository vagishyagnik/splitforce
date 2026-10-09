package com.splitforce;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * AWS Lambda entry point. Spring starts in the static initializer, so with SnapStart the
 * started application is snapshotted once per published version and restored on cold start.
 * Lambda Function URLs send HTTP API v2 payloads.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> HANDLER;
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        try {
            HANDLER = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(SplitForceApplication.class);
        } catch (ContainerInitializationException e) {
            throw new IllegalStateException("Could not start Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        HANDLER.proxyStream(input, buffer, context);
        JSON.writeValue(output, moveSetCookiesToCookies(JSON.readTree(buffer.toByteArray())));
    }

    /**
     * Function URLs ignore {@code multiValueHeaders} and allow one value per header, so a response
     * setting two cookies (e.g. the OAuth callback) would lose one. The v2 format has a dedicated
     * {@code cookies} array for this.
     */
    private static JsonNode moveSetCookiesToCookies(JsonNode response) {
        if (!(response instanceof ObjectNode root)) return response;
        ArrayNode cookies = root.putArray("cookies");
        if (root.get("multiValueHeaders") instanceof ObjectNode multi) {
            JsonNode values = multi.remove("Set-Cookie");
            if (values != null) values.forEach(cookies::add);
        }
        if (root.get("headers") instanceof ObjectNode headers) {
            headers.remove("Set-Cookie");
        }
        if (cookies.isEmpty()) root.remove("cookies");
        return root;
    }
}
