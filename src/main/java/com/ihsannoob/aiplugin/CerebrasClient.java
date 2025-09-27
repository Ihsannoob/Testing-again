package com.ihsannoob.aiplugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * Minimal HTTP client for calling Cerebras-like API.
 * Adjust payload/response parsing according to the real Cerebras API docs.
 */
public class CerebrasClient {

    public static class Message {
        public final String role;
        public final String content;
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final int maxTokens;
    private final HttpClient http;
    private final Duration timeout;
    private final Logger logger;
    private final Gson gson;

    public CerebrasClient(String apiKey, String endpoint, String model, int maxTokens, int timeoutSeconds, Logger logger, Gson gson) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.logger = logger;
        this.gson = gson;
    }

    /**
     * Generate a response given the conversation messages.
     * This method performs a synchronous HTTP request and returns the assistant text or null on error.
     */
    public String generate(List<Message> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warning("Cerebras API key is empty. Set api_key in config.yml");
            return null;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);

            // Build messages array (adapts to common "messages" format)
            JsonArray msgs = new JsonArray();
            for (Message m : messages) {
                JsonObject mo = new JsonObject();
                mo.addProperty("role", m.role);
                mo.addProperty("content", m.content);
                msgs.add(mo);
            }
            payload.add("messages", msgs);
            payload.addProperty("max_tokens", maxTokens);

            String body = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            String respBody = resp.body();
            if (status / 100 != 2) {
                logger.warning("Cerebras API returned non-2xx: " + status + " body: " + respBody);
                return null;
            }

            // Try to parse common fields. Different providers return different JSON shapes.
            JsonElement root = gson.fromJson(respBody, JsonElement.class);
            if (root == null || root.isJsonNull()) return null;
            JsonObject rootObj = root.getAsJsonObject();

            // Attempt 1: common structure with choices -> message -> content
            if (rootObj.has("choices")) {
                JsonArray choices = rootObj.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject first = choices.get(0).getAsJsonObject();
                    if (first.has("message")) {
                        JsonObject msg = first.getAsJsonObject("message");
                        if (msg.has("content")) {
                            return msg.get("content").getAsString();
                        }
                    } else if (first.has("text")) {
                        return first.get("text").getAsString();
                    } else if (first.has("delta")) {
                        JsonObject delta = first.getAsJsonObject("delta");
                        if (delta.has("content")) return delta.get("content").getAsString();
                    }
                }
            }

            // Attempt 2: some APIs return an "output" array
            if (rootObj.has("output")) {
                JsonArray out = rootObj.getAsJsonArray("output");
                if (out.size() > 0) {
                    JsonObject first = out.get(0).getAsJsonObject();
                    if (first.has("content")) return first.get("content").getAsString();
                    if (first.has("generated_text")) return first.get("generated_text").getAsString();
                }
            }

            // Attempt 3: direct field "text" or "generated_text"
            if (rootObj.has("text")) return rootObj.get("text").getAsString();
            if (rootObj.has("generated_text")) return rootObj.get("generated_text").getAsString();

            logger.warning("Could not parse Cerebras response: " + respBody);
            return null;

        } catch (IOException | InterruptedException ex) {
            logger.severe("Error calling Cerebras API: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        } catch (Exception ex) {
            logger.severe("Unexpected error in Cerebras client: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }
}
