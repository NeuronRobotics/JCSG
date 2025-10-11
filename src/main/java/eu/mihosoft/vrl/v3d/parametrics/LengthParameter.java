package eu.mihosoft.vrl.v3d.parametrics;

import java.util.ArrayList;

public class LengthParameter extends Parameter {
//	public LengthParameter(String key, Double defaultValue, ArrayList<Double> options) {
//		this(CSGDatabase.getInstance(),key,defaultValue,options);
//	}
	public LengthParameter(CSGDatabaseInstance instance,String key, Double defaultValue, ArrayList<Double> options) {
		super(instance); 
		ArrayList<String> opts=new ArrayList<String>();
		 for(Object d:options)
			 opts.add(d.toString());
		setup(key, new Long((long) (defaultValue*1000.0)), opts);
	}


	
}
