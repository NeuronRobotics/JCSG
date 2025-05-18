package eu.mihosoft.vrl.v3d.parametrics;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;

public class CSGDatabase {
	private static CSGDatabaseInstance instance = new CSGDatabaseInstance(new File("CSGdatabase.json"));

	public static void set(String key, Parameter value) {
		getInstance().set(key, value);
	}

	public static Parameter get(String key) {
		return getInstance().get(key);
	}

	public static void clear() {
		getInstance().clear();
	}

	public static void addParameterListener(String key, IParameterChanged l) {
		getInstance().addParameterListener(key, l);
	}

	public static void clearParameterListeners(String key) {
		getInstance().clearParameterListeners(key);
	}

	public static void removeParameterListener(String key, IParameterChanged l) {
		getInstance().removeParameterListener(key, l);
	}

	public static CopyOnWriteArrayList<IParameterChanged> getParamListeners(String key) {
		return getInstance().getParamListeners(key);
	}

	public static void delete(String key) {
		getInstance().delete(key);
	}

	public static void loadDatabaseFromFile(File f) {
		 getInstance().loadDatabaseFromFile(f);
	}

	public static String getDataBaseString() {
		return getInstance().getDataBaseString();
	}

	public static void saveDatabase() {
		getInstance().saveDatabase();
	}


	public static File getDbFile() {
		return getInstance().getDbFile();
	}

	public static void setDbFile(File dbFile) {
		getInstance().setDbFile(dbFile);
	}

	public static void reLoadDbFile() {
		getInstance().reLoadDbFile();
	}

	public static CSGDatabaseInstance getInstance() {
		return instance;
	}

	public static void setInstance(CSGDatabaseInstance instance) {
		CSGDatabase.instance = instance;
	}
}
