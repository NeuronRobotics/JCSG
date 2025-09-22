package eu.mihosoft.vrl.v3d.svg;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMGElement;
import org.apache.batik.anim.dom.SVGOMImageElement;
import org.apache.batik.anim.dom.SVGOMPathElement;
import org.apache.batik.anim.dom.SVGOMPolylineElement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.vecmath.Matrix4d;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.svg.SVGItem;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGImageElement;
import org.w3c.dom.svg.SVGPathSegList;
import org.w3c.dom.svg.SVGPointList;

import com.piro.bezier.BezierPath;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Edge;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import javafx.scene.paint.Color;

// CSG.setDefaultOptType(CSG.OptType.CSG_BOUND);
/**
 * Responsible for converting all SVG path elements into MetaPost curves.
 */
public class SVGLoad {
	private static final String PATH_ELEMENT_NAME = "path";
	private static final String GROUP_ELEMENT_NAME = "g";
	private Document svgDocument;
	boolean hp = true;
	private HashMap<String, List<Polygon>> polygonByLayers = null;
	private HashMap<String, ArrayList<CSG>> csgByLayers = new HashMap<String, ArrayList<CSG>>();
	private HashMap<Polygon, Color> colors = new HashMap<>();
//	private ArrayList<CSG> sections = null;
//	private ArrayList<CSG> holes = null;
//
//	private List<Polygon> polygons = null;
	private ISVGLoadProgress progress = null;
	private double thickness;
	private boolean negativeThickness = false;
	private double height = 0;
	private double width = 0;
	private Double scale = null;
	private HashMap<String, Double> units = new HashMap<>();
//	static {
//		units.put("mm", (1/SVGExporter.Scale));
//		units.put("px", 1.0);
//		units.put("cm", units.get("mm")/10.0);
//		units.put("in", units.get("mm")/25.4);
//		units.put("ft", units.get("in")/12.0);
//		units.put("m", units.get("mm")/1000.0);
//
//	}

	private double toPx(String value) {

		for (String key : units.keySet()) {
			if (value.endsWith(key)) {
				String[] split = value.split(key);
				if (key.contentEquals("m") && split.length > 1) {
					// meters but not meters units
					break;
				}
				//// com.neuronrobotics.sdk.common.Log.error("Units set to "+key+" for "+value);
				return Double.parseDouble(split[0]) / units.get(key);
			}
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException ex) {
			// if the size is not defined, then the scale can not be computed, using
			// internal data
			throw ex;
		}
	}

	private void setScale(double value) {

		scale = value;
		if (scale.isInfinite() || scale.isNaN())
			throw new RuntimeException("Scale must be real number");
		units.put("mm", (1 / getScale()));
		units.put("px", 1.0);
		units.put("cm", units.get("mm") / 10.0);
		units.put("in", units.get("mm") / 25.4);
		units.put("ft", units.get("in") / 12.0);
		units.put("m", units.get("mm") / 1000.0);
	}

	private Double getScale() {
		return scale.doubleValue();
	}

	private double toMM(String value) {
		Double px = toPx(value);
		return px * 1 / getScale();
	}

	private static ISVGLoadProgress progressDefault = new ISVGLoadProgress() {

		@Override
		public void onShape(CSG newShape) {
			// Auto-generated method stub

		}
	};

	public void setHolePolarity(boolean p) {
		hp = p;
	}

	/**
	 * Responsible for converting an SVG path element to MetaPost. This will convert
	 * just the bezier curve portion of the path element, not its style. Typically
	 * the SVG path data is provided from the "d" attribute of an SVG path node.
	 */
	class MetaPostPath2 {
		private SVGOMPathElement pathElement;
		private String transform;

		/**
		 * Use to create an instance of a class that can parse an SVG path element to
		 * produce MetaPost code.
		 *
		 * @param pathNode The path node containing a "d" attribute (output as MetaPost
		 *                 code).
		 */
		public MetaPostPath2(Node pathNode) {
			setPathNode(pathNode);
		}

