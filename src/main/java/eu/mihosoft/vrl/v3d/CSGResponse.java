package eu.mihosoft.vrl.v3d;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class CSGResponse implements Serializable {
	private static final long serialVersionUID = 1L;
	private List<CSG> csgList;
	private CSGRemoteOperation operation;

	public CSGResponse() {
		this.csgList = new ArrayList<>();
		this.operation = CSGRemoteOperation.UNION;
	}

	public CSGResponse(List<CSG> csgList, CSGRemoteOperation operation) {
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
}