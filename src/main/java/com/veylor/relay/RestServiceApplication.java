package com.veylor.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RestServiceApplication {

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(RestServiceApplication.class, args);
    }

    private static void loadEnv() {
        java.io.File envFile = findEnvFile();
        if (envFile != null && envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int equalIdx = line.indexOf('=');
                    if (equalIdx > 0) {
                        String key = line.substring(0, equalIdx).trim();
                        String value = line.substring(equalIdx + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("Failed to load .env file: " + e.getMessage());
            }
        }
    }

    private static java.io.File findEnvFile() {
        java.io.File current = new java.io.File(".").getAbsoluteFile();
        while (current != null) {
            java.io.File candidate = new java.io.File(current, ".env");
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
            current = current.getParentFile();
        }
        return null;
    }

}
