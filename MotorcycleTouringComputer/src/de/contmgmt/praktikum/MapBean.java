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

import org.postgis.LineString;
import org.postgis.PGgeometry;
import org.postgis.PGgeometryLW;
import org.postgresql.util.PGobject;

import de.contmgmt.praktikum.vo.Route;


@ManagedBean(name = "MapBean")
@ViewScoped
public class MapBean implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3033537972479491306L;
	
	private List<Route> routes;
	private List<String> geoJsonList;
	private int radius;
	private String startPoint;
	private String endPoint;
	
	private InitialContext ctx;
	
	
	@PostConstruct
	private void init() {
		getData();
	}

	private void getData() {
		
		Connection conn;
		geoJsonList = new ArrayList<String>();
		routes = new ArrayList<>();
		try {
			ctx = new InitialContext();
			DataSource ds = (DataSource) ctx.lookup("java:/PostgresDS2");
			conn = ds.getConnection();
			
			Statement s = conn.createStatement();
			ResultSet r = s.executeQuery("select id, points::geometry, ST_AsGeoJson(points::geometry) from route limit 50");
// 			ResultSet r = s.executeQuery("select ST_AsGeoJson(ST_Collect(dp.geom)) from route, ST_DumpPoints(route.points::geometry) as dp limit 10");
			Route route = null;
			while (r.next()) {
				route = new Route();
				
				route.setId(r.getInt(1));
				route.setRoute((LineString) PGgeometry.geomFromString(((PGobject) r.getObject(2)).getValue()));;
				route.setRouteAsGeoJSON(r.getString(3));
				
				routes.add(route);
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
	

	public List<Route> getRoutes() {
		return routes;
	}

	public void setRoutes(List<Route> routes) {
		this.routes = routes;
	}

	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {
		this.radius = radius;
	}

	public String getStartPoint() {
		return startPoint;
	}

	public void setStartPoint(String startPoint) {
		this.startPoint = startPoint;
	}

	public String getEndPoint() {
		return endPoint;
	}

	public void setEndPoint(String endPoint) {
		this.endPoint = endPoint;
	}
}
