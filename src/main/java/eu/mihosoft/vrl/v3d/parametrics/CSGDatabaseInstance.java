package eu.mihosoft.vrl.v3d.parametrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;

public class CSGDatabaseInstance {

	ConcurrentHashMap<String, Parameter> database = null;
	File dbFile = null;
	final Type TT_mapStringString = new TypeToken<ConcurrentHashMap<String, Parameter>>() {
	}.getType();
	// final Gson gson = new
	// GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
			.excludeFieldsWithoutExposeAnnotation().create();
	final ConcurrentHashMap<String, CopyOnWriteArrayList<IParameterChanged>> parameterListeners = new ConcurrentHashMap<>();

	private HashMap<String, HashMap<String, IParametric>> mapOfAllparametrics = null;

	private HashMap<String, HashMap<String, IParametric>> getMap() {
		if (mapOfAllparametrics == null) {
			mapOfAllparametrics = new HashMap<String, HashMap<String, IParametric>>();
		}
		return mapOfAllparametrics;
	}

	public HashMap<String, IParametric> getMapOfparametrics(CSG source) {
		if (getMap().get(source.getUniqueId()) == null) {
			getMap().put(source.getUniqueId(), new HashMap<>());
		}
		return getMap().get(source.getUniqueId());
	}

	public CSGDatabaseInstance setParameter(CSG instance, Parameter w) {
		setParameter(instance, w, new IParametric() {
			@Override
			public CSG change(CSG oldCSG, String parameterKey, Long newValue) {
				if (parameterKey.contentEquals(w.getName()))
					get(w.getName()).setValue(newValue);
				return oldCSG;
			}
		});
		return this;
	}

	public CSGDatabaseInstance setParameter(CSG instance, String key, double defaultValue, double upperBound,
			double lowerBound, IParametric function) {
		ArrayList<Double> vals = new ArrayList<Double>();
		vals.add(upperBound);
		vals.add(lowerBound);
		setParameter(instance, new LengthParameter(this, key, defaultValue, vals), function);
		return this;
	}

	public CSGDatabaseInstance setParameter(CSG obj, Parameter w, IParametric function) {
		if (w == null)
			return this;
		if (get(w.getName()) == null)
			set(w.getName(), w);
		if (getMapOfparametrics(obj).get(w.getName()) == null)
			getMapOfparametrics(obj).put(w.getName(), function);
		return this;
	}

	public CSGDatabaseInstance setParameterIfNull(CSG instance, String key) {
		if (getMapOfparametrics(instance).get(key) == null)
			getMapOfparametrics(instance).put(key, new IParametric() {

				@Override
				public CSG change(CSG oldCSG, String parameterKey, Long newValue) {
					get(key).setValue(newValue);
					return oldCSG;
				}
			});
		return this;
	}

	public Set<String> getParameters(CSG instance) {

		return getMapOfparametrics(instance).keySet();
	}

