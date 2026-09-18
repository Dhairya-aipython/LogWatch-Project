// Log Analyzer - Multithreaded Log File Parser
// Java Project - September 2026
// Author: [Dhairya Agrawal]

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

class LogAnalyzer {

    // Custom exception for invalid log format
    static class LogFormatException extends Exception {
        LogFormatException(String message) {
            super(message);
        }
    }

    // Stores individual log entry data (immutable)
    static class LogData {
        final String timestamp, level, ip, endpoint, message;
        final int responseTime;

        LogData(String ts, String lvl, String ip, String ep, String msg, int rt) {
            this.timestamp = ts;
            this.level = lvl;
            this.ip = ip;
            this.endpoint = ep;
            this.message = msg;
            this.responseTime = rt;
        }

        @Override
        public String toString() {
            return timestamp + " [" + level + "] " + ip + " -> " +
                    endpoint + " (" + responseTime + "ms)";
        }
    }

    // Holds all analysis results (thread-safe)
    static class Stats {
        private final Map<String, AtomicInteger> byLevel = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> byIp = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> byEndpoint = new ConcurrentHashMap<>();
        private final List<LogData> slow = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger total = new AtomicInteger(0);
        private final AtomicInteger corrupt = new AtomicInteger(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicInteger slowest = new AtomicInteger(0);
        final int slowThreshold = 500;

        void add(LogData e) {
            total.incrementAndGet();
            totalTime.addAndGet(e.responseTime);

            // Thread-safe update of slowest using CAS
            int currentSlowest;
            do {
                currentSlowest = slowest.get();
                if (e.responseTime <= currentSlowest) break;
            } while (!slowest.compareAndSet(currentSlowest, e.responseTime));

            if (e.responseTime > slowThreshold) {
                slow.add(e);
            }

            // Thread-safe counter updates
            byLevel.computeIfAbsent(e.level, k -> new AtomicInteger()).incrementAndGet();
            byIp.computeIfAbsent(e.ip, k -> new AtomicInteger()).incrementAndGet();
            byEndpoint.computeIfAbsent(e.endpoint, k -> new AtomicInteger()).incrementAndGet();
        }

        void addCorrupt() {
            corrupt.incrementAndGet();
        }

        int getTotal() { return total.get(); }
        int getCorrupt() { return corrupt.get(); }
        long getTotalTime() { return totalTime.get(); }
        int getSlowest() { return slowest.get(); }
        Map<String, AtomicInteger> getByLevel() { return byLevel; }
        Map<String, AtomicInteger> getByIp() { return byIp; }
        Map<String, AtomicInteger> getByEndpoint() { return byEndpoint; }

        List<LogData> getSlowRequests() {
            synchronized(slow) {
                return new ArrayList<>(slow);  // Return safe copy
            }
        }
    }

    // Worker thread that processes a chunk of log lines
    static class LogParser implements Runnable {
        private final List<String> lines;
        private final Stats stats;
        private final int workerId;

        // Regex pattern for log line format
        private static final Pattern LINE_PATTERN = Pattern.compile(
                "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) \\[(\\w+)] (\\S+) (\\S+) \"(.+)\" resp_time_ms=(\\d+)$"
        );

        LogParser(List<String> lines, Stats stats, int id) {
            this.lines = lines;
            this.stats = stats;
            this.workerId = id;
        }

        @Override
        public void run() {
            int processed = 0;
            for (String line : lines) {
                // Skip empty or whitespace-only lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    Matcher m = LINE_PATTERN.matcher(line);
                    if (!m.matches()) {
                        throw new LogFormatException("Line doesn't match expected format");
                    }

                    // Parse and validate response time
                    int responseTime;
                    try {
                        responseTime = Integer.parseInt(m.group(6));
                    } catch (NumberFormatException ex) {
                        throw new LogFormatException("Invalid response time value");
                    }

                    stats.add(new LogData(
                            m.group(1),  // timestamp
                            m.group(2),  // level (INFO, DEBUG, etc)
                            m.group(3),  // IP address
                            m.group(4),  // endpoint
                            m.group(5),  // message
                            responseTime
                    ));
                    processed++;
                } catch (LogFormatException ex) {
                    // Expected parsing errors
                    stats.addCorrupt();
                } catch (Exception ex) {
                    // Unexpected errors
                    stats.addCorrupt();
                }
            }
            System.out.println("Worker-" + workerId + " finished: " + processed + " lines");
        }
    }

    public static void main(String[] args) {
        // Get filename from command line or use default
        String filename = args.length > 0 ? args[0] : "server.log";
        String reportFile = args.length > 1 ? args[1] : "analysis_report.txt";

        // Check if input file exists
        File f = new File(filename);
        if (!f.exists()) {
            System.out.println("Error: File '" + filename + "' not found!");
            System.out.println("Usage: java LogAnalyzer [logfile] [reportfile]");
            return;
        }

        System.out.println("=== Log Analyzer Started ===");
        System.out.println("Input file: " + filename);
        System.out.println("Report file: " + reportFile);

        List<String> allLines;
        try {
            allLines = Files.readAllLines(Paths.get(filename));
        } catch (IOException ex) {
            System.out.println("Error reading file: " + ex.getMessage());
            return;
        }

        System.out.println("Lines read: " + allLines.size());

        if (allLines.isEmpty()) {
            System.out.println("Warning: File is empty, nothing to analyze");
            return;
        }

        Stats stats = new Stats();
        int workerCount = 4;
        int chunkSize = (int) Math.ceil(allLines.size() / (double) workerCount);

        ExecutorService pool = Executors.newFixedThreadPool(workerCount);

        try {
            // Submit worker tasks
            for (int i = 0; i < workerCount; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, allLines.size());

                if (start >= allLines.size()) {
                    break;
                }

                List<String> chunk = allLines.subList(start, end);
                pool.submit(new LogParser(chunk, stats, i + 1));
            }

            pool.shutdown();

            // Wait for all workers to complete
            boolean finished = pool.awaitTermination(1, TimeUnit.MINUTES);
            if (!finished) {
                System.out.println("Warning: Processing timeout - forcing shutdown");
                pool.shutdownNow();
            }

        } catch (InterruptedException ex) {
            System.out.println("Error: Processing interrupted");
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return;
        } finally {
            if (!pool.isShutdown()) {
                pool.shutdownNow();
            }
        }

