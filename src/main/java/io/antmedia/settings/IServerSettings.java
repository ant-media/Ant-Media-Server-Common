package io.antmedia.settings;

public interface IServerSettings {
	public static final String BEAN_NAME = "ant.media.server.settings";

	/**
	 * Server default HTTP port
	 * It's 5080 by default
	 * @return default HTTP port
	 */
	public int getDefaultHttpPort();
}
