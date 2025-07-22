package com.piro.bezier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.mihosoft.vrl.v3d.Edge;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;

public class BezierPath {

	static final Matcher matchPoint = Pattern.compile("\\s*(\\d+)[^\\d]+(\\d+)\\s*").matcher("");

	BezierListProducer path;

	private ArrayList<Vector3d> plInternal = new ArrayList<Vector3d>();
	private static int resolutionPoints = 4;

	/** Creates a new instance of Animate */
	public BezierPath() {
	}

	/** Creates a new instance of Animate */
	public BezierPath(String path) {
		parsePathString(path);
	}

	public void parsePathString(String d) {

		this.path = new BezierListProducer();

		parsePathList(d);
	}

	protected void parsePathList(String list) {
		// Oh come on... well i have no idea what this regx is going to parse for, good
		// luck
		final Matcher matchPathCmd = Pattern
				.compile("([MmLlHhVvAaQqTtCcSsZz])|([-+]?((\\d*\\.\\d+)|(\\d+))([eE][-+]?\\d+)?)").matcher(list);

		// Tokenize
		LinkedList<String> tokens = new LinkedList<String>();
		while (matchPathCmd.find()) {
			tokens.addLast(matchPathCmd.group());
		}

		char curCmd = 'Z';
		while (tokens.size() != 0) {
			String curToken = tokens.removeFirst();
			char initChar = curToken.charAt(0);
			if ((initChar >= 'A' && initChar <= 'Z') || (initChar >= 'a' && initChar <= 'z')) {
				curCmd = initChar;
			} else {
				tokens.addFirst(curToken);
			}
			double  x, y;
			switch (curCmd) {
			case 'M':
				x = nextFloat(tokens);
				y = nextFloat(tokens);
				path.movetoAbs(x, y);
				setThePoint(new Vector3d(x, y, 0));
				curCmd = 'L';
				break;
			case 'm':
				x = nextFloat(tokens);
				y = nextFloat(tokens);
				path.movetoRel(x, y);
				setThePoint(new Vector3d(x, y, 0));
				curCmd = 'l';
				break;
			case 'L':
				path.linetoAbs(nextFloat(tokens), nextFloat(tokens));
				setThePoint(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));
				break;
			case 'l':
				path.linetoRel(nextFloat(tokens), nextFloat(tokens));
				setThePoint(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));
				break;
			case 'H':
				path.linetoHorizontalAbs(nextFloat(tokens));

				setThePoint(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));

				break;
			case 'h':
				path.linetoHorizontalRel(nextFloat(tokens));

				setThePoint(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));

				break;
			case 'V':
				path.linetoVerticalAbs(nextFloat(tokens));

				setThePoint(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));

				break;
			case 'v':
				path.linetoVerticalAbs(nextFloat(tokens));
				setThePoint(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));
				break;
			case 'A':
			case 'a':
				break;
			case 'Q':
				path.curvetoQuadraticAbs(nextFloat(tokens), nextFloat(tokens), nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 'q':
				path.curvetoQuadraticAbs(nextFloat(tokens), nextFloat(tokens), nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 'T':
				path.curvetoQuadraticSmoothAbs(nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 't':
				path.curvetoQuadraticSmoothRel(nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 'C':
				path.curvetoCubicAbs(nextFloat(tokens), nextFloat(tokens), nextFloat(tokens), nextFloat(tokens),
						nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 'c':
				path.curvetoCubicRel(nextFloat(tokens), nextFloat(tokens), nextFloat(tokens), nextFloat(tokens),
						nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 'S':
				path.curvetoCubicSmoothAbs(nextFloat(tokens), nextFloat(tokens), nextFloat(tokens), nextFloat(tokens));
				for (double i =0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 's':
				path.curvetoCubicSmoothRel(nextFloat(tokens), nextFloat(tokens), nextFloat(tokens), nextFloat(tokens));
				for (double i = 0; i < 1; i += getResolution()) {
					addingPoint(i);
				}
				addingPoint(1);
				break;
			case 'Z':
			case 'z':
				path.closePath();
				// pointList.add(path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1));
				break;
			case '/':
				// comment line
				break;
			default:
				throw new RuntimeException("Invalid path element");
			}
		}
	}

	private double getResolution() {
		Vector3d start = path.bezierSegs.get(path.bezierSegs.size() - 1).eval(0);
		Vector3d end = path.bezierSegs.get(path.bezierSegs.size() - 1).eval(1);
		double magnitude = start.minus(end).magnitude();
		if(magnitude<Plane.getEPSILON())
			return 1;
		double dpoints = magnitude/0.5;
		double increment = 1.0/dpoints;
		double min = 1.0/((double)getResolutionPoints());
		if(increment<min)
			increment=min;
		if(increment>0.5)
			increment=0.5;
//		System.out.println("Path with inc "+points);
		return increment;
	}

	private boolean addingPoint(double i) {
		Vector3d eval = path.bezierSegs.get(path.bezierSegs.size() - 1).eval(i);
		return setThePoint(eval);
	}

	private boolean setThePoint(Vector3d eval) {
		int end = plInternal.size()-1;

		if(end>0) {
			if(Math.abs(plInternal.get(0).minus(eval).magnitude())<Extrude.getMinimumDIstance()) {
				return false;
			}
			if(Math.abs(plInternal.get(end).minus(eval).magnitude())<Extrude.getMinimumDIstance()) {
				return false;
			}
		}
		if(plInternal.size()>1) {
			Edge e = new Edge(new Vertex(plInternal.get(end-1)), new Vertex(plInternal.get(end)));
			if(e.colinear(eval)) {
				plInternal.set(end, eval);
				return true;
			}
		}
		return plInternal.add(eval);
	}

	static protected double  nextFloat(LinkedList<String> l) {
		String s = l.removeFirst();
		return Float.parseFloat(s);
	}

	/**
	 * Evaluates this animation element for the passed interpolation time. Interp
	 * must be on [0..1].
	 */
	public Vector3d eval(double  interp) {
		Vector3d point = new Vector3d(0, 0);// = new Vector3d();
//		if (interp < 0.001)
//			interp = (double ) 0.001;
//		if (interp > 0.9999)
//			interp = (double ) 0.9999;

		double curLength = path.curveLength * interp;
		for (Iterator<Bezier> it = path.bezierSegs.iterator(); it.hasNext();) {
			Bezier bez = it.next();

			double bezLength = bez.getLength();
			if (curLength < bezLength) {
				double param = curLength / bezLength;
				point = bez.eval(param);
				break;
			}

			curLength -= bezLength;
		}

		return point;
	}

	/**
	 * Evaluates this animation element for the passed interpolation time. Interp
	 * must be on [0..1].
	 */
	public ArrayList<Vector3d> evaluate() {

		return plInternal;
	}

	public static int getResolutionPoints() {
		return resolutionPoints;
	}

	public static void setResolution(int r) {
		resolutionPoints = r;
	}

}
