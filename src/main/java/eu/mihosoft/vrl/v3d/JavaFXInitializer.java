package eu.mihosoft.vrl.v3d;

public class JavaFXInitializer {
	private static boolean latch = false;
	public static boolean errored = false;
	public JavaFXInitializer() {

	}
	private static void gointernal() {
		if (latch) {
			// System.out.println("ERR initializer already started");
			return;
		}
		System.out.println("Starting JavaFX initializer..." + JavaFXInitializer.class);

		try {
			final javafx.embed.swing.JFXPanel fxPanel = new javafx.embed.swing.JFXPanel();
		} catch (Throwable e) {
			errored = true;
			e.printStackTrace();
		}
		latch = true;
	}
	public static void go() {
		if (latch) {
			// System.out.println("ERR initializer already started");
			return;
		}
		new Thread() {
			public void run() {
				try {
					gointernal();
				} catch (Throwable t) {
					t.printStackTrace();
					errored = true;
				}
			}
		}.start();
		try {
			long start = System.currentTimeMillis();
			while ((System.currentTimeMillis() - start) < 1000 && latch == false) {
				Thread.sleep(16);
			}
			if (!latch)
				errored = true;
		} catch (Throwable e) {
			e.printStackTrace();
			errored = true;
		}
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		StackTraceElement e = stacktrace[2];// maybe this number needs to be corrected
		System.out.println((errored ? "ERRORED" : "Success") + " JavaFX initializing! " + e);
	}

}
