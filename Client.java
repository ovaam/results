package ru.hse.network;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Client {
    private static final SimpleDateFormat dateFormat = 
        new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    
    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: java Client <IP> <port> <N> <M> <Q>");
            System.err.println("Example: java Client 127.0.0.1 12345 8 5000 25");
            System.exit(1);
        }
        
        String serverIP = args[0];
        int port = Integer.parseInt(args[1]);
        int N = Integer.parseInt(args[2]);
        int M = Integer.parseInt(args[3]);
        int Q = Integer.parseInt(args[4]);
        
        System.out.println("Starting client with parameters:");
        System.out.println("  Server: " + serverIP + ":" + port);
        System.out.println("  N=" + N + ", M=" + M + ", Q=" + Q);
        
        Random random = new Random();
        
        String timestamp = dateFormat.format(new Date());
        String csvFilename = String.format("results_%s_N%d_M%d_Q%d.csv", 
            timestamp, N, M, Q);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilename))) {
            writer.println("K,Bytes,AvgTimeMs");
            writer.println("# Client started: " + new Date());
            writer.println("# Parameters: N=" + N + ", M=" + M + ", Q=" + Q);
            
            System.out.println("Writing results to: " + csvFilename);
            System.out.println("Starting measurements...\n");
            
            for (int k = 0; k < M; k++) {
                long dataSize = (long) N * k + 8;
                long totalTime = 0;
                int successfulRuns = 0;
                
                for (int q = 0; q < Q; q++) {
                    if (q > 0) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    int maxRetries = 3;
                    boolean success = false;
                    for (int retry = 0; retry < maxRetries && !success; retry++) {
                        try (Socket socket = new Socket(serverIP, port)) {
                            socket.setTcpNoDelay(true);
                            int timeout = Math.max(30000, (int)Math.min(300000, dataSize / 1000 + 30000));
                            socket.setSoTimeout(timeout);
                            
                            OutputStream out = socket.getOutputStream();
                            InputStream in = socket.getInputStream();
                            
                            byte[] dataToSend = new byte[(int) dataSize];
                            random.nextBytes(dataToSend);
                            
                            byte[] sizeBytes = longToBytes(dataSize);
                            
                            long startTime = System.currentTimeMillis();
                            
                            out.write(sizeBytes);
                            if (dataSize > 100000) {
                                int chunkSize = 65536;
                                int offset = 0;
                                while (offset < dataSize) {
                                    int toSend = (int)Math.min(chunkSize, dataSize - offset);
                                    out.write(dataToSend, offset, toSend);
                                    offset += toSend;
                                }
                            } else {
                                out.write(dataToSend);
                            }
                            out.flush();
                            
                            byte[] responseBytes = new byte[19];
                            int bytesRead = 0;
                            while (bytesRead < 19) {
                                int read = in.read(responseBytes, bytesRead, 19 - bytesRead);
                                if (read == -1) {
                                    throw new IOException("Unexpected end of stream while reading response");
                                }
                                bytesRead += read;
                            }
                            String response = new String(responseBytes, "UTF-8");
                            
                            long endTime = System.currentTimeMillis();
                            long duration = endTime - startTime;
                            
                            totalTime += duration;
                            successfulRuns++;
                            success = true;
                            
                            if (M > 100 && k % (M/10) == 0 && q == 0) {
                                System.out.printf("Progress: %d%% (K=%d, Size=%d bytes)\n", 
                                    (k * 100) / M, k, dataSize);
                            }
                            
                        } catch (SocketTimeoutException e) {
                            if (retry == maxRetries - 1) {
                                if (q == 0) {
                                    System.err.println("Timeout at K=" + k + " after " + maxRetries + " retries");
                                }
                            } else {
                                try {
                                    Thread.sleep(200 * (retry + 1));
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        } catch (IOException e) {
                            if (retry == maxRetries - 1) {
                                if (q == 0) {
                                    System.err.println("Error at K=" + k + ": " + e.getMessage() + " after " + maxRetries + " retries");
                                }
                            } else {
                                try {
                                    Thread.sleep(200 * (retry + 1));
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                }
                
                if (successfulRuns > 0) {
                    long averageTime = totalTime / successfulRuns;
                    
                    writer.println(k + "," + dataSize + "," + averageTime);
                    
                    if (k % 100 == 0 || M <= 100) {
                        System.out.printf("K=%5d | Size=%8d bytes | Avg Time=%5d ms\n", 
                            k, dataSize, averageTime);
                    }
                } else {
                    writer.println(k + "," + dataSize + ",ERROR");
                }
            }
            
            System.out.println("\n✓ Measurements completed!");
            System.out.println("✓ Results saved to: " + csvFilename);
            
            createSummaryFile(csvFilename, N, M, Q, serverIP, port);
            
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }
    
    private static void createSummaryFile(String csvFilename, int N, int M, int Q, 
                                          String ip, int port) {
        String summaryFilename = csvFilename.replace(".csv", "_summary.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryFilename))) {
            writer.println("=== Bandwidth Measurement Summary ===");
            writer.println("Date: " + new Date());
            writer.println("Parameters:");
            writer.println("  Server: " + ip + ":" + port);
            writer.println("  N: " + N);
            writer.println("  M: " + M);
            writer.println("  Q: " + Q);
            writer.println("Data file: " + csvFilename);
            writer.println("\nSystem info:");
            writer.println("  Java: " + System.getProperty("java.version"));
            writer.println("  OS: " + System.getProperty("os.name") + " " + 
                System.getProperty("os.version"));
            writer.println("  Architecture: " + System.getProperty("os.arch"));
            writer.println("\nInstructions:");
            writer.println("1. Open " + csvFilename + " in Excel/Numbers");
            writer.println("2. Select columns A (Bytes) and C (AvgTimeMs)");
            writer.println("3. Insert -> Scatter Chart");
            writer.println("4. X-axis: Bytes, Y-axis: AvgTimeMs");
        } catch (IOException e) {
            System.err.println("Could not create summary file: " + e.getMessage());
        }
    }
}