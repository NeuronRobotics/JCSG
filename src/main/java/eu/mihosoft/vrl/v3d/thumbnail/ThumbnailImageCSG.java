package eu.mihosoft.vrl.v3d.thumbnail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Transform;
import javafx.scene.PerspectiveCamera;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Rotate;

public class ThumbnailImageCSG {
	private static CullFace cullFaceValue = CullFace.BACK;
	private static int ImageSize = 1000;
	private WritableImage img;

	public Bounds getSellectedBounds(List<CSG> incoming) {
		Vector3d min = null;
		Vector3d max = null;
		for (CSG c : incoming) {
			if (c.isHide())
				continue;
			if (c.isInGroup())
				continue;
			Vector3d min2 = c.getBounds().getMin().clone();
			Vector3d max2 = c.getBounds().getMax().clone();
			if (min == null)
				min = min2;
			if (max == null)
				max = max2;
			if (min2.x < min.x)
				min.x = min2.x;
			if (min2.y < min.y)
				min.y = min2.y;
			if (min2.z < min.z)
				min.z = min2.z;
			if (max.x < max2.x)
				max.x = max2.x;
			if (max.y < max2.y)
				max.y = max2.y;
			if (max.z < max2.z)
				max.z = max2.z;
		}
		if (max == null)
			max = new Vector3d(0, 0, 0);
		if (min == null)
			min = new Vector3d(0, 0, 0);
		return new Bounds(min, max);
	}

	public WritableImage get(List<CSG> c, CSGDatabaseInstance instance) {
		ArrayList<CSG> csgList = new ArrayList<CSG>();
		for (CSG cs : c) {
			if (cs.hasManipulator()) {
				try {
					csgList.add(cs.transformed(TransformConverter.fromAffine(cs.getManipulator()))
							.syncProperties(instance, cs));
				} catch (MissingManipulatorException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else
				csgList.add(cs);
		}
		// Create a group to hold all the meshes
		Group root = new Group();

		// Add all meshes to the group
		Bounds b = getSellectedBounds(csgList);

		double yOffset = (b.getMax().y - b.getMin().y) / 2;
		double xOffset = (b.getMax().x - b.getMin().x) / 2;
		double zCenter = (b.getMax().z - b.getMin().z) / 2;
		for (CSG csg : csgList) {
			if (csg.isHide())
				continue;
			if (csg.isInGroup())
				continue;
			MeshView meshView = csg.movez(-zCenter).getMesh();
			if (csg.isHole()) {
				PhongMaterial material = new PhongMaterial();
				material.setDiffuseColor(new Color(0.25, 0.25, 0.25, 0.75));
				material.setSpecularColor(javafx.scene.paint.Color.WHITE);
				meshView.setMaterial(material);
				meshView.setOpacity(0.25);
			}
			meshView.setCullFace(getCullFaceValue());
			root.getChildren().add(meshView);
		}

		// Calculate the bounds of all CSGs combined
		double totalz = b.getMax().z - b.getMin().z;
		double totaly = b.getMax().y - b.getMin().y;
		double totalx = b.getMax().x - b.getMin().x;

		// Create a perspective camera
		PerspectiveCamera camera = new PerspectiveCamera(true);

		// Calculate camera position to fit all objects in view
		double maxDimension = Math.max(totalx, Math.max(totaly, totalz));
		double cameraDistance = (maxDimension / Math.tan(Math.toRadians(camera.getFieldOfView() / 2))) * 0.8;

		// TransformNR camoffset = new TransformNR(xOffset, yOffset, 0);
		// TransformNR camDist = new TransformNR(0, 0, -cameraDistance);
		// TransformNR rot = new TransformNR(new RotationNR(-150, 45, 0));
		//
		// Affine af = TransformFactory.nrToAffine(camoffset.times(rot.times(camDist)));
		Affine camDist = new Affine();
		camDist.setTz(-cameraDistance);
		Rotate rot1 = new Rotate(45, Rotate.Z_AXIS);
		Rotate rot2 = new Rotate(-150, Rotate.Y_AXIS);
		Affine camoffset = new Affine();
		camoffset.setTx(xOffset);
		camoffset.setTy(yOffset);
		camera.getTransforms().add(camoffset);
		camera.getTransforms().add(rot2);
		camera.getTransforms().add(rot1);
		camera.getTransforms().add(camDist);
		//

		Scene scene = new Scene(root, getImageSize(), getImageSize(), true, SceneAntialiasing.BALANCED);
		scene.setFill(Color.TRANSPARENT);
		scene.setCamera(camera);

		// Set up snapshot parameters
		SnapshotParameters params = new SnapshotParameters();
		params.setFill(Color.TRANSPARENT);
		params.setCamera(camera);
		params.setDepthBuffer(true);
		params.setTransform(Transform.scale(1, 1));
		// Set the near and far clip
		camera.setNearClip(0.1); // Set the near clip plane
		camera.setFarClip(9000.0); // Set the far clip plane

		// Create the WritableImage first
		WritableImage snapshot = new WritableImage(getImageSize(), getImageSize());

		root.snapshot(params, snapshot);

		return snapshot;
	}

	public void writeImage(CSGDatabaseInstance instance, CSG incoming, File toPNG) {
		ArrayList<CSG> bits = new ArrayList<CSG>();
		bits.add(incoming);
		writeImage(instance, bits, toPNG);
	}

	public void writeImage(CSGDatabaseInstance instance, List<CSG> incoming, File toPNG) {
		img = null;

		File image = toPNG;
		javafx.application.Platform.runLater(() -> img = get(incoming, instance));
		long start = System.currentTimeMillis();
		while (img == null) {
			try {
				Thread.sleep(16);
				// com.neuronrobotics.sdk.common.Log.error("Waiting for image to write");
			} catch (InterruptedException e) {
				// Auto-generated catch block
				e.printStackTrace();
				return;
			}
			if ((System.currentTimeMillis() - start) > 500) {
				System.err.println("Image failed to render!");
				throw new RuntimeException("Failed to load image");

			}
		}
		BufferedImage bufferedImage = SwingFXUtils.fromFXImage(img, null);

		try {
			ImageIO.write(bufferedImage, "png", image);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return;
	}

	public static CullFace getCullFaceValue() {
		return cullFaceValue;
	}

	public static void setCullFaceValue(CullFace cullFaceValue) {
		ThumbnailImageCSG.cullFaceValue = cullFaceValue;
	}

	public static int getImageSize() {
		return ImageSize;
	}

	public static void setImageSize(int imageSize) {
		ImageSize = imageSize;
	}
}
