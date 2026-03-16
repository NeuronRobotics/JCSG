package eu.mihosoft.vrl.v3d;

import java.lang.reflect.Type;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class GsonTest {
	@Test
	public void runGson() {
		Type TT_CaDoodleFile = new TypeToken<Bounds>() {
		}.getType();
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation()
				.create();
		Bounds b = new Cube(100).toCSG().getBounds();
		String json = gson.toJson(b);
		// System.out.println(json);
	}
}
