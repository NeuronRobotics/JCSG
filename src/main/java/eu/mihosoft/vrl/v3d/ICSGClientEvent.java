package eu.mihosoft.vrl.v3d;

public interface ICSGClientEvent {

	public void toSend(CSGRequest request) ;

	public void response(CSGResponse response, CSGRequest request);
}
