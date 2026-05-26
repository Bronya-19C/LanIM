package com.alpha.lanim.dal;

import com.alpha.lanim.util.Constants;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBUtil {

    private static final String DB_PATH = Constants.DEFAULT_DB_PATH;
    private static final String DB_URL = Constants.JDBC_PREFIX + DB_PATH;
    private static boolean initialized = false;

    private String dbUrl;

    public DBUtil() {
        String customPath = System.getProperty("sqlite.path");
        if (customPath != null && !customPath.isEmpty()) {
            this.dbUrl = Constants.JDBC_PREFIX + customPath;
        } else {
            this.dbUrl = DB_URL;
        }
    }

    public static synchronized void init() {
        if (initialized) return;
        new DBUtil().initializeDatabase();
        initialized = true;
    }

    public void initializeDatabase() {
        try {
            Path dbFile = Paths.get(dbUrl.replace(Constants.JDBC_PREFIX, ""));
            Path parentDir = dbFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create data directory", e);
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            DBUtil.class.getClassLoader().getResourceAsStream("sql/schema.sql"),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append("\n");
                }
            }

            for (String part : sql.toString().split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public static Connection getDefaultConnection() throws SQLException {
        String customPath = System.getProperty("sqlite.path");
        if (customPath != null && !customPath.isEmpty()) {
            return DriverManager.getConnection(Constants.JDBC_PREFIX + customPath);
        }
        return DriverManager.getConnection(DB_URL);
    }
}