		/**
		 * Converts this object's SVG path to a MetaPost draw statement.
		 * 
		 * @return A string that represents the MetaPost code for a path element.
		 */
		public String toCode() {
			String sb = "";
			SVGOMPathElement pathElement = getPathElement();
			SVGPathSegList pathList = pathElement.getNormalizedPathSegList();
			// String offset = pathElement.getOwnerSVGElement();

			int pathObjects = pathList.getNumberOfItems();
			/*
			 * sb.append( "M "+offset .replaceAll("translate", "") .replaceAll("(", "")
			 * .replaceAll(")", "") +"\n");
			 */
			// sb.append( "//"+getId()+"\n");

			for (int i = 0; i < pathObjects; i++) {
				SVGItem item = (SVGItem) pathList.getItem(i);
				String itemLine = String.format(Locale.US,"%s%n", item.getValueAsString());
				sb += itemLine;
			}

			return sb.toString();
		}

		/**
		 * Typecasts the given pathNode to an SVGOMPathElement for later analysis.
		 * 
		 * @param pathNode The path element that contains curves, lines, and other SVG
		 *                 instructions.
		 */
		private void setPathNode(Node pathNode) {
			this.pathElement = (SVGOMPathElement) pathNode;
		}

		/**
		 * Returns an SVG document element that contains path instructions (usually for
		 * drawing on a canvas).
		 * 
		 * @return An object that contains a list of items representing pen movements.
		 */
		private SVGOMPathElement getPathElement() {
			return this.pathElement;
		}
	}

	/**
	 * Creates an SVG Document given a URI.
	 *
	 * @param uri Path to the file.
	 * @throws Exception Something went wrong parsing the SVG file.
	 */
	public SVGLoad(URI uri) throws IOException {
		setSVGDocument(createSVGDocument(uri));
	}

	/**
	 * Creates an SVG Document given a URI.
	 *
	 * @param f Path to the file.
	 * @throws Exception Something went wrong parsing the SVG file.
	 */
	public SVGLoad(File f) throws IOException {
		setSVGDocument(createSVGDocument(f.toURI()));
	}