        // Display results to console
        System.out.println("\n***** Analysis Results *****");
        System.out.println("Valid entries: " + stats.getTotal());
        System.out.println("Invalid entries: " + stats.getCorrupt());

        double avg = stats.getTotal() == 0 ? 0 : (double) stats.getTotalTime() / stats.getTotal();
        System.out.printf("Average response time: %.2f ms%n", avg);
        System.out.println("Slowest request: " + stats.getSlowest() + " ms");
        System.out.println("Slow requests (>" + stats.slowThreshold + "ms): " + stats.getSlowRequests().size());

        System.out.println("\n--- Requests by Level ---");
        for (var e : stats.getByLevel().entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue().get());
        }

        System.out.println("\n--- Top 5 IP Addresses ---");
        List<Map.Entry<String, AtomicInteger>> ipList = new ArrayList<>(stats.getByIp().entrySet());
        ipList.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        int count = 0;
        for (var e : ipList) {
            if (count >= 5) break;
            System.out.println("  " + e.getKey() + ": " + e.getValue().get() + " hits");
            count++;
        }

        System.out.println("\n--- Top 5 Endpoints ---");
        List<Map.Entry<String, AtomicInteger>> endpointList = new ArrayList<>(stats.getByEndpoint().entrySet());
        endpointList.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        count = 0;
        for (var e : endpointList) {
            if (count >= 5) break;
            System.out.println("  " + e.getKey() + ": " + e.getValue().get() + " hits");
            count++;
        }

        List<LogData> slowRequests = stats.getSlowRequests();
        if (!slowRequests.isEmpty()) {
            System.out.println("\n--- Slow Requests (first 10) ---");
            int shown = 0;
            for (LogData entry : slowRequests) {
                if (shown >= 10) break;
                System.out.println("  " + entry);
                shown++;
            }
        }

        // Write detailed report to file
        try {
            writeReport(stats, reportFile, filename);
            System.out.println("\nReport saved to: " + reportFile);
        } catch (IOException ex) {
            System.out.println("Error writing report: " + ex.getMessage());
        }

        System.out.println("\n*****************************");
    }

    // Write analysis report to file
    private static void writeReport(Stats stats, String reportFile, String sourceFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
            writer.write("LOG ANALYSIS REPORT");
            writer.newLine();
            writer.write("==================");
            writer.newLine();
            writer.write("Source file: " + sourceFile);
            writer.newLine();
            writer.write("Worker threads: 4");
            writer.newLine();
            writer.newLine();

            writer.write("SUMMARY");
            writer.newLine();
            writer.write("-------");
            writer.newLine();
            writer.write("Valid entries: " + stats.getTotal());
            writer.newLine();
            writer.write("Invalid entries: " + stats.getCorrupt());
            writer.newLine();

            double avg = stats.getTotal() == 0 ? 0 : (double) stats.getTotalTime() / stats.getTotal();
            writer.write(String.format("Average response time: %.2f ms", avg));
            writer.newLine();
            writer.write("Slowest request: " + stats.getSlowest() + " ms");
            writer.newLine();
            writer.write("Slow requests (>" + stats.slowThreshold + "ms): " + stats.getSlowRequests().size());
            writer.newLine();
            writer.newLine();

            writer.write("REQUESTS BY LEVEL");
            writer.newLine();
            writer.write("-----------------");
            writer.newLine();
            for (var e : stats.getByLevel().entrySet()) {
                writer.write("  " + e.getKey() + ": " + e.getValue().get());
                writer.newLine();
            }
            writer.newLine();

            writer.write("TOP 5 IP ADDRESSES");
            writer.newLine();
            writer.write("------------------");
            writer.newLine();
            List<Map.Entry<String, AtomicInteger>> ipList = new ArrayList<>(stats.getByIp().entrySet());
            ipList.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
            int count = 0;
            for (var e : ipList) {
                if (count >= 5) break;
                writer.write("  " + e.getKey() + ": " + e.getValue().get() + " hits");
                writer.newLine();
                count++;
            }
            writer.newLine();

            writer.write("TOP 5 ENDPOINTS");
            writer.newLine();
            writer.write("---------------");
            writer.newLine();
            List<Map.Entry<String, AtomicInteger>> endpointList = new ArrayList<>(stats.getByEndpoint().entrySet());
            endpointList.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
            count = 0;
            for (var e : endpointList) {
                if (count >= 5) break;
                writer.write("  " + e.getKey() + ": " + e.getValue().get() + " hits");
                writer.newLine();
                count++;
            }
            writer.newLine();

            writer.write("SLOW REQUESTS (>" + stats.slowThreshold + "ms)");
            writer.newLine();
            writer.write("---------------------------");
            writer.newLine();
            List<LogData> slowRequests = stats.getSlowRequests();
            int shown = 0;
            for (LogData entry : slowRequests) {
                if (shown >= 15) {
                    writer.write("  ... and " + (slowRequests.size() - shown) + " more");
                    writer.newLine();
                    break;
                }
                writer.write("  " + entry);
                writer.newLine();
                shown++;
            }
        }
    }
}