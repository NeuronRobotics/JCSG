package com.piro.bezier;

public class BezierHistory {

	Vector2 startPoint = new Vector2();
	Vector2 lastPoint = new Vector2();
	Vector2 lastKnot = new Vector2();

	public BezierHistory() {
	}

	public void setStartPoint(double x, double y) {
		startPoint.set(x, y);
	}

	public void setLastPoint(double x, double y) {
		lastPoint.set(x, y);
	}

	public void setLastKnot(double x, double y) {
		lastKnot.set(x, y);
	}
}