	/**
	 * Creates an SVG Document String of SVG data.
	 *
	 * @param data Contents of an svg file
	 * @throws Exception Something went wrong parsing the SVG file.
	 */
	public SVGLoad(String data) throws IOException {
		File tmpsvg = new File(System.getProperty("java.io.tmpdir") + "/" + Math.random());
		tmpsvg.createNewFile();
		FileWriter fw = new FileWriter(tmpsvg.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(data);
		bw.close();
		setSVGDocument(createSVGDocument(tmpsvg.toURI()));
		tmpsvg.deleteOnExit();
	}

	public static ArrayList<CSG> extrude(File f, double thickness) throws IOException {
		return new SVGLoad(f.toURI()).extrude(thickness);

	}

	/**
	 * This function will create a list of polygons that can be exported back to an
	 * SVG
	 * 
	 * @param f the file containing the SVG data
	 * @return
	 * @throws IOException
	 */
	public static HashMap<String, List<Polygon>> toPolygons(File f) throws IOException {
		return new SVGLoad(f.toURI()).toPolygons();

	}

	public HashMap<String, List<Polygon>> toPolygons() {
		if (polygonByLayers == null)
			try {
				loadAllGroups(new Transform());
			} catch (Exception e) {
				// Auto-generated catch block
				e.printStackTrace();
			}

		return getPolygonByLayers();
	}

//	public static ArrayList<CSG> extrude(File f, double thickness) throws IOException {
//		return new SVGLoad(f.toURI()).extrude(thickness);
//	}

	public static ArrayList<CSG> extrude(URI uri, double thickness) throws IOException {

		return new SVGLoad(uri).extrude(thickness);

	}

	private void loadAllGroups(Transform startingFrame) {

		Element documentElement = getSVGDocument().getDocumentElement();
		NodeList pn = documentElement.getChildNodes();// .getElementsByTagName("g");
		String viewbox = documentElement.getAttribute("viewBox");
		try {
			double viewW = Double.parseDouble(viewbox.split(" ")[2]);
			double viewH = Double.parseDouble(viewbox.split(" ")[3]);
			try {
				NamedNodeMap all = documentElement.getAttributes();
//			for(int i=0;i<all.getLength();i++) {
//				System.err.println("Attribute found "+all.item(i).getNodeName());
//			}
				String hval = documentElement.getAttribute("height");
				String wval = documentElement.getAttribute("width");

				setScale(1);// use to compute bounds
				height = toMM(hval);
				width = toMM(wval);
				double value = viewW / width;
				//// com.neuronrobotics.sdk.common.Log.error("Page size height = "+height+"
				//// width ="+width+" with scale "+(int)(value*25.4)+" DPI ");
				setScale(value);
			} catch (Throwable t) {
				// t.printStackTrace();
				height = 0;// toMM(viewH+"px");
				width = 0;// toMM(viewW+"px");
				setScale(3.543307); // Assume 90 DPI and mm
			}
		} catch (NumberFormatException ex) {

			height = 0;
			width = 0;
			setScale(3.543307); // Assume 90 DPI and mm
		}
		// println "Loading groups from "+pn.getClass()
		int pnCount = pn.getLength();
		for (int j = 0; j < pnCount; j++) {
			Node item = pn.item(j);
			//// com.neuronrobotics.sdk.common.Log.error("\tTOP LEVEL :"+item);
			if (SVGOMGElement.class.isInstance(item)) {

				SVGOMGElement element = (SVGOMGElement) item;
				loadGroup(element, startingFrame, "TOP");
			}
			if (SVGOMPathElement.class.isInstance(item) || SVGOMImageElement.class.isInstance(item)) {
				try {
					loadPath(item, startingFrame, "TOP");
				} catch (Throwable t) {

					t.printStackTrace();
				}
			}
		}

	}

	private void loadGroup(SVGOMGElement element, Transform startingFrame, String encapsulatingLayer) {
		Node transforms = element.getAttributes().getNamedItem("transform");
		Transform newFrame = getNewframe(startingFrame, transforms);
		String layername;

		try {
			layername = element.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label");
			if (layername == null || layername.length() == 0)
				throw new RuntimeException();
		} catch (Throwable t) {
			layername = null;
		}
		if (layername == null) {
			layername = encapsulatingLayer;
		} else {
			//// com.neuronrobotics.sdk.common.Log.error("Updated to layer "+layername+"
			//// from "+encapsulatingLayer);
		}
		//// com.neuronrobotics.sdk.common.Log.error("\tGroup " +
		//// element.getAttribute("id") +"\n\t inkscape name: "+layername+ " \n\t root "
		//// + newFrame.getX() + " " + newFrame.getY());

		NodeList children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node n = children.item(i);
			if (SVGOMGElement.class.isInstance(n)) {
				loadGroup((SVGOMGElement) n, newFrame, layername);
			} else {
				//// com.neuronrobotics.sdk.common.Log.error("\tNot group:"+n);
				try {
					loadPath(n, newFrame, layername);
				} catch (Throwable t) {

					t.printStackTrace();
				}
			}
		}
	}

