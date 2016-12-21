package de.contmgmt.praktikum;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.naming.InitialContext;
import javax.sql.DataSource;


@ManagedBean(name = "MapBean")
@ViewScoped
public class MapBean implements Serializable {
	private List<String> geoJsonList;
	private InitialContext ctx;
	
	@PostConstruct
	private void init() {
		getData();
	}

	private void getData() {
		
		Connection conn;
		geoJsonList = new ArrayList<String>();
		try {
			ctx = new InitialContext();
			DataSource ds = (DataSource) ctx.lookup("java:/PostgresDS");
			conn = ds.getConnection();
			/*
			 * Create a statement and execute a select query.
			 */
			Statement s = conn.createStatement();
//			ResultSet r = s.executeQuery("select ST_AsGeoJson(points::geometry) from route");
			ResultSet r = s.executeQuery("select ST_AsGeoJson(ST_Collect(dp.geom)) from route, ST_DumpPoints(route.points::geometry) as dp limit 10");
			while (r.next()) {
				/*
				 * Retrieve the geometry as an object then cast it to the
				 * geometry type. Print things out.
				 */
				String geom = r.getString(1);
				geoJsonList.add(geom);
			}
			s.close();
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<String> getGeoJsonList() {
		return geoJsonList;
	}

	public void setGeoJsonList(List<String> geoJsonList) {
		this.geoJsonList = geoJsonList;
	}
}
