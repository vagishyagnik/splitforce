package com.splitforce.mcp;

/**
 * A tool-level failure whose message is safe to surface to the MCP client as the text of an
 * {@code isError} tool result (bad arguments, not-found, a rejected domain operation).
 */
public class McpToolException extends RuntimeException {
    public McpToolException(String message) {
        super(message);
    }
}
