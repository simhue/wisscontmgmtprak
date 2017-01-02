package de.contmgmt.praktikum.vo;

import org.postgis.LineString;

import com.google.gson.Gson;

public class Route {
	private int id;
	private LineString route;
	private String routeAsGeoJson;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public LineString getRoute() {
		return route;
	}
	public void setRoute(LineString route) {
		this.route = route;
	}
	
	public void setRouteAsGeoJSON(String geoJSON) {
		this.routeAsGeoJson = geoJSON;
	}
	
	public String getRouteAsGeoJSON() {
//		Gson gson = new Gson();
//		return gson.toJson(route);
		return routeAsGeoJson;
	}
}
