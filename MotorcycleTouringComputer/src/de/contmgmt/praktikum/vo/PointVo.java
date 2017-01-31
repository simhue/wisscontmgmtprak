package de.contmgmt.praktikum.vo;

import org.postgis.Point;

public class PointVo {
	private int id;
	private Point point;
	
	public PointVo(Point point) {
		this.point = point;
	}
	
	public PointVo() {
		// TODO Auto-generated constructor stub
	}

	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public Point getPoint() {
		return point;
	}
	public void setPoint(Point point) {
		this.point = point;
	}
}
