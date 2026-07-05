package eu.mihosoft.vrl.v3d.parametrics;

import java.util.ArrayList;
import java.util.List;

public class StringParameter extends Parameter {

	private List<String> options2;
	// public StringParameter(String key, String defaultValue, ArrayList<String>
	// options) {
	// this(CSGDatabase.getInstance(),key, defaultValue, options);
	// }
	public StringParameter(CSGDatabaseInstance instance, String key, String defaultValue, ArrayList<String> options) {
		super(instance);
		setup(key, defaultValue, options);
		options2 = options;
	}

	public void setString(String s) {
		setStrValue(s);
	}

	public String getString() {
		return (String) getStrValue();
	}

	public List<String> getStringOptions() {
		return new ArrayList<String>(options2);
	}

}
