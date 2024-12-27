
package eu.mihosoft.vrl.v3d.ext.imagej;

/**
 * Fork of
 * https://github.com/fiji/fiji/blob/master/src-plugins/3D_Viewer/src/main/java/customnode/STLLoader.java
 * 
 * TODO: license unclear
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;

import eu.mihosoft.vrl.v3d.Vector3d;

;


//  Auto-generated Javadoc
/**
 * The Class STLLoader.
 */
public class STLLoader {

//        /**
//         * Load the specified stl file and returns the result as a hash map, mapping
//         * the object names to the corresponding <code>CustomMesh</code> objects.
//         */
//        public static Map<String, CustomMesh> load(String stlfile)
//                        throws IOException {
//                STLLoader sl = new STLLoader();
//                try {
//                        sl.parse(stlfile);
//                } catch (RuntimeException e) {
//                        ////System.out.println("error reading " + sl.name);
//                        throw e;
//                }
//                return sl.meshes;
//        }
//
	/**
	 * Instantiates a new STL loader.
	 */
//        private HashMap<String, CustomMesh> meshes;
	public STLLoader() {
	}

	/** The line. */
	String line;

	/** The in. */
	BufferedReader in;

	/** The vertices. */
	// attributes of the currently read mesh
	private ArrayList<Vector3d> vertices = new ArrayList<>();

	/** The normal. */
	private Vector3d normal = new Vector3d(0.0f, 0.0f, 0.0f); // to be used for file checking

	/** The fis. */
	private FileInputStream fis;

	/** The triangles. */
	private int triangles;
//    private DecimalFormat decimalFormat = new DecimalFormat("0.0E0");

	/**
	 * Parses the.
	 *
	 * @param f the f
	 * @return the array list
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public ArrayList<Vector3d> parse(File f) throws IOException {
		vertices.clear();

		// determine if this is a binary or ASCII STL
		// and send to the appropriate parsing method
		// Hypothesis 1: this is an ASCII STL
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line = br.readLine();
			String[] words = line.trim().split("\\s+");
			if (line.indexOf('\0') < 0 && words[0].equalsIgnoreCase("solid")) {
				////// System.out.println("Looks like an ASCII STL");
				parseAscii(f);
				br.close();
				return vertices;
			}
			br.close();
		} catch (java.lang.NullPointerException ex) {
			ex.printStackTrace();
		} // the split cna fail on binary stls
		// Hypothesis 2: this is a binary STL
		FileInputStream fs = new FileInputStream(f);

		// bytes 80, 81, 82 and 83 form a little-endian int
		// that contains the number of triangles
		byte[] buffer = new byte[84];
		fs.read(buffer, 0, 84);
		fs.close();
		triangles = (int) (((buffer[83] & 0xff) << 24) | ((buffer[82] & 0xff) << 16) | ((buffer[81] & 0xff) << 8)
				| (buffer[80] & 0xff));
		if (((f.length() - 84) / 50) == triangles) {
			////// System.out.println("Looks like a binary STL");
			parseBinary(f);
			return vertices;
		}
		// System.out.println("File is not a valid STL");

		return vertices;
	}

	/**
	 * Parses the ascii.
	 *
	 * @param f the f
	 */
	private void parseAscii(File f) {
		try {
			in = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		vertices = new ArrayList<>();
		try {
			while ((line = in.readLine()) != null) {
				String[] numbers = line.trim().split("\\s+");
				if (numbers[0].equals("vertex")) {
					double x = parseDouble(numbers[1]);
					double y = parseDouble(numbers[2]);
					double z = parseDouble(numbers[3]);
					Vector3d vertex = new Vector3d(x, y, z);
					vertices.add(vertex);
				} else if (numbers[0].equals("facet") && numbers[1].equals("normal")) {
					normal.x = parseDouble(numbers[2]);
					normal.y = parseDouble(numbers[3]);
					normal.z = parseDouble(numbers[4]);
				}
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Parses the binary.
	 *
	 * @param f the f
	 */
	private void parseBinary(File f) {
		vertices = new ArrayList<Vector3d>();
		try {
			fis = new FileInputStream(f);
			for (int h = 0; h < 84; h++) {
				fis.read();// skip the header bytes
			}
			for (int t = 0; t < triangles; t++) {
				byte[] tri = new byte[50];
				for (int tb = 0; tb < 50; tb++) {
					tri[tb] = (byte) fis.read();
				}
				normal.x = leBytesToFloat(tri[0], tri[1], tri[2], tri[3]);
				normal.y = leBytesToFloat(tri[4], tri[5], tri[6], tri[7]);
				normal.z = leBytesToFloat(tri[8], tri[9], tri[10], tri[11]);
				for (int i = 0; i < 3; i++) {
					final int j = i * 12 + 12;
					double px = leBytesToFloat(tri[j], tri[j + 1], tri[j + 2], tri[j + 3]);
					double py = leBytesToFloat(tri[j + 4], tri[j + 5], tri[j + 6], tri[j + 7]);
					double pz = leBytesToFloat(tri[j + 8], tri[j + 9], tri[j + 10], tri[j + 11]);
					Vector3d p = new Vector3d(px, py, pz);
					vertices.add(p);
				}
			}
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//    private double parseFloat(String string) throws ParseException {
//        //E+05 -> E05, e+05 -> E05
//        string = string.replaceFirst("[eE]\\+", "E");
//        //E-05 -> E-05, e-05 -> E-05
//        string = string.replaceFirst("e\\-", "E-");
//        return decimalFormat.parse(string).doubleValue();
//    }

	/**
	 * Parses the double.
	 *
	 * @param string the string
	 * @return the double
	 * @throws ParseException the parse exception
	 */
	private double parseDouble(String string) throws ParseException {

		return Double.parseDouble(string);
	}

	/**
	 * Le bytes to double.
	 *
	 * @param b0 the b0
	 * @param b1 the b1
	 * @param b2 the b2
	 * @param b3 the b3
	 * @return the double
	 */
	private double leBytesToFloat(byte b0, byte b1, byte b2, byte b3) {
		return Float.intBitsToFloat((((b3 & 0xff) << 24) | ((b2 & 0xff) << 16) | ((b1 & 0xff) << 8) | (b0 & 0xff)));
	}

}
