package ru.stepan1411.kits_browser.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class WebClient {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String backendUrl = "https://kitbrowser.survivalworld.win";
    private static String authToken;

    public static void setAuthToken(String token) {
        authToken = token;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void setBackendUrl(String url) {
        backendUrl = url;
    }

    public static String getBackendUrl() {
        return backendUrl;
    }

    public static boolean checkConnection() {
        try {
            httpGet(backendUrl + "/api/kits/names");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String importKitRaw(String kitName, String jsonData) throws Exception {
        return importKitRaw(kitName, jsonData, null);
    }

    public static String importKitRaw(String kitName, String jsonData, String owner) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("name", kitName);
        payload.put("data", GSON.fromJson(jsonData, Map.class));
        if (owner != null) payload.put("owner", owner);
        return httpPost(backendUrl + "/api/kits/import", GSON.toJson(payload));
    }

    public static String createLoginLink(String username) throws Exception {
        return createLoginLink(username, null);
    }

    public static String createLoginLink(String username, String server) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("username", username);
        if (server != null) payload.put("server", server);
        return httpPost(backendUrl + "/api/auth/login-link", GSON.toJson(payload));
    }

    public static String fetchStats() throws Exception {
        return httpGet(backendUrl + "/api/kits/stats");
    }

    public static String downloadKit(String kitName) throws Exception {
        return httpGet(backendUrl + "/api/kits/download/" + kitName);
    }

    public static String downloadKitById(int id) throws Exception {
        return httpGet(backendUrl + "/api/kits/download-by-id/" + id);
    }

    public static String logOut(String username, String server) throws Exception {
        return httpPost(backendUrl + "/api/auth/logout-link", GSON.toJson(Map.of("username", username, "server", server)));
    }

    public static String httpGet(String urlStr) throws Exception {
        URI uri = new URI(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Accept", "application/json");
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            String errBody = "";
            try { errBody = new String(readAll(conn.getErrorStream()), StandardCharsets.UTF_8); } catch (Exception ignored) {}
            throw new RuntimeException("HTTP " + code + ": " + errBody);
        }
        return new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
    }

    public static String httpPost(String urlStr, String body) throws Exception {
        URI uri = new URI(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code != 200) throw new RuntimeException("HTTP " + code);
        return new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
    }

    private static byte[] readAll(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = is.read(data)) != -1) buffer.write(data, 0, n);
        return buffer.toByteArray();
    }
}
