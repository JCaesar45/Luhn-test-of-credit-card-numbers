import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Luhn Gate — Java 17+ implementation.
 * Compile: javac LuhnGate.java
 * Run:     java LuhnGate
 * Self-test: java LuhnGate --selftest
 */
public final class LuhnGate {

    public enum Brand { VISA, MASTERCARD, AMEX, DISCOVER, JCB, DINERS, UNKNOWN }

    public record Verdict(
        boolean valid, Brand brand, int s1, int s2,
        int total, int digits, double elapsedMicros
    ) {}

    private static final Map<Brand, String[][]> IIN = Map.of(
        Brand.VISA,       new String[][]{{"4", "4"}},
        Brand.MASTERCARD, new String[][]{{"51", "55"}, {"2221", "2720"}},
        Brand.AMEX,       new String[][]{{"34", "34"}, {"37", "37"}},
        Brand.DISCOVER,   new String[][]{{"6011", "6011"}, {"644", "649"}, {"65", "65"}},
        Brand.JCB,        new String[][]{{"3528", "3589"}},
        Brand.DINERS,     new String[][]{{"300", "305"}, {"36", "36"}, {"38", "39"}}
    );

    public static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "");
    }

    public static boolean luhnTest(String raw) {
        String s = clean(raw);
        if (s.length() < 2 || !s.matches("\\d+")) return false;
        int sum = 0;
        boolean alt = false;
        for (int i = s.length() - 1; i >= 0; i--) {
            int d = s.charAt(i) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    public static Brand detectBrand(String raw) {
        String s = clean(raw);
        for (var entry : IIN.entrySet()) {
            for (String[] range : entry.getValue()) {
                int w = range[0].length();
                if (s.length() < w) continue;
                String prefix = s.substring(0, w);
                if (prefix.matches("\\d+")
                    && prefix.compareTo(range[0]) >= 0
                    && prefix.compareTo(range[1]) <= 0) {
                    return entry.getKey();
                }
            }
        }
        return Brand.UNKNOWN;
    }

    public static Verdict audit(String raw) {
        String s = clean(raw);
        long t0 = System.nanoTime();
        if (s.length() < 2 || !s.matches("\\d+")) {
            return new Verdict(false, Brand.UNKNOWN, 0, 0, 0, s.length(),
                (System.nanoTime() - t0) / 1000.0);
        }
        int s1 = 0, s2 = 0;
        boolean alt = false;
        for (int i = s.length() - 1; i >= 0; i--) {
            int d = s.charAt(i) - '0';
            if (alt) {
                int dd = d * 2;
                s2 += dd > 9 ? dd - 9 : dd;
            } else {
                s1 += d;
            }
            alt = !alt;
        }
        int total = s1 + s2;
        return new Verdict(total % 10 == 0, detectBrand(s), s1, s2, total, s.length(),
            (System.nanoTime() - t0) / 1000.0);
    }

    // ---------- HTTP surface ----------
    private static void writeJson(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static String jsonVerdict(Verdict v) {
        return String.format(
            "{\"valid\":%b,\"brand\":\"%s\",\"s1\":%d,\"s2\":%d,\"total\":%d,\"digits\":%d,\"elapsedUs\":%.2f}",
            v.valid(), v.brand().name().toLowerCase(), v.s1(), v.s2(),
            v.total(), v.digits(), v.elapsedMicros());
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--selftest")) {
            List<Map.Entry<String, Boolean>> vectors = List.of(
                Map.entry("4111111111111111", true),
                Map.entry("4111111111111112", false),
                Map.entry("49927398716",      true),
                Map.entry("49927398717",      false),
                Map.entry("1234567812345678", false),
                Map.entry("1234567812345670", true)
            );
            int failed = 0;
            for (var e : vectors) {
                boolean got = luhnTest(e.getKey());
                boolean ok = got == e.getValue();
                if (!ok) failed++;
                System.out.printf("[%s] %s → %b%n", ok ? "ok" : "XX", e.getKey(), got);
            }
            System.exit(failed == 0 ? 0 : 1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/health", ex ->
            writeJson(ex, 200, "{\"status\":\"ok\"}"));

        server.createContext("/v1/validate", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String pan = extractPan(body);
            Verdict v = audit(pan);
            writeJson(ex, 200, jsonVerdict(v));
        });

        server.start();
        System.out.println("LuhnGate listening on :8080");
    }

    private static String extractPan(String body) {
        // minimal JSON scrape — "pan":"..."
        int k = body.indexOf("\"pan\"");
        if (k < 0) return "";
        int colon = body.indexOf(':', k);
        int q1 = body.indexOf('"', colon + 1);
        int q2 = body.indexOf('"', q1 + 1);
        return (q1 < 0 || q2 < 0) ? "" : body.substring(q1 + 1, q2);
    }
}