	private Transform getNewframe(Transform startingFrame, Node transforms) {
		if (transforms == null)
			return startingFrame;
		Transform newFrame = new Transform().apply(startingFrame);
		String transformValue = transforms.getNodeValue();
		//// com.neuronrobotics.sdk.common.Log.error("\tApply " + transformValue + "
		//// root " + startingFrame.getX() + " " + startingFrame.getY());
		if (transformValue.contains("translate")) {
			String[] transformValues = transformValue.replaceAll("translate", "").replaceAll("\\(", "")
					.replaceAll("\\)", "").split("\\,");
			newFrame.apply(new Transform().translate(toPx(transformValues[0]), toPx(transformValues[1]), 0));

		} else if (transformValue.contains("rotate")) {
			String[] rotvals = transformValue.replaceAll("rotate", "").replaceAll("\\(", "").replaceAll("\\)", "")
					.split("\\,");
			newFrame = startingFrame.inverse().apply(new Transform().rotZ(-Double.parseDouble(rotvals[0])))
					.apply(startingFrame);

		} else if (transformValue.contains("scale")) {
			String[] transformValues = transformValue.replaceAll("scale", "").replaceAll("\\(", "")
					.replaceAll("\\)", "").split("\\,");
			// //com.neuronrobotics.sdk.common.Log.error(id.getNodeValue() + " " +
			// transformValues);
			double scalex = toPx(transformValues[0]);
			double scaley = toPx(transformValues.length == 2 ? transformValues[1] : transformValues[0]);
			newFrame.scale(scalex, scaley, 1);

		} else if (transformValue.contains("matrix")) {
			String[] transformValues = transformValue.replaceAll("matrix", "").replaceAll("\\(", "")
					.replaceAll("\\)", "").split("\\,");
			// //com.neuronrobotics.sdk.common.Log.error("Matrix found " +new
			// ArrayList<>(Arrays.asList(transformValues)));
			double a = toPx(transformValues[0]);
			double b = toPx(transformValues[1]);
			double c = toPx(transformValues[2]);
			double d = toPx(transformValues[3]);
			double e = toPx(transformValues[4]);
			double f = toPx(transformValues[5]);
			double elemenents[] = { a, c, 0, e, b, d, 0, f, 0, 0, 1, 0, 0, 0, 0, 1 };
			newFrame.apply(new Transform(new Matrix4d(elemenents)));

		}
		return newFrame;
	}

