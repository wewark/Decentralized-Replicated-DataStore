import org.hive2hive.core.file.IFileAgent;

import java.io.File;
import java.io.IOException;

class IFileAgentImpl implements IFileAgent {

	private final File root;

	public IFileAgentImpl(String username) {
		/*
		 * Change "/Data" to another name when running another instance
		 * to simulate logging in from a different machine
		 */
		root = new File(new File(System.getProperty("user.dir") + "/Data"), username);
		root.mkdirs();
	}

	public File getRoot() {
		return root;
	}

	public void writeCache(String key, byte[] data) throws IOException {
		// do nothing as examples don't depend on performance
	}

	public byte[] readCache(String key) throws IOException {
		// do nothing as examples don't depend on performance
		return null;
	}

}