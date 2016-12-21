package de.unileipzig.contentmanagement.praktikum;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.xml.bind.JAXB;

import org.jsoup.Jsoup;

import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Feature;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.LineString;
import de.micromata.opengis.kml.v_2_2_0.Placemark;

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
	private String insert_statement = "insert into route (points) values(ST_GeomFromKML(?))";

	public void processURL(String url) {
		Connection conn;
		try {
			String lineString = null;

			String kmlAsString = Jsoup.connect(url).get().outerHtml();
			Kml kml = Kml.unmarshal(kmlAsString);

			Document doc = (Document) kml.getFeature();
			for (Feature feature : doc.getFeature()) {
				if (feature instanceof Placemark) {
					LineString ls = (LineString) ((Placemark) feature).getGeometry();
					if (ls.getCoordinates().size() > 0) {
						StringWriter sw = new StringWriter();
						JAXB.marshal(ls, sw);
						lineString = sw.toString();
					}
				}
			}

			if (lineString != null) {
				conn = ConnectionFactory.getConnection();

				PreparedStatement statement = conn.prepareStatement(insert_statement);
				statement.setString(1, lineString);

				statement.executeUpdate();
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
