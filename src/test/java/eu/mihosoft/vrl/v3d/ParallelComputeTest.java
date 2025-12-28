package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.aparapi.device.Device;

public class ParallelComputeTest {

	@Test
	public void test() throws InterruptedException {

		final float inA[] = new float[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 1 };// .... // get a float array of data from
																			// somewhere
		final float inB[] = new float[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 1 };// .... // get a float array of data from
																			// somewhere
		assert (inA.length == inB.length);
		final float[] result = new float[inA.length];

		for (int i = 0; i < result.length; i++) {
			result[i] = i;
		}

		Kernel kernel = new Kernel() {
			@Override
			public void run() {
				int i = getGlobalId();
				//result[i] = inA[i] + inB[i];
				result[i]+=1;
			}
		};

		Device device = Device.best();
		System.out.println("Dev " + device.getShortDescription());
		Range range = device.createRange(result.length);
		kernel.execute(range);
		while (kernel.isExecuting()) {
			Thread.sleep(1);
		} 

		for (int i = 0; i < result.length; i++) {
			System.out.println("Value at " + i + " is " + result[i]);
		}

	}

}