	// SVGOMGElement
	private void loadPath(Node pathNode, Transform startingFrame, String encapsulatingLayer) {
		Transform newFrame;
		// NodeList pathNodes = element.getElementsByTagName("path");
		// Node transforms = element.getAttributes().getNamedItem("transform");
		if (pathNode != null) {
			// //com.neuronrobotics.sdk.common.Log.error("\tPath
			// "+pathNode.getAttributes().getNamedItem("id").getNodeValue());
			//// com.neuronrobotics.sdk.common.Log.error("Path loading "+pathNode);

			newFrame = startingFrame;
			try {
				Node transforms = pathNode.getAttributes().getNamedItem("transform");
				newFrame = getNewframe(startingFrame, transforms);
			} catch (Exception ex) {
				// no transform
			}
			try {
				if (SVGOMPathElement.class.isInstance(pathNode)) {
//						NamedNodeMap attribs = pathNode.getAttributes();
//						for(int i=0;i<attribs.getLength();i++) {
//							Node n = attribs.item(i);
//							String namespaceURI = n.getNamespaceURI();
//							String nodeName = n.getNodeName();
//							System.out.print("\nName "+nodeName+" namespace "+namespaceURI);
//							try {
//								System.out.print(" value "+attribs.getNamedItemNS(namespaceURI, nodeName).getNodeValue());
//							}catch(Exception ex) {
//								
//							}
//							//com.neuronrobotics.sdk.common.Log.error("");
//							
//						}
					Color c = null;
					//// com.neuronrobotics.sdk.common.Log.error("Layer "+encapsulatingLayer);
					try {
						String[] style = pathNode.getAttributes().getNamedItem("style").getNodeValue().split(";");
						for (String s : style) {
							if (s.startsWith("fill:")) {
								String string = s.split(":")[1];
								c = Color.web(string);
								break;
							}
						}
					} catch (java.lang.IllegalArgumentException ex) {
						// this means the fill is set to "none"
					} catch (Exception ex) {
						// ex.printStackTrace();
					}
					if (c == null) {
						try {
							String[] style = pathNode.getAttributes().getNamedItem("style").getNodeValue().split(";");
							for (String s : style) {
								if (s.startsWith("stroke:")) {
									String string = s.split(":")[1];
									c = Color.web(string);
									break;
								}
							}
						} catch (java.lang.IllegalArgumentException ex1) {
							// this means the stroke is set to "none" the default green color will be used

						} catch (Exception ex1) {
							// ex1.printStackTrace();
						}
					}
					MetaPostPath2 mpp = new MetaPostPath2(pathNode);
					String code = mpp.toCode();
					//// com.neuronrobotics.sdk.common.Log.error("\tPath
					//// "+pathNode.getAttributes().getNamedItem("id").getNodeValue()+" "+newFrame);
					loadComposite(code, newFrame, encapsulatingLayer, c);
				} else if (SVGOMPolylineElement.class.isInstance(pathNode)) {
					Color c = null;
					//// com.neuronrobotics.sdk.common.Log.error("Layer "+encapsulatingLayer);
					try {
						String[] style = pathNode.getAttributes().getNamedItem("style").getNodeValue().split(";");
						for (String s : style) {
							if (s.startsWith("fill:")) {
								String string = s.split(":")[1];
								c = Color.web(string);
								break;
							}
						}
					} catch (java.lang.IllegalArgumentException ex) {
						// this means the fill is set to "none"
					} catch (Exception ex) {
						// ex.printStackTrace();
					}
					try {
						c = Color.web(pathNode.getAttributes().getNamedItem("stroke").getNodeValue());
					} catch (java.lang.IllegalArgumentException ex) {
						// this means the fill is set to "none"
					} catch (Exception ex) {
						// ex.printStackTrace();
					}
					if (c == null) {
						try {
							String[] style = pathNode.getAttributes().getNamedItem("style").getNodeValue().split(";");
							for (String s : style) {
								if (s.startsWith("stroke:")) {
									String string = s.split(":")[1];
									c = Color.web(string);
									break;
								}
							}
						} catch (java.lang.IllegalArgumentException ex1) {
							// this means the stroke is set to "none" the default green color will be used

						} catch (Exception ex1) {
							// ex1.printStackTrace();
						}
					}

					String sb = null;
					SVGOMPolylineElement pathElement = (SVGOMPolylineElement) pathNode;
					SVGPointList pathList = pathElement.getPoints();
					// String offset = pathElement.getOwnerSVGElement();

					int pathObjects = pathList.getNumberOfItems();

					for (int i = 0; i < pathObjects; i++) {
						SVGItem item = (SVGItem) pathList.getItem(i);
						String itemLine = String.format(Locale.US,"%s%n", item.getValueAsString());
						if (sb == null) {
							sb = "M " + itemLine;
						}
						sb += "L " + itemLine;
					}
					sb += "z\n";
					loadComposite(sb, newFrame, encapsulatingLayer, c);
				} else if (SVGOMImageElement.class.isInstance(pathNode)) {
					SVGImageElement image = (SVGOMImageElement) pathNode;
					//// com.neuronrobotics.sdk.common.Log.error("Loading Image element..");
					double x = toPx(image.getAttributes().getNamedItem("x").getNodeValue());
					double y = toPx(image.getAttributes().getNamedItem("y").getNodeValue());
					double pheight = toPx(image.getAttributes().getNamedItem("height").getNodeValue());
					double pwidth = toPx(image.getAttributes().getNamedItem("width").getNodeValue());
					String[] imageData = null;
					for (int i = 0; i < image.getAttributes().getLength(); i++) {
						Node n = image.getAttributes().item(i);
						if (n.getNodeName().contains("href"))
							try {
								imageData = n.getNodeValue().split("/");

							} catch (Exception e) {
								// Auto-generated catch block
								e.printStackTrace();
							}
					}
					// TODO parse the Image string into an Image object
					// for(int i=0;i<10;i++)
					// //com.neuronrobotics.sdk.common.Log.error(imageData[i]);
				}
			} catch (java.lang.ClassCastException ex) {
				// attempt to load image
				// com.neuronrobotics.sdk.common.Log.error("Found "+pathNode.getClass());
				// ex.printStackTrace();
			}

		}

	}

