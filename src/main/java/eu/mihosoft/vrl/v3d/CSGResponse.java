package eu.mihosoft.vrl.v3d;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class CSGResponse implements Serializable {
	private static final long serialVersionUID = 1L;
	private List<CSG> csgList;
	private CSGRemoteOperation operation;
	private ServerActionState state = ServerActionState.SUCCESS;
	private String message = null;

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

	public ServerActionState getState() {
		return state;
	}

	public void setState(ServerActionState state) {
		this.state = state;
		if (message == null)
			message = state.toString();
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		this.message = sw.toString();
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
