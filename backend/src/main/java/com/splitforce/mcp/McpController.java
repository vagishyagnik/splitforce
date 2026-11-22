package com.splitforce.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Model Context Protocol endpoint over Streamable HTTP in stateless JSON mode (no SSE — the Lambda
 * container buffers responses). Handles the JSON-RPC methods an MCP client needs: initialize, ping,
 * tools/list and tools/call, plus notifications. Authentication is handled upstream by the OAuth JWT
 * filter; the authenticated {@link OAuth2User} is passed to the tools, which reuse the REST controllers.
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private static final String SERVER_NAME = "splitforce";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String DEFAULT_PROTOCOL = "2025-06-18";

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private final McpTools tools;
    private final ObjectMapper json;

    public McpController(McpTools tools, ObjectMapper json) {
        this.tools = tools;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody(required = false) JsonNode body,
                                           @AuthenticationPrincipal OAuth2User principal) {
        if (body == null || body.isNull()) {
            return ResponseEntity.ok(errorResponse(null, PARSE_ERROR, "no JSON-RPC payload"));
        }
        if (body.isArray()) {
            ArrayNode out = json.createArrayNode();
            for (JsonNode element : body) {
                JsonNode response = handleOne(element, principal);
                if (response != null) out.add(response);
            }
            // A batch of only notifications yields no responses → 202 with empty body.
            return out.isEmpty() ? ResponseEntity.accepted().build() : ResponseEntity.ok(out);
        }
        JsonNode response = handleOne(body, principal);
        return response == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(response);
    }

    /** Returns the JSON-RPC response, or null for a notification (no id). */
    private JsonNode handleOne(JsonNode req, OAuth2User principal) {
        if (req == null || !req.isObject()) {
            return errorResponse(null, INVALID_REQUEST, "request must be a JSON-RPC object");
        }
        JsonNode idNode = req.get("id");
        boolean isNotification = idNode == null || idNode.isNull();
        String method = req.path("method").asText(null);
        if (method == null) {
            return isNotification ? null : errorResponse(idNode, INVALID_REQUEST, "missing method");
        }
        JsonNode params = req.get("params");

        try {
            switch (method) {
                case "initialize":
                    return result(idNode, initialize(params));
                case "ping":
                    return result(idNode, json.createObjectNode());
                case "tools/list":
                    return result(idNode, toolsList());
                case "tools/call":
                    return result(idNode, toolsCall(principal, params));
                default:
                    // notifications/* and other notifications need no response.
                    if (isNotification || method.startsWith("notifications/")) return null;
                    return errorResponse(idNode, METHOD_NOT_FOUND, "unknown method: " + method);
            }
        } catch (McpToolException e) {
            return isNotification ? null : errorResponse(idNode, INVALID_PARAMS, e.getMessage());
        } catch (Exception e) {
            return isNotification ? null : errorResponse(idNode, INTERNAL_ERROR,
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private ObjectNode initialize(JsonNode params) {
        String protocol = params != null && params.hasNonNull("protocolVersion")
                ? params.get("protocolVersion").asText() : DEFAULT_PROTOCOL;
        ObjectNode result = json.createObjectNode();
        result.put("protocolVersion", protocol);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return result;
    }

    private ObjectNode toolsList() {
        ObjectNode result = json.createObjectNode();
        result.set("tools", json.valueToTree(tools.definitions()));
        return result;
    }

    private ObjectNode toolsCall(OAuth2User principal, JsonNode params) {
        if (params == null || !params.hasNonNull("name")) {
            throw new McpToolException("tools/call requires a tool name");
        }
        String name = params.get("name").asText();
        JsonNode arguments = params.get("arguments");

        ObjectNode result = json.createObjectNode();
        ArrayNode content = result.putArray("content");
        try {
            Object toolResult = tools.call(principal, name, arguments);
            String text = toolResult instanceof String s ? s : json.writerWithDefaultPrettyPrinter().writeValueAsString(toolResult);
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
        } catch (McpToolException e) {
            content.addObject().put("type", "text").put("text", e.getMessage());
            result.put("isError", true);
        } catch (Exception e) {
            content.addObject().put("type", "text").put("text",
                    e.getMessage() == null ? e.toString() : e.getMessage());
            result.put("isError", true);
        }
        return result;
    }

    private JsonNode result(JsonNode id, JsonNode value) {
        ObjectNode response = json.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? null : id);
        response.set("result", value);
        return response;
    }

    private JsonNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode response = json.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? null : id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "error" : message);
        return response;
    }

    // Kept for symmetry with potential future resources/prompts; currently unused list helpers.
    @SuppressWarnings("unused")
    private List<Map<String, Object>> empty() {
        return new ArrayList<>();
    }
}
