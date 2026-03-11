package com.asset;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class InitDataRunner {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/asset_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowMultiQueries=true";
        String user = "root";
        String password = "root";
        String sqlFile = "init_data.sql";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sqlFile), StandardCharsets.UTF_8))) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                sqlBuilder.append(line).append("\n");
                if (line.endsWith(";")) {
                    String sql = sqlBuilder.toString();
                    try {
                        stmt.execute(sql);
                    } catch (Exception e) {
                        System.err.println("Error executing SQL: " + sql);
                        e.printStackTrace();
                    }
                    sqlBuilder.setLength(0);
                }
            }
            System.out.println("SQL script executed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
