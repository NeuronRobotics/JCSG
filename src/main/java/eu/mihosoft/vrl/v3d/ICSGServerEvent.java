package eu.mihosoft.vrl.v3d;

public interface ICSGServerEvent {
	public void starting();

	public void finishedOp(ServerActionState state,CSGServerHandler source);

	public void gotRequest(CSGRemoteOperation operation,CSGServerHandler source);
	
	
	
}