	private void loadComposite(String code, Transform startingFrame, String encapsulatingLayer, Color c) {
		// Count the occourences of M
		int count = code.length() - code.replace("M", "").length();
		if (count < 2) {
			// println "Single path found"

			// setHolePolarity(true);
//			try {
			loadSingle(code, startingFrame, encapsulatingLayer, c);
//			} catch (Exception ex) {
//				//com.neuronrobotics.sdk.common.Log.error("Polygon failed to load!");
//				ex.printStackTrace();
//				// BowlerStudio.printStackTrace(ex);
//			}
		} else {

			// setHolePolarity(false);
			String[] pathParts = code.split("M");
			// println "Complex path found "+ pathParts.length ;
			for (int i = 0; i < pathParts.length; i++) {
				String sectionedPart = "M" + pathParts[i];
				// reapply the split char
				if (sectionedPart.length() > 1) {

					// println "Seperated complex: "
					loadSingle(sectionedPart, startingFrame, encapsulatingLayer, c);
				}
			}
		}
		// //com.neuronrobotics.sdk.common.Log.error("SVG has this many elements loaded:
		// "+sections.size());
		// BowlerStudioController.setCsg(sections,null);
	}

	public static boolean isCCW(Polygon polygon) {
		double runningTotal = 0;
		List<Edge> edges = Edge.fromPolygon(polygon);
		for (Edge e : edges) {
			// runningTotal+=((e.getP1().pos.x-e.getP2().pos.x)*(e.getP1().pos.y-e.getP2().pos.y));
			runningTotal += e.getP1().pos.x * e.getP2().pos.y;
			runningTotal -= e.getP2().pos.x * e.getP1().pos.y;
		}

		return runningTotal < 0;
	}

