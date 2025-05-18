package eu.mihosoft.vrl.v3d.parametrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class CSGDatabaseInstance {

	ConcurrentHashMap<String, Parameter> database = null;
	File dbFile = null;
	final Type TT_mapStringString = new TypeToken<ConcurrentHashMap<String, Parameter>>() {
	}.getType();
	final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	final ConcurrentHashMap<String, CopyOnWriteArrayList<IParameterChanged>> parameterListeners = new ConcurrentHashMap<>();

	public CSGDatabaseInstance(File db) {
		dbFile = db;
	}

	public void set(String key, Parameter value) {
		getDatabase();
		// synchronized(database){
		getDatabase().put(key, value);
		// }
	}

	public Parameter get(String key) {
		Parameter ret = null;
		getDatabase();// load database before synchronization
		// synchronized(database){
		ret = getDatabase().get(key);
		// }
		return ret;
	}

	public void clear() {

		getDatabase();
		// synchronized(database){
		database.clear();
		// }
		parameterListeners.clear();
		saveDatabase();
	}

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
			new Thread() {
				public void run() {
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
//					        	////System.out.println("Hash Map loaded from "+jsonString);
//					        	for(String k:tm.keySet()){
//						        	////System.out.println("Key: "+k+" vlaue= "+tm.get(k));
//						        }
								setDatabase(tm);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						// System.out.println(dbFile.getAbsolutePath());
						setDatabase(new ConcurrentHashMap<String, Parameter>());
					}
					Runtime.getRuntime().addShutdownHook(new Thread() {
						@Override
						public void run() {
							saveDatabase();
						}
					});
				}
			}.start();
			long start = System.currentTimeMillis();
			while (database == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// Auto-generated catch block
					e.printStackTrace();
				}
				if ((System.currentTimeMillis() - start) > 500) {
					setDatabase(new ConcurrentHashMap<String, Parameter>());
				}
			}
		}
		return database;
	}

	public void loadDatabaseFromFile(File f) {
		InputStream in = null;
		String jsonString;
		try {
			try {
				in = FileUtils.openInputStream(f);
				jsonString = IOUtils.toString(in);
				ConcurrentHashMap<String, Parameter> tm = gson.fromJson(jsonString, TT_mapStringString);
				if (tm != null)
					for (String k : tm.keySet()) {
						set(k, tm.get(k));
					}
				saveDatabase();
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
		getDatabase();
		// synchronized(database){
		writeOut = gson.toJson(database, TT_mapStringString);
		// }
		return writeOut;
	}

	public void saveDatabase() {
		String writeOut = getDataBaseString();
		try {
			if (!getDbFile().exists()) {
				getDbFile().createNewFile();
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
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void setDatabase(ConcurrentHashMap<String, Parameter> database) {
		if (this.database != null) {
			return;
		}
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
