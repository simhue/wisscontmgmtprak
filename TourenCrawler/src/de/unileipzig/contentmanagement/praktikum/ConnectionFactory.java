package de.unileipzig.contentmanagement.praktikum;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {
	private static Connection conn;
	
	public static Connection getConnection() throws SQLException {
		if(conn == null) {
			conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/ContMgmt", "postgres", "postgres");
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
