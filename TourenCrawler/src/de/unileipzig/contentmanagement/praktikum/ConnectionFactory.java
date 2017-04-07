package de.unileipzig.contentmanagement.praktikum;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class ConnectionFactory {
	private static Connection conn;
	
	public static Connection getConnection() throws SQLException {
		if(conn == null) {
			Properties prop = new Properties();
			InputStream input = null;

			try {

				input = new FileInputStream("config.properties");

				// load a properties file
				prop.load(input);

			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				if (input != null) {
					try {
						input.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			conn = DriverManager.getConnection(prop.getProperty("dburl"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));
			conn.setAutoCommit(false);
		}
		return conn;
	}
	
	public static void closeConnection() {
		if(conn != null) {
			try {
				conn.close();
				conn = null;
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void rollback() {
		try {
			conn.rollback();
		} catch(SQLException ex) {
			ex.printStackTrace();
		}
	}
}
