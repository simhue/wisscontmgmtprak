package de.unileipzig.contentmanagement.praktikum;

import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXB;

import org.jsoup.Jsoup;

import de.micromata.opengis.kml.v_2_2_0.Coordinate;
import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Feature;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.LineString;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.Point;

public class Crawler {
	private static final int start = 80948;
	private static final int numberOfIterations = 100;
	private static final double maxMeanDistanceBetweenTwoPoints = 296.36796346009714;
	private static int numberOfStoredRoutes = 0;
	private static double meanDistanceBetweenTwoPoints = 0;
	private static double meanDistanceBetweenTwoPointsSum = 0;
	private static final String url = "http://maps.motorradonline.de/track/kml/";
	
	
	private final String return_new_id = " RETURNING id";
	private final String insert_point = "INSERT INTO points(point) VALUES(ST_GeomFromKML(?))" + return_new_id;
	private final String insert_route = "INSERT INTO route(description) VALUES(null)" + return_new_id;
	private final String insert_route_points = "INSERT INTO routepoints(routeid, pointid) VALUES(?, ?)";

	public void processURL(String url) {
		Connection conn;
		try {
			int pointId = -1;
			int routeId = -1;
			List<String> points = new ArrayList<String>();

			String kmlAsString = Jsoup.connect(url).get().outerHtml();
			Kml kml = Kml.unmarshal(kmlAsString);
			LineString ls = null;
			
			Document doc = (Document) kml.getFeature();
			for (Feature feature : doc.getFeature()) {
				if (feature instanceof Placemark) {
					ls = (LineString) ((Placemark) feature).getGeometry();
					for(Coordinate coordinate : ls.getCoordinates()) {
						Point point = new Point();
						point.addToCoordinates(coordinate.getLongitude(), coordinate.getLatitude(), coordinate.getAltitude());
						StringWriter sw = new StringWriter();
						JAXB.marshal(point, sw);
						points.add(sw.toString());
					}
				}
			}
			
			//not every kml document has points to store
			if (points.isEmpty()) {return;}
			
			conn = ConnectionFactory.getConnection();
//			CallableStatement proc = conn.prepareCall("{ ? = call ST_Length(ST_GeomFromKML(?)::geography) }");
//			StringWriter sw = new StringWriter();
//			JAXB.marshal(ls, sw);
//			proc.registerOutParameter(1, Types.DOUBLE);
//			proc.setString(2, sw.toString());
//			proc.execute();
//			meanDistanceBetweenTwoPointsSum += proc.getDouble(1) / points.size();
//			if(proc.getDouble(1)  / points.size() > maxMeanDistanceBetweenTwoPoints) {
//				System.out.println("Discarding...");
//				return;
//			}
//			proc.close();
			
			ResultSet rs = null;
			
			PreparedStatement statement = conn.prepareStatement(insert_route);
			rs = statement.executeQuery();
			rs.next();
			routeId = rs.getInt(1);
			for(int i = 0; i < points.size(); i++) {
				String point = points.get(i);
				if(i + 1 < points.size()) {
					String point2 = points.get(i);
					conn = ConnectionFactory.getConnection();
					CallableStatement proc = conn.prepareCall("{ ? = call ST_Distance(ST_GeomFromKML(?)::geography, ST_GeomFromKML(?)::geography) }");
					proc.registerOutParameter(1, Types.DOUBLE);
					proc.setString(2, points.get(i));
					proc.setString(3, points.get(i + 1));
					proc.execute();
					if(proc.getDouble(1) > 1500) {
						System.out.println(proc.getDouble(1) + " m\nDiscarding...");
						
						conn.rollback(); 
						return;
					}
					proc.close();
				}
//			for(String point : points) {
				statement = conn.prepareStatement(insert_point);
				statement.setString(1, point);
				rs = statement.executeQuery();
				rs.next();
				pointId = rs.getInt(1);
				
				statement = conn.prepareStatement(insert_route_points);
				statement.setInt(1, routeId);
				statement.setInt(2, pointId);
				statement.execute();
				statement.close();
			}
			conn.commit();
//			numberOfStoredRoutes++;
		} catch (Exception ex) {
			ex.printStackTrace();
		} 
	}

	public static void main(String[] args) {
		Crawler c = new Crawler();
		
		for(int i = 0; i < numberOfIterations; i++) {
			c.processURL(url + (start + i));
		}
		
//		System.out.println("Number of stored routes: " + numberOfStoredRoutes);
//		System.out.println(meanDistanceBetweenTwoPointsSum / numberOfStoredRoutes);

		ConnectionFactory.closeConnection();
	}
}
