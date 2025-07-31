
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
import java.util.List;

import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;

;


//  Auto-generated Javadoc
/**
 * The Class STLLoader.
 */
public class STLLoader {

	/**
	 * Instantiates a new STL loader.
	 */
	public STLLoader() {
	}



	/**
	 * Parses the.
	 *
	 * @param f the f
	 * @return the array list
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public ArrayList<Polygon> parse(File f) throws IOException {
		ArrayList<Polygon> polygons = new ArrayList<>();

		// determine if this is a binary or ASCII STL
		// and send to the appropriate parsing method
		// Hypothesis 1: this is an ASCII STL
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line = br.readLine();
			String[] words = line.trim().split("\\s+");
			if (line.indexOf('\0') < 0 && words[0].equalsIgnoreCase("solid")) {
				////// System.out.println("Looks like an ASCII STL");
				parseAscii(f,polygons);
				br.close();
				return polygons;
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
		int triangles = (int) (((buffer[83] & 0xff) << 24) | ((buffer[82] & 0xff) << 16) | ((buffer[81] & 0xff) << 8)
				| (buffer[80] & 0xff));
		if (((f.length() - 84) / 50) == triangles) {
			////// System.out.println("Looks like a binary STL");
			parseBinary(f,polygons, triangles);
			return polygons;
		}
		// System.out.println("File is not a valid STL");

		return polygons;
	}

	/**
	 * Parses the ascii.
	 *
	 * @param f the f
	 */
	private void parseAscii(File f,ArrayList<Polygon> polygons) {
		BufferedReader in=null;
		String line="";
		try {
			in = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		ArrayList<Vertex> vertices = new ArrayList<>();
		try {
			Vector3d normal = new Vector3d(0, 0,0);
			while ((line = in.readLine()) != null) {
				String[] numbers = line.trim().split("\\s+");
				if (numbers[0].equals("vertex")) {
					double x = parseDouble(numbers[1]);
					double y = parseDouble(numbers[2]);
					double z = parseDouble(numbers[3]);
					Vector3d vertex = new Vector3d(x, y, z);
					vertices.add(new Vertex(vertex));
					if(vertices.size()==3) {
						Plane pl = new Plane(normal, vertices);
						try {
							polygons.add(new Polygon(vertices, null, true, pl));
						} catch (ColinearPointsException e) {
							System.out.println(e.getMessage()+ " STL Load Pruned "+vertices);
						}
						vertices.clear();
					}
				} else if (numbers[0].equals("facet") && numbers[1].equals("normal")) {
					normal = new Vector3d(0, 0,0);
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
	 * @param polygons2 
	 */
	private void parseBinary(File f, ArrayList<Polygon> polygons,int triangles) {
		try {
			FileInputStream fis = new FileInputStream(f);
			for (int h = 0; h < 84; h++) {
				fis.read();// skip the header bytes
			}
			for (int t = 0; t < triangles; t++) {
				byte[] tri = new byte[50];
				for (int tb = 0; tb < 50; tb++) {
					tri[tb] = (byte) fis.read();
				}
				ArrayList<Vertex> vertices = new ArrayList<Vertex>();
				Vector3d normal = new Vector3d(0, 0,0);
				normal.x = leBytesToFloat(tri[0], tri[1], tri[2], tri[3]);
				normal.y = leBytesToFloat(tri[4], tri[5], tri[6], tri[7]);
				normal.z = leBytesToFloat(tri[8], tri[9], tri[10], tri[11]);
				for (int i = 0; i < 3; i++) {
					final int j = i * 12 + 12;
					double px = leBytesToFloat(tri[j], tri[j + 1], tri[j + 2], tri[j + 3]);
					double py = leBytesToFloat(tri[j + 4], tri[j + 5], tri[j + 6], tri[j + 7]);
					double pz = leBytesToFloat(tri[j + 8], tri[j + 9], tri[j + 10], tri[j + 11]);
					Vector3d p = new Vector3d(px, py, pz);
					vertices.add(new Vertex(p));
					if(vertices.size()==3) {
						try {
							Plane pl=null;
							try {
								pl = new Plane(normal, vertices);
							}catch(NumberFormatException ex) {
								pl=Plane.createFromPoints(vertices);
							}
							polygons.add(new Polygon(vertices, null, true, pl));
						} catch (ColinearPointsException e) {
							e.printStackTrace();
						}
						vertices.clear();
					}
				}
			}
			fis.close();
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
