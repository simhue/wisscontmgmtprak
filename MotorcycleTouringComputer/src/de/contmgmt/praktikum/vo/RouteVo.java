package de.contmgmt.praktikum.vo;

import java.util.ArrayList;
import java.util.List;

import org.postgis.LineString;

public class RouteVo {
	private int id;
	private LineString route;
	private List<PointVo> points;
	private String description;
	
	public RouteVo() {
		points = new ArrayList<>();
	}
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}

	public List<PointVo> getPoints() {
		return points;
	}

	public void setPoints(List<PointVo> points) {
		this.points = points;
	}
}
