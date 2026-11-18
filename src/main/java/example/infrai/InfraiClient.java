package example.infrai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiClient {
    private static final int MAX_429_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 250L;
    private final HttpClient http;
    private final String key;
    private final String baseUrl;

    public InfraiClient(String key, String baseUrl) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.key = key;
        this.baseUrl = baseUrl;
    }

    public String accountBudgetSet(double hardCapUsd, String period, double alertThresholdUsd, String idempotencyKey) throws IOException, InterruptedException {
        return request("PUT", "/v1/account/budget/set", "{\"hard_cap_usd\":" + hardCapUsd + ",\"period\":\"" + period + "\",\"alert_threshold_usd\":" + alertThresholdUsd + ",\"idempotency_key\":\"" + idempotencyKey + "\"}");
    }

    public String accountAutorechargeConfigure(double triggerBalance, double rechargeAmount, String idempotencyKey) throws IOException, InterruptedException {
        return request("PUT", "/v1/account/autorecharge/configure", "{\"trigger_balance\":" + triggerBalance + ",\"recharge_amount\":" + rechargeAmount + ",\"idempotency_key\":\"" + idempotencyKey + "\"}");
    }

    public String accountBalance() throws IOException, InterruptedException {
        return request("GET", "/v1/account/balance", null);
    }

    public double extractBalance(String envelope) throws IOException {
        Matcher matcher = Pattern.compile("\\\"balance(?:_usd)?\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(envelope);
        if (!matcher.find()) throw new IOException("Infrai balance missing from response: " + envelope);
        return Double.parseDouble(matcher.group(1));
    }

    public String accountTopup(double amount, String idempotencyKey) throws IOException, InterruptedException {
        return request("POST", "/v1/account/topup", "{\"amount\":" + amount + ",\"idempotency_key\":\"" + idempotencyKey + "\"}");
    }

    public String emailSend(String to, String subject, String body, String idempotencyKey) throws IOException, InterruptedException {
        return request("POST", "/v1/email/send", "{\"to\":\"" + to + "\",\"subject\":\"" + subject + "\",\"body\":\"" + body + "\",\"idempotency_key\":\"" + idempotencyKey + "\"}");
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpRequest request = builder.build();
        HttpResponse<String> response = null;
        for (int attempt = 0; ; attempt++) {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 || attempt >= MAX_429_RETRIES) break;
            Thread.sleep(retryDelayMillis(response, attempt));
        }
        String envelope = response.body();
        if (!envelope.contains("\"ok\":true")) throw new IOException("Infrai error: " + envelope);
        return envelope;
    }

    private long retryDelayMillis(HttpResponse<String> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                return Math.max(0L, Long.parseLong(retryAfter.trim()) * 1000L);
            } catch (NumberFormatException ignored) {
                try {
                    long delay = Duration.between(Instant.now(), ZonedDateTime.parse(
                            retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis();
                    return Math.max(0L, delay);
                } catch (DateTimeParseException ignoredDate) {
                    // Fall through to exponential backoff for an invalid header.
                }
            }
        }
        return INITIAL_BACKOFF_MILLIS * (1L << attempt);
    }
}
