package de.unileipzig.contentmanagement.praktikum;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
	private static final String url = "http://maps.motorradonline.de/track/kml/";
	// /**
	// * ST_GeomFrom_KML(?) -> convert KML-Format into PostGIS-Geometry-Format
	// the
	// * kml files http://maps.motorradonline.de offers are represented as
	// * "LineString" so ST_DumpPoints returns a Dump all Points of the
	// LineString and ST_Collect
	// * transforms the Dump into MultiPoints
	// */
	// private String insert_statement = "insert into route (points)
	// values(ST_Collect((ST_DumpPoints(ST_GeomFromKML(?))).geom))";
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
			
			Document doc = (Document) kml.getFeature();
			for (Feature feature : doc.getFeature()) {
				if (feature instanceof Placemark) {
					LineString ls = (LineString) ((Placemark) feature).getGeometry();
					for(Coordinate coordinate : ls.getCoordinates()) {
						Point point = new Point();
						point.addToCoordinates(coordinate.getLongitude(), coordinate.getLatitude(), coordinate.getAltitude());
						StringWriter sw = new StringWriter();
						JAXB.marshal(point, sw);
						points.add(sw.toString());
					}
				}
			}

			if (!points.isEmpty()) {
				ResultSet rs = null;
				conn = ConnectionFactory.getConnection();
				
				PreparedStatement statement = conn.prepareStatement(insert_route);
				rs = statement.executeQuery();
				rs.next();
				routeId = rs.getInt(1);
				
				for(String point : points) {
					statement = conn.prepareStatement(insert_point);
					statement.setString(1, point);
					rs = statement.executeQuery();
					rs.next();
					pointId = rs.getInt(1);
					
					statement = conn.prepareStatement(insert_route_points);
					statement.setInt(1, routeId);
					statement.setInt(2, pointId);
					statement.execute();
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} 
	}

	public static void main(String[] args) {
		Crawler c = new Crawler();
		
		for(int i = 0; i <= numberOfIterations; i++) {
			c.processURL(url + (start + i));
		}

		ConnectionFactory.closeConnection();
	}
}
