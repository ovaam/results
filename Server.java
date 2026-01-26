package ru.hse.network;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Server {
    private static final DateTimeFormatter formatter = 
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Server <port>");
            System.exit(1);
        }
        
        int port = Integer.parseInt(args[0]);
        System.out.println("Starting server on port " + port);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server ready. IP: " + InetAddress.getLocalHost().getHostAddress());
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected from: " + 
                    clientSocket.getInetAddress().getHostAddress());
                
                final Socket socket = clientSocket;
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleClient(Socket clientSocket) {
        InputStream in = null;
        OutputStream out = null;
        try {
            clientSocket.setSoTimeout(60000);
            clientSocket.setTcpNoDelay(true);
            
            in = clientSocket.getInputStream();
            out = clientSocket.getOutputStream();
            
            while (!clientSocket.isClosed() && !clientSocket.isInputShutdown()) {
                byte[] sizeBytes = new byte[8];
                int bytesRead = 0;
                while (bytesRead < 8) {
                    int read = in.read(sizeBytes, bytesRead, 8 - bytesRead);
                    if (read == -1) {
                        return;
                    }
                    bytesRead += read;
                }
                
                long dataSize = bytesToLong(sizeBytes);
                
                if (dataSize < 0 || dataSize > 100_000_000) {
                    System.err.println("Invalid data size: " + dataSize);
                    return;
                }
                
                byte[] data = new byte[(int) dataSize];
                bytesRead = 0;
                while (bytesRead < dataSize) {
                    int read = in.read(data, bytesRead, (int)dataSize - bytesRead);
                    if (read == -1) {
                        return;
                    }
                    bytesRead += read;
                }
                
                String response = LocalDateTime.now().format(formatter);
                byte[] responseBytes = response.getBytes("UTF-8");
                
                out.write(responseBytes);
                out.flush();
                
                if (dataSize % 1000 == 0 || dataSize < 100) {
                    System.out.println("Processed " + dataSize + " bytes from " + 
                        clientSocket.getInetAddress().getHostAddress());
                }
            }
            
        } catch (IOException e) {
        } finally {
            try {
                if (in != null) in.close();
            } catch (IOException e) {
            }
            try {
                if (out != null) out.close();
            } catch (IOException e) {
            }
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
            }
        }
    }
    
    private static long bytesToLong(byte[] bytes) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }
}