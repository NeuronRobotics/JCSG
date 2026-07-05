package eu.mihosoft.vrl.v3d;

public class Debug3dProvider {
	static IDebug3dProvider provider = null;
	public static void addObject(Object o) {
		if (isProviderAvailible())
			provider.addObject(o);
	}

	public static void clearScreen() {
		if (isProviderAvailible())
			provider.clearScreen();
	}

	public static boolean isProviderAvailible() {
		return provider != null;
	}

	/**
	 * @param provider
	 *            the provider to set
	 */
	public static void setProvider(IDebug3dProvider provider) {
		Debug3dProvider.provider = provider;
	}

}
