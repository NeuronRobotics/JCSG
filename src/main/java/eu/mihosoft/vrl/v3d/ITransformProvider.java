package eu.mihosoft.vrl.v3d;

public interface ITransformProvider {
	/**
	 * Provide a Transform based on the incoming state
	 * @param unitVector a unit vector of progress a value from 0 to 1
	 * @param domain The engineering units total for reference in the calculations
	 * @return A transform for the given step
	 */
	public Transform get(double unitVector,double domain);
}