	public CSGDatabaseInstance setParameterNewValue(CSG instance, String key, double newValue) {
		IParametric function = getMapOfparametrics(instance).get(key);
		if (function != null) {
			CSG setManipulator = function.change(instance, key, new Long((long) (newValue * 1000)));
			if(setManipulator.hasManipulator())
				try {
					setManipulator.setManipulator(instance.getManipulator());
				} catch (MissingManipulatorException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			setManipulator.setColor(instance.getColor());
			return this;
		}
		return this;
	}

	public CSGDatabaseInstance(File db) {
		dbFile = db;
		if (!dbFile.exists()) {
			try {
				dbFile.createNewFile();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		getDatabase();

	}

	public void set(String key, Parameter value) {
		getDatabase();
		// synchronized(database){
		if (value == null)
			throw new RuntimeException();
		getDatabase().put(key, value);
		// }
	}

	public Parameter get(String key) {
		Parameter ret = null;
		// synchronized(database){
		ret = getDatabase().get(key);
		// }
		return ret;
	}

//	public void clear() {
//
//		getDatabase();
//		// synchronized(database){
//		database.clear();
//		// }
//		parameterListeners.clear();
//		saveDatabase();
//	}

	public void addParameterListener(String key, IParameterChanged l) {
		CopyOnWriteArrayList<IParameterChanged> list = getParamListeners(key);
		if (!list.contains(l)) {
			list.add(l);
		}
	}

	public void clearParameterListeners(String key) {
		synchronized (parameterListeners) {
			CopyOnWriteArrayList<IParameterChanged> back = parameterListeners.get(key);
			if (back == null) {
				back = new CopyOnWriteArrayList<>();
				parameterListeners.put(key, back);
			}
			back.clear();
		}
	}

	public void removeParameterListener(String key, IParameterChanged l) {
		if (parameterListeners.get(key) == null) {
			return;
		}
		CopyOnWriteArrayList<IParameterChanged> list = parameterListeners.get(key);
		if (list.contains(l)) {
			list.remove(l);
		}
	}

	public CopyOnWriteArrayList<IParameterChanged> getParamListeners(String key) {
		synchronized (parameterListeners) {
			CopyOnWriteArrayList<IParameterChanged> back = parameterListeners.get(key);
			if (back == null) {
				back = new CopyOnWriteArrayList<>();
				parameterListeners.put(key, back);
			}
			return back;
		}
	}

	public void delete(String key) {
		// synchronized(database){
		getDatabase().remove(key);
		// }
	}

	private ConcurrentHashMap<String, Parameter> getDatabase() {
		if (database == null) {

			String jsonString;
			try {

				if (!getDbFile().exists()) {
					setDatabase(new ConcurrentHashMap<String, Parameter>());
				} else {
					InputStream in = null;
					try {
						in = FileUtils.openInputStream(getDbFile());
						jsonString = IOUtils.toString(in);
					} finally {
						IOUtils.closeQuietly(in);
					}
					ConcurrentHashMap<String, Parameter> tm = gson.fromJson(jsonString, TT_mapStringString);

					if (tm != null) {
						for (String k : tm.keySet()) {
							tm.get(k).setInstance(this);
						}
						setDatabase(tm);
					} else {
						setDatabase(new ConcurrentHashMap<String, Parameter>());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Failed to load " + dbFile.getAbsolutePath());
				setDatabase(new ConcurrentHashMap<String, Parameter>());
			}

		}
		if (database == null)
			throw new RuntimeException();
		return database;
	}

	public void loadDatabaseFromFile(File f) {
		getDatabase();
		InputStream in = null;
		String jsonString;
		try {
			try {
				in = FileUtils.openInputStream(f);
				jsonString = IOUtils.toString(in);
				ConcurrentHashMap<String, Parameter> tm = gson.fromJson(jsonString, TT_mapStringString);
				if (tm != null)
					for (String k : tm.keySet()) {
						Parameter value = tm.get(k);
						value.setInstance(this);
						set(k, value);
					}
				//saveDatabase();
			} catch (Exception e) {
				// System.out.println(f.getAbsolutePath());
				e.printStackTrace();
			}

		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	public String getDataBaseString() {
		String writeOut = null;
		// synchronized(database){
		writeOut = gson.toJson(database, TT_mapStringString);
		// }
		return writeOut;
	}

	public void saveDatabase() throws Exception {
		if (database == null || database.size() == 0) {
			new Exception("Can not save an empty database! to " + getDbFile().getAbsolutePath()).printStackTrace();
			;
			return;
		}
		String writeOut = getDataBaseString();

		if (!getDbFile().exists()) {
			try {
				getDbFile().createNewFile();
			} catch (IOException e) {
				return;
			}
		}
		OutputStream out = null;
		try {
			out = FileUtils.openOutputStream(getDbFile(), false);
			IOUtils.write(writeOut, out);
			out.flush();
			out.close(); // don't swallow close Exception if copy completes normally
		} finally {
			IOUtils.closeQuietly(out);
		}

	}

	private void setDatabase(ConcurrentHashMap<String, Parameter> database) {
		this.database = database;
	}

	public File getDbFile() {
		return dbFile;
	}

	public void setDbFile(File dbFile) {
		if (!dbFile.exists())
			try {
				dbFile.createNewFile();
			} catch (IOException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
		this.dbFile = dbFile;
		loadDatabaseFromFile(dbFile);
	}

	public void reLoadDbFile() {
		setDbFile(dbFile);
	}
}