	private void loadSingle(String code, Transform startingFrame, String encapsulatingLayer, Color c) {
		if (encapsulatingLayer == null)
			throw new RuntimeException("Layer Name can not be null");
		// println code
		BezierPath path = new BezierPath(5);
		path.parsePathString(code);

		ArrayList<Vector3d> p = path.evaluate();
		for (Vector3d point : p) {
			point.transform(startingFrame);
			point.transform(new Transform().scale((1.0 / getScale())));
			point.transform(new Transform().translate(0, -height, 0));
			point.transform(new Transform().rotZ(-180));
			point.transform(new Transform().rotY(180));
		}

		// //com.neuronrobotics.sdk.common.Log.error(" Path " + code);
		// Polygon poly = Polygon.fromPoints(p);
		if (p.size() > 2) {
			if (getPolygonByLayers() == null)
				setPolygonByLayers(new HashMap<String, List<Polygon>>());
			if (getPolygonByLayers().get(encapsulatingLayer) == null)
				getPolygonByLayers().put(encapsulatingLayer, new ArrayList<Polygon>());
			try {
				boolean hole = !Extrude.isCCWv3d(p);
				if (hole)
					Collections.reverse(p);
				List<Polygon> list = getPolygonByLayers().get(encapsulatingLayer);
				// Polygon poly = Polygon.fromPoints(p , new PropertyStorage(), new Plane(new
				// Vector3d(0, 0, 1),p.get(0)) , true);
				Polygon poly = Polygon.fromPoints(p);
				PolygonUtil.triangulatePolygon(poly);
				poly.setHole(hole);
				if (c != null) {
					colors.put(poly, c);
					poly.setColor(c);
				}
				list.add(poly);
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public List<String> getLayers() {
		ArrayList<String> layers = new ArrayList<String>();
		if (getPolygonByLayers() == null) {
			toPolygons();
		}
		for (Object key : getPolygonByLayers().keySet().stream().sorted().toArray())
			layers.add((String) key);
		return layers;
	}

	public CSG extrudeLayerToCSG(double t, String layer) {
		CSG unionAll = CSG.unionAll(extrudeLayers(t, layer).get(layer));
		unionAll.setName(layer);

		return unionAll;
	}

	public ArrayList<CSG> extrudeLayer(double t, String layer) {
		return extrudeLayers(t, layer).get(layer);
	}

	public HashMap<String, ArrayList<CSG>> extrudeLayers(double t) {
		return extrudeLayers(t, null);
	}

	public HashMap<String, ArrayList<CSG>> extrudeLayers(double t, String targetLayer) {
		this.thickness = t;

		if (thickness < 0) {
			thickness = -thickness;
			negativeThickness = true;
		} else {
			negativeThickness = false;
		}

		toPolygons();

		for (String key : getPolygonByLayers().keySet()) {
			if (targetLayer != null)
				if (!targetLayer.contentEquals(key))
					continue;
			if (csgByLayers.get(key) == null) {
				csgByLayers.put(key, new ArrayList<CSG>());
			}
			ArrayList<CSG> parts = csgByLayers.get(key);
			parts.clear();

			for (Polygon p : getPolygonByLayers().get(key)) {
				try {
					PolygonUtil.triangulatePolygon(p);
					boolean isHole = p.isHole();
					CSG newbit;
					try {
						newbit = Extrude.getExtrusionEngine().extrude(new Vector3d(0, 0, thickness + (isHole ? 1 : 0)),
								p);
						if (negativeThickness) {
							newbit = newbit.toZMax();
						}
						if (colors.get(p) != null) {
							newbit.setColor(colors.get(p));
						}
						if (isHole) {
							// newbit=newbit.movez(negativeThickness?0.5:-0.5);
							newbit.setIsHole(true);
						}
						parts.add(newbit);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} catch (ColinearPointsException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}

		return csgByLayers;
	}

	public Color getColor(Polygon p) {
		return colors.get(p);
	}

	public ArrayList<CSG> extrude(double t) throws IOException {

		HashMap<String, ArrayList<CSG>> layers = extrudeLayers(t, null);
		ArrayList<CSG> all = new ArrayList<CSG>();
		for (String key : layers.keySet()) {
			all.addAll(layers.get(key));
		}

		return all;
	}

	/**
	 * This will set the document to parse. This method also initializes the SVG DOM
	 * enhancements, which are necessary to perform SVG and CSS manipulations. The
	 * initialization is also required to extract information from the SVG path
	 * elements.
	 *
	 * @param document The document that contains SVG content.
	 */
	public void setSVGDocument(Document document) {
		initSVGDOM(document);
		this.svgDocument = document;
	}

	/**
	 * Returns the SVG document parsed upon instantiating this class.
	 * 
	 * @return A valid, parsed, non-null SVG document instance.
	 */
	public Document getSVGDocument() {
		return this.svgDocument;
	}

	/**
	 * Enhance the SVG DOM for the given document to provide CSS- and SVG-specific
	 * DOM interfaces.
	 * 
	 * @param document The document to enhance.
	 * @link http://wiki.apache.org/xmlgraphics-batik/BootSvgAndCssDom
	 */
	private void initSVGDOM(Document document) {
		UserAgent userAgent = new UserAgentAdapter();
		DocumentLoader loader = new DocumentLoader(userAgent);
		BridgeContext bridgeContext = new BridgeContext(userAgent, loader);
		bridgeContext.setDynamicState(BridgeContext.DYNAMIC);

		// Enable CSS- and SVG-specific enhancements.
		(new GVTBuilder()).build(bridgeContext, document);
	}

	/**
	 * Use the SAXSVGDocumentFactory to parse the given URI into a DOM.
	 * 
	 * @param uri The path to the SVG file to read.
	 * @return A Document instance that represents the SVG file.
	 * @throws Exception The file could not be read.
	 */
	private Document createSVGDocument(URI uri) throws IOException {
		String parser = XMLResourceDescriptor.getXMLParserClassName();
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
		return factory.createDocument(uri.toString());
	}

	public ISVGLoadProgress getProgress() {
		return progress;
	}

	public void setProgress(ISVGLoadProgress progress) {
		this.progress = progress;
	}

	public static ISVGLoadProgress getProgressDefault() {
		return progressDefault;
	}

	private HashMap<String, List<Polygon>> getPolygonByLayers() {
		return polygonByLayers;
	}

	private void setPolygonByLayers(HashMap<String, List<Polygon>> polygonByLayers) {
		this.polygonByLayers = polygonByLayers;
	}

}
