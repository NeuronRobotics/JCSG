package eu.mihosoft.vrl.v3d;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class CSGRequest implements Serializable {
	private static final long serialVersionUID = 1L;
	private List<CSG> csgList;
	private CSGRemoteOperation operation;
	private String APIKEY;
	private List<Vector3d> points;
	private PropertyStorage storage;

	public CSGRequest() {
		this.csgList = new ArrayList<>();
		this.operation = CSGRemoteOperation.UNION;
	}

	public CSGRequest(List<CSG> csgList, CSGRemoteOperation operation,List<Vector3d> points, PropertyStorage storage) {
		this.setPoints(points);
		this.setStorage(storage);
		this.csgList = csgList != null ? new ArrayList<>(csgList) : new ArrayList<>();
		this.operation = operation != null ? operation : CSGRemoteOperation.UNION;
	}

	public List<CSG> getCsgList() {
		return csgList;
	}

	public void setCsgList(List<CSG> csgList) {
		this.csgList = csgList != null ? new ArrayList<>(csgList) : new ArrayList<>();
	}

	public CSGRemoteOperation getOperation() {
		return operation;
	}

	public void setOperation(CSGRemoteOperation operation) {
		this.operation = operation != null ? operation : CSGRemoteOperation.UNION;
	}

	@Override
	public String toString() {
		return "CSGRequest{operation=" + operation + ", csgCount=" + csgList.size() + "}";
	}

	public String getAPIKey() {
		return APIKEY;
	}

	public void setAPIKEY(String aPIKEY) {
		APIKEY = aPIKEY;
	}

	public List<Vector3d> getPoints() {
		return points;
	}

	public void setPoints(List<Vector3d> points) {
		this.points = points;
	}

	public PropertyStorage getStorage() {
		return storage;
	}

	public void setStorage(PropertyStorage storage) {
		this.storage = storage;
	}
}