package ch.so.agi.dsmocclusion.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class RangeAwareHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final AtomicInteger totalRequests = new AtomicInteger();
    private final AtomicInteger rangeRequests = new AtomicInteger();
    private final AtomicInteger corruptRemaining = new AtomicInteger();
    private final AtomicInteger corruptedRangeResponses = new AtomicInteger();
    private final List<String> seenRanges = new CopyOnWriteArrayList<>();

    public RangeAwareHttpServer(Path file, String route) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] content = Files.readAllBytes(file);
        server.createContext(route, new FileHandler(content));
        server.start();
    }

    public URI uri(String route) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + route);
    }

    public int totalRequests() {
        return totalRequests.get();
    }

    public int rangeRequests() {
        return rangeRequests.get();
    }

    public List<String> seenRanges() {
        return seenRanges;
    }

    public void corruptNextRangeResponses(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        corruptRemaining.set(count);
    }

    public void alwaysCorruptRangeResponses() {
        corruptRemaining.set(Integer.MAX_VALUE);
    }

    public int corruptedRangeResponses() {
        return corruptedRangeResponses.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private final class FileHandler implements HttpHandler {
        private final byte[] content;

        private FileHandler(byte[] content) {
            this.content = content;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            totalRequests.incrementAndGet();
            Headers headers = exchange.getResponseHeaders();
            headers.add("Accept-Ranges", "bytes");
            headers.add("Content-Type", "image/tiff");

            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                headers.add("Content-Length", Integer.toString(content.length));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            if (rangeHeader != null) {
                rangeRequests.incrementAndGet();
                seenRanges.add(rangeHeader);
                long[] range = parseRange(rangeHeader, content.length);
                int start = (int) range[0];
                int end = (int) range[1];
                int length = end - start + 1;
                byte[] responseBody = slice(start, end);
                if (shouldCorruptRangeResponse()) {
                    corruptedRangeResponses.incrementAndGet();
                    responseBody = new byte[length];
                }
                headers.add("Content-Range", "bytes " + start + "-" + end + "/" + content.length);
                headers.add("Content-Length", Integer.toString(responseBody.length));
                exchange.sendResponseHeaders(206, responseBody.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(responseBody);
                }
                return;
            }

            headers.add("Content-Length", Integer.toString(content.length));
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(content);
            }
        }

        private long[] parseRange(String header, int length) {
            String value = header.replace("bytes=", "");
            String[] parts = value.split("-", 2);
            long start = Long.parseLong(parts[0]);
            long end = parts[1].isBlank() ? length - 1L : Long.parseLong(parts[1]);
            end = Math.min(end, length - 1L);
            return new long[] {start, end};
        }

        private byte[] slice(int start, int end) {
            return Arrays.copyOfRange(content, start, end + 1);
        }

        private boolean shouldCorruptRangeResponse() {
            while (true) {
                int remaining = corruptRemaining.get();
                if (remaining <= 0) {
                    return false;
                }
                int next = remaining == Integer.MAX_VALUE ? Integer.MAX_VALUE : remaining - 1;
                if (corruptRemaining.compareAndSet(remaining, next)) {
                    return true;
                }
            }
        }
    }
}
