package de.contmgmt.praktikum;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.postgis.Geometry;
import org.postgis.LineString;
import org.postgis.MultiLineString;
import org.postgis.MultiPoint;
import org.postgis.PGgeometry;
import org.postgis.Point;
import org.postgresql.util.PGobject;
import org.primefaces.event.map.GeocodeEvent;
import org.primefaces.event.map.OverlaySelectEvent;
import org.primefaces.event.map.PointSelectEvent;
import org.primefaces.event.map.StateChangeEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.map.DefaultMapModel;
import org.primefaces.model.map.GeocodeResult;
import org.primefaces.model.map.MapModel;
import org.primefaces.model.map.Marker;
import org.primefaces.model.map.Polyline;

import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.RoadsApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.LatLng;
import com.google.maps.model.SnappedPoint;
import com.google.maps.model.TravelMode;

import de.contmgmt.praktikum.vo.PointVo;
import de.contmgmt.praktikum.vo.RouteVo;

@ManagedBean(name = "MapBean")
@ViewScoped
public class MapBean implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3033537972479491306L;

	/**
	 * Constants
	 */
	private final String G_MAPS_KEY = "AIzaSyAcGWsRky0gILulRmWRU8CCtOjEZfMZvP8";
	private static GeoApiContext geoApiContext;

	private InitialContext ctx;

	private List<String> selectedAddresses;
	private List<RouteVo> routes;
	private List<RouteVo> snappedRoutes;
	private RouteVo newRoute;
	private int radius;
	private String startAddress;
	private boolean isStartAddress;
	private String endAddress;
	private boolean isEndAddress;
	private int distanceUnit;
	
	private MapModel mapModel;
	private int zoom;
	private String center;
	private Marker currentMarker;
	private Marker firstMarker;
	private Marker secondMarker;
	private Polyline selectedPolyline;
	private StreamedContent kmlFile;

	//SQL-Queries
	private final String routesInRadius = "SELECT route.id, route.description, points.id, points.point FROM route join routepoints on route.id = routepoints.routeid join points on routepoints.pointid = points.id WHERE ST_DistanceSphere(point, ST_MakePoint(?, ?)) <= ? * ? order by route.id, points.id";
	private final String return_new_id = " RETURNING id";
	private final String insert_point = "INSERT INTO points(point) VALUES(ST_GeomFromEWKT(?))" + return_new_id;
	private final String insert_route = "INSERT INTO route(description) VALUES(null)" + return_new_id;
	private final String insert_route_points = "INSERT INTO routepoints(routeid, pointid) VALUES(?, ?)";

	private final double STROKE_OPACITY_PASSIVE = 0.45d;
	private final double STROKE_OPACITY_SELECTED = 1d;
	
	private boolean isManually;

	@PostConstruct
	private void init() {
		if (geoApiContext == null) {
			geoApiContext = new GeoApiContext().setApiKey(G_MAPS_KEY);
		}
		mapModel = new DefaultMapModel();
		zoom = 11;
		center = "50.80,6.43";
		reset();
	}

	public void reset() {
		routes = new ArrayList<>();
		snappedRoutes = new ArrayList<>();
		selectedAddresses = new ArrayList<>();
		selectedPolyline = null;
		newRoute = null;
		startAddress = "";
		currentMarker = null;
		clearOverlays();
	}

	private void clearOverlays() {
		if (mapModel != null) {
			mapModel.getPolylines().clear();
			mapModel.getMarkers().clear();
		}
	}

	private FacesContext getFacesContext() {
		return FacesContext.getCurrentInstance();
	}

	private Connection getConnection() throws Exception {
		InitialContext ctx = new InitialContext();
		DataSource ds = (DataSource) ctx.lookup("java:/PostgresDS");
		return ds.getConnection();
	}
	
	public void saveNewRoute() {
		Connection conn;
		try {
			int newRouteId, newPointId;
			ResultSet rs = null;
			conn = getConnection();
			PreparedStatement statement = conn.prepareStatement(insert_route);
			rs = statement.executeQuery();
			rs.next();
			newRouteId = rs.getInt(1);
			
			for(PointVo point : newRoute.getPoints()) {
				statement = conn.prepareStatement(insert_point);
				statement.setString(1, point.getPoint().toString());
				rs = statement.executeQuery();
				rs.next();
				newPointId = rs.getInt(1);
				
				statement = conn.prepareStatement(insert_route_points);
				statement.setInt(1, newRouteId);
				statement.setInt(2, newPointId);
				statement.execute();
			}
			
			statement.close();
			conn.close();
			
			clearOverlays();
			mapModel.addOverlay(currentMarker);
			getRoutesInRadius();
			mapModel.getPolylines().stream().filter(item -> item.getData().equals(newRouteId + "")).collect(Collectors.toList()).get(0).setStrokeOpacity(STROKE_OPACITY_SELECTED);
			
			addInfoMessage("Route gespeichert");
		} catch(Exception e) {
			addErrorMessage("Hoppla! Da ist etwas schief gegangen.");
			e.printStackTrace();
		}
	}
	
	public void getRoutesInRadius() {
		routes.clear();

		Connection conn;
		try {
			org.primefaces.model.map.LatLng latlng = mapModel.getMarkers().stream()
					.filter(item -> item.getTitle().equals("start")).collect(Collectors.toList())
					.get(0).getLatlng();
			
			conn = getConnection();

			PreparedStatement s = conn.prepareStatement(routesInRadius);
			s.setDouble(1, latlng.getLng());
			s.setDouble(2, latlng.getLat());
			s.setInt(3, radius);
			s.setInt(4, distanceUnit);
			ResultSet rs = s.executeQuery();

			RouteVo route = new RouteVo();
			route.setId(-1);
			PointVo point = null;
			boolean hasResults = false;

			while (rs.next()) {
				hasResults = true;
				if (route.getId() != rs.getInt(1)) {
					if (route.getId() != -1) {
						routes.add(route);
					}

					route = new RouteVo();
					route.setId(rs.getInt(1));
					route.setDescription(rs.getString(2));
				}

				point = new PointVo();
				point.setId(rs.getInt(3));
				point.setPoint((Point) PGgeometry.geomFromString(((PGobject) rs.getObject(4)).getValue()));
				route.getPoints().add(point);
			}

			if (hasResults) {
				routes.add(route);
			}

			Integer colorCode = 0x136aad;
			for (RouteVo rVo : routes) {
				Polyline polyline = new Polyline();
				polyline.setStrokeColor(String.format("#%06X", (0xFFFFFF & colorCode)));
				polyline.setData(rVo.getId() + "");
				polyline.setStrokeOpacity(STROKE_OPACITY_PASSIVE);
				polyline.setStrokeWeight(3);
				for (PointVo pVo : rVo.getPoints()) {
					polyline.getPaths().add(convertPointType(pVo.getPoint(), org.primefaces.model.map.LatLng.class));
				}
				mapModel.addOverlay(polyline);
				colorCode += 50;
			}
			s.close();
			conn.close();
		} catch (Exception e) {
			addErrorMessage("Hoppla! Da ist etwas schief gegangen.");
			e.printStackTrace();
		}

		createMarkers();
	}
	
	public void createNewRoute() {
		if(routes.isEmpty())
			return;
		newRoute = new RouteVo();
		Point start = convertPointType(currentMarker.getLatlng(), Point.class);
		Point end = start;
		List<Point> collectedPoints = new ArrayList<>();
		selectedAddresses.add(reverseGeocode(convertPointType(start, LatLng.class)));
		
		for(Marker marker : mapModel.getMarkers()) {
			collectedPoints.add(convertPointType(marker.getLatlng(), Point.class));
		}
		collectedPoints.remove(start);		
		
		Point nextPoint = start, closestPoint = getClosestPoint(start, new MultiPoint(collectedPoints.toArray(new Point[collectedPoints.size()])));
		collectedPoints.remove(closestPoint);
		
		collectedPoints.add(end);
		
		while(true) {
			selectedAddresses.add(reverseGeocode(convertPointType(closestPoint, LatLng.class)));
			newRoute.getPoints().addAll(calculateRoute(convertPointType(nextPoint, LatLng.class), convertPointType(closestPoint, LatLng.class)).getPoints());
			if(closestPoint.equals(end) || collectedPoints.isEmpty()) break;
			
			for(RouteVo route : routes) {
				if(route.getPoints().get(0).getPoint().equals(closestPoint)) {
					nextPoint = route.getPoints().get(route.getPoints().size() - 1).getPoint();
				} else if(route.getPoints().get(route.getPoints().size() - 1).getPoint().equals(closestPoint)) {
					nextPoint = route.getPoints().get(0).getPoint();
					Collections.reverse(route.getPoints());
				}
				
				if(route.getPoints().get(0).getPoint().equals(closestPoint) || 
						route.getPoints().get(route.getPoints().size() - 1).getPoint().equals(closestPoint)) {
					collectedPoints.remove(nextPoint);
					newRoute.getPoints().addAll(route.getPoints());					
					closestPoint = getClosestPoint(nextPoint, new MultiPoint(collectedPoints.toArray(new Point[collectedPoints.size()])));
					collectedPoints.remove(closestPoint);
					break;
				}
			}
		}
//		return route;
	}

	private Point getClosestPoint(Point origin, MultiPoint multiPoint) {
		Connection conn;
		Point result = null;
		origin.setSrid(4326);
		multiPoint.setSrid(4326);
		try {
			conn = getConnection();
			// Procedure call.
			CallableStatement proc = conn.prepareCall("{ ? = call ST_ClosestPoint(ST_GeomFromEWKT(?), ST_GeomFromEWKT(?)) }");
			proc.registerOutParameter(1, Types.OTHER);
			proc.setString(3, origin.toString());
			proc.setString(2, multiPoint.toString());
			proc.execute();
			result = (Point) PGgeometry.geomFromString(((PGobject) proc.getObject(1)).getValue());

			proc.close();
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	private RouteVo calculateRoute(LatLng origin, LatLng destination) {
		DirectionsResult result;
		RouteVo vo = new RouteVo();
		try {
			result = DirectionsApi
					.getDirections(geoApiContext, convertPointType(origin, LatLng.class).toString(),
							convertPointType(destination, LatLng.class).toString())
					.mode(TravelMode.DRIVING).await();
			for (DirectionsRoute dr : result.routes) {
				Polyline polyline = new Polyline();
				polyline.setStrokeColor("#ff0000");
				polyline.setStrokeWeight(3);
				for (LatLng point : dr.overviewPolyline.decodePath()) {
					vo.getPoints().add(new PointVo(convertPointType(point, Point.class)));
					polyline.getPaths().add(convertPointType(point, org.primefaces.model.map.LatLng.class));
				}

				mapModel.addOverlay(polyline);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return vo;
	}

	private String reverseGeocode(LatLng latlng) {
		try {
			return GeocodingApi.reverseGeocode(geoApiContext, latlng).await()[0].formattedAddress;
		} catch (Exception e) {
			return "";
		}
	}
	
	private void snapToRoad(List<PointVo> points) {
		try {
			List<LatLng> latLngList = new ArrayList<>();
			if (points.size() > 100) {
				return;
			}
			points.forEach(x -> latLngList.add(convertPointType(x.getPoint(), LatLng.class)));
			SnappedPoint[] result = RoadsApi
					.snapToRoads(geoApiContext, true, latLngList.toArray(new LatLng[latLngList.size()])).await();
			RouteVo route = new RouteVo();
			for (int i = 0; i < result.length; i++) {
				PointVo point = new PointVo();
				point.setPoint(convertPointType(result[i].location, Point.class));
				route.getPoints().add(point);
			}
			snappedRoutes.add(route);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T convertPointType(Object point, Class<T> clazz) {
		if (point.getClass() == clazz.getClass()) {
			return (T) point;
		}

		double lng, lat;
		if (point instanceof LatLng) {
			lat = ((LatLng) point).lat;
			lng = ((LatLng) point).lng;
		} else if (point instanceof org.primefaces.model.map.LatLng) {
			lat = ((org.primefaces.model.map.LatLng) point).getLat();
			lng = ((org.primefaces.model.map.LatLng) point).getLng();
		} else if (point instanceof Point) {
			lat = ((Point) point).y;
			lng = ((Point) point).x;
		} else {
			return null;
		}

		if (clazz == LatLng.class) {
			return (T) new LatLng(lat, lng);
		} else if (clazz == org.primefaces.model.map.LatLng.class) {
			return (T) new org.primefaces.model.map.LatLng(lat, lng);
		} else if (clazz == Point.class) {
			return (T) new Point(lng, lat);
		} else {
			return null;
		}
	}

	private void createMarkers() {
		if(!isManually) {
			for (Polyline route : handleEmptyCollection(mapModel.getPolylines())) {
				 org.primefaces.model.map.LatLng point = route.getPaths().get(0);
				Marker beginOfRoute = new Marker(convertPointType(point, org.primefaces.model.map.LatLng.class));
				beginOfRoute.setTitle(route.getId() + "");
				
				point = route.getPaths().get(route.getPaths().size() - 1);
				Marker endOfRoute = new Marker(convertPointType(point, org.primefaces.model.map.LatLng.class));
				endOfRoute.setTitle(route.getId() + "");
				
				mapModel.addOverlay(beginOfRoute);
				mapModel.addOverlay(endOfRoute);
	
			}
		}
	}

	private LineString toLineString(List<PointVo> pointVoList) {
		List<Point> pointList = new ArrayList<>();
		for (PointVo pointVo : handleEmptyCollection(pointVoList)) {
			pointList.add(pointVo.getPoint());
		}
		return new LineString(pointList.toArray(new Point[pointList.size()]));
	}

	public String getRoutesAsGeoJson(List<RouteVo> routes) {
		if (routes.isEmpty())
			return "";

		List<LineString> lineStringList = new ArrayList<>();
		for (RouteVo route : routes) {
			lineStringList.add(toLineString(route.getPoints()));
			// snapToRoad(route.getPoints());
		}

		return geomAsGeoJson(new MultiLineString(lineStringList.toArray(new LineString[lineStringList.size()])));
	}

	private String geomAsGeoJson(Geometry geom) {
		Connection conn;
		String geoJson = "";
		try {
			ctx = new InitialContext();
			DataSource ds = (DataSource) ctx.lookup("java:/PostgresDS");
			conn = ds.getConnection();
			// Procedure call.
			CallableStatement proc = conn.prepareCall("{ ? = call ST_AsGeoJson(ST_GeomFromEWKT(?)) }");
			proc.registerOutParameter(1, Types.VARCHAR);
			proc.setString(2, geom.toString());
			proc.execute();
			geoJson = proc.getString(1);

			proc.close();
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return geoJson;
	}

	private <E> List<E> handleEmptyCollection(List<E> list) {
		if (list == null) {
			return Collections.emptyList();
		} else {
			return list;
		}
	}

	public boolean isPointSelected() {
		return currentMarker != null;
	}
	
	private void addInfoMessage(String message) {
		getFacesContext().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, message, ""));
	}
	
	private void addErrorMessage(String message) {
		getFacesContext().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, null, message));
	}
	
	private void addWarningMessage(String message) {
		getFacesContext().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, null, message));
	}

	/**
	 * Action listener
	 */

	public void onGeocode(GeocodeEvent event) {
        List<GeocodeResult> results = event.getResults();
        if (results != null && !results.isEmpty()) {
        	if(results.size() > 1) {
        		addWarningMessage("Mehrere Adressen gefunden. Bitte mehr Details (z.B. PLZ) angeben.");
        		return;
        	} else {
                GeocodeResult result = results.get(0);
                if(isStartAddress) {
                	mapModel.getMarkers().removeIf(marker -> marker.getTitle().equals("start"));
                	mapModel.addOverlay(new Marker(result.getLatLng(), result.getAddress(), "start"));
                } else if (isEndAddress) {
                	mapModel.getMarkers().removeIf(marker -> marker.getTitle().equals("end"));
                	mapModel.addOverlay(new Marker(result.getLatLng(), result.getAddress(), "end"));
                }
                
                isStartAddress = isEndAddress = false;
            }
        } else {
        	addInfoMessage("Keine Adresse gefunden");
        }
    }
	
	public void onStateChange(StateChangeEvent ev) {
		zoom = ev.getZoomLevel();
		center = convertPointType(ev.getCenter(), LatLng.class).toString();
	}

	public void onPointSelect(PointSelectEvent ev) {
		boolean isFirstMarker = currentMarker == null;
		currentMarker = new Marker(ev.getLatLng());
		currentMarker.setTitle("start");
		mapModel.getMarkers().removeIf(marker -> marker.getTitle().equals("start"));
		startAddress = reverseGeocode(convertPointType(currentMarker.getLatlng(), LatLng.class));
		selectedAddresses.clear();
		if (!isFirstMarker) {
			clearOverlays();
		}
		mapModel.addOverlay(currentMarker);
	}

	public void onMarkerSelect(OverlaySelectEvent ev) {
		if(ev.getOverlay() == null) {return;}
		
		if(ev.getOverlay() instanceof Polyline) {
			selectedPolyline = (Polyline) ev.getOverlay();
			
			mapModel.getPolylines().stream()
			.forEach(item -> item.setStrokeOpacity(STROKE_OPACITY_PASSIVE));
			
			mapModel.getPolylines().stream()
			.filter(item -> item.equals(ev.getOverlay())).collect(Collectors.toList()).get(0)
			.setStrokeOpacity(STROKE_OPACITY_SELECTED);
		} else if(ev.getOverlay() instanceof Marker) {
			//disable manual route selection for now
//			selectedAddresses.add(reverseGeocode(convertPointType(((Marker) ev.getOverlay()).getLatlng(), LatLng.class)));
//			if (firstMarker == null) {
//				firstMarker = (Marker) ev.getOverlay();
//			} else {
//				calculateRoute(convertPointType(firstMarker.getLatlng(), LatLng.class), convertPointType(((Marker) ev.getOverlay()).getLatlng(), LatLng.class));
//				firstMarker = null;
//			}
		}
	}
	
	public StreamedContent getKml() {
		List<Point> points = selectedPolyline.getPaths().stream().map(item -> convertPointType(item, Point.class)).collect(Collectors.toList());
		LineString ls = new LineString(points.toArray(new Point[points.size()]));
		ls.setSrid(4326);
		Connection conn;
		String result = "";
		try {
			conn = getConnection();
			// Procedure call.
			CallableStatement proc = conn.prepareCall("{ ? = call ST_AsKML(ST_GeomFromEWKT(?)) }");
			proc.registerOutParameter(1, Types.VARCHAR);
			proc.setString(2, ls.toString());
			proc.execute();
			result = proc.getString(1);

			proc.close();
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Placemark>" + result + "</Placemark></Document></kml>";
		return new DefaultStreamedContent(new ByteArrayInputStream(result.getBytes()), "xml", "tour.kml");
	}
	
	/**
	 * Getter & Setter
	 */
	public boolean isPolylineSelected() {
		return selectedPolyline != null;
	}
	
	public boolean isNewRoute() {
		return newRoute != null;
	}

	public List<RouteVo> getRoutes() {
		return routes;
	}

	public void setRoutes(List<RouteVo> routes) {
		this.routes = routes;
	}

	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {
		this.radius = radius;
	}

	public MapModel getMapModel() {
		return mapModel;
	}

	public void setMapModel(MapModel mapModel) {
		this.mapModel = mapModel;
	}

	public String getGMapsKey() {
		return G_MAPS_KEY;
	}

	public int getZoom() {
		return zoom;
	}

	public void setZoom(int zoom) {
		this.zoom = zoom;
	}

	public String getCenter() {
		return center;
	}

	public void setCenter(String center) {
		this.center = center;
	}

	public String getStart() {
		return startAddress;
	}

	public void setStart(String startAddress) {
		this.startAddress = startAddress;
	}

	public boolean isStartAddress() {
		return isStartAddress;
	}

	public void setStartAddress(boolean isStartAddress) {
		this.isStartAddress = isStartAddress;
	}

	public String getEnd() {
		return endAddress;
	}

	public void setEnd(String endAddress) {
		this.endAddress = endAddress;
	}

	public boolean isEndAddress() {
		return isEndAddress;
	}

	public void setEndAddress(boolean isEndAddress) {
		this.isEndAddress = isEndAddress;
	}

	public int getDistanceUnit() {
		return distanceUnit;
	}

	public void setDistanceUnit(int distanceUnit) {
		this.distanceUnit = distanceUnit;
	}

	public List<String> getSelectedAddresses() {
		return selectedAddresses;
	}

	public void setSelectedAddresses(List<String> selectedAddresses) {
		this.selectedAddresses = selectedAddresses;
	}

}
