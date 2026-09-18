# Log Analyzer – Multithreaded Log File Parser

## 1. Project Title

**Log Analyzer – Multithreaded Log File Parser**

## 2. Project Description

Log Analyzer is a Java-based application designed to analyze server log files using multithreading. The program reads log entries, validates their format, identifies corrupted entries, and generates useful statistics such as average response time, slowest request, top IP addresses, and frequently accessed endpoints.

The application uses four worker threads to process different parts of the log file concurrently, improving the efficiency of log processing.

## 3. Problem Statement

Large server log files contain valuable information about system activity, user requests, response times, and errors. Manually analyzing these files is time-consuming and inefficient.

This project provides an automated solution for parsing and analyzing server logs while using multithreading to process large amounts of data efficiently.

## 4. Objectives

* To read and process server log files.
* To validate log entries using Regular Expressions.
* To identify invalid or corrupted log entries.
* To analyze response times.
* To identify slow requests.
* To find the most active IP addresses.
* To find the most frequently accessed endpoints.
* To implement multithreading using Java.
* To generate an analysis report automatically.

## 5. Technologies Used

* **Language:** Java
* **Concepts:** Multithreading, Exception Handling, File Handling, Regular Expressions
* **Concurrency:** ExecutorService, ConcurrentHashMap, AtomicInteger, AtomicLong
* **Input:** Server log file
* **Output:** Console results and analysis report

## 6. Key Features

* Multithreaded log processing
* Four worker threads
* Log format validation
* Custom exception handling
* Corrupted log detection
* Average response-time calculation
* Slow-request detection
* Top 5 IP address analysis
* Top 5 endpoint analysis
* Automatic report generation
* Thread-safe statistics management

## 7. Input Format

The program expects log entries in the following format:

```text
YYYY-MM-DD HH:MM:SS [LEVEL] IP_ADDRESS ENDPOINT "MESSAGE" resp_time_ms=TIME
```

### Example

```text
2026-09-18 10:15:23 [INFO] 192.168.1.10 /api/login "User login successful" resp_time_ms=120
```

## 8. Working of the Project

1. The program reads the input log file.
2. The log file is divided into multiple chunks.
3. Four worker threads are created using `ExecutorService`.
4. Each worker processes its assigned log lines.
5. Regular Expressions are used to validate the log format.
6. Valid entries are stored as structured log data.
7. Invalid entries are counted as corrupted entries.
8. Thread-safe statistics are updated for every valid entry.
9. The program calculates response-time statistics.
10. Top IP addresses and endpoints are identified.
11. Slow requests above 500 ms are detected.
12. A detailed analysis report is generated.

## 9. Multithreading

The project uses a fixed thread pool containing **4 worker threads**.

```java
ExecutorService pool = Executors.newFixedThreadPool(4);
```

The log file is divided into chunks, and each worker thread processes one chunk independently.

Thread-safe classes such as `ConcurrentHashMap`, `AtomicInteger`, `AtomicLong`, and `Collections.synchronizedList()` are used to safely manage shared data.

## 10. Analysis Performed

The program provides the following analysis:

### Total Entries

Counts all valid log entries.

### Invalid Entries

Counts log entries that do not follow the expected format.

### Average Response Time

Calculates the average response time of valid requests.

### Slowest Request

Identifies the request with the highest response time.

### Slow Requests

Requests having a response time greater than **500 ms** are classified as slow requests.

### Requests by Level

Counts requests according to their log level.

### Top 5 IP Addresses

Displays the five IP addresses with the highest number of requests.

### Top 5 Endpoints

Displays the five most frequently accessed endpoints.

## 11. Exception Handling

The project uses a custom exception:

```java
LogFormatException
```

It is used to handle invalid log formats.

The program also handles:

* Invalid response-time values
* Missing input files
* File reading errors
* Processing interruptions
* Unexpected parsing errors

## 12. Output

The application displays the analysis results on the console and generates a report file.

Example:

```text
***** Analysis Results *****

Valid entries: 96
Invalid entries: 4
Average response time: 243.52 ms
Slowest request: 1250 ms
Slow requests (>500ms): 12

--- Requests by Level ---
INFO: 50
DEBUG: 20
ERROR: 15

--- Top 5 IP Addresses ---
192.168.1.10: 25 hits
192.168.1.15: 21 hits

--- Top 5 Endpoints ---
/api/login: 30 hits
/api/users: 24 hits
```

## 13. Generated Report

The program automatically generates:

```text
analysis_report.txt
```

The report contains:

* Source file information
* Number of worker threads
* Valid entries
* Invalid entries
* Average response time
* Slowest request
* Slow requests
* Requests by level
* Top 5 IP addresses
* Top 5 endpoints

## 14. How to Run

### Compile

```bash
javac LogAnalyzer.java
```

### Run with Default Files

```bash
java LogAnalyzer
```

The default input file is:

```text
server.log
```

and the default report file is:

```text
analysis_report.txt
```

### Run with Custom Files

```bash
java LogAnalyzer server.log report.txt
```

## 15. Project Structure

```text
LogAnalyzer/
│
├── LogAnalyzer.java
├── server.log
├── analysis_report.txt
└── README.md
```

## 16. Learning Outcomes

Through this project, the following Java concepts are demonstrated:

* File Handling
* Exception Handling
* Custom Exceptions
* Regular Expressions
* Collections
* Multithreading
* Runnable Interface
* ExecutorService
* Thread Pool
* Concurrent Collections
* Atomic Variables
* Report Generation

## 17. Future Enhancements

* Real-time log monitoring
* Graphical dashboard
* CSV and JSON report generation
* Database integration
* Configurable worker threads
* Configurable slow-request threshold
* Advanced log filtering
* Performance comparison between single-threaded and multithreaded processing

## 18. Author

**Dhairya Agrawal**

**Java Project – September 2026**
