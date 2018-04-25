import org.apache.commons.io.FileUtils;
import org.hive2hive.core.api.H2HNode;
import org.hive2hive.core.api.configs.FileConfiguration;
import org.hive2hive.core.api.configs.NetworkConfiguration;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.api.interfaces.IFileManager;
import org.hive2hive.core.api.interfaces.IH2HNode;
import org.hive2hive.core.processes.files.list.FileNode;
import org.hive2hive.core.security.UserCredentials;

import java.io.File;
import java.net.InetAddress;
import java.util.Scanner;

public class Node {
	public static void main(String[] args) throws Exception {
		IFileConfiguration fileConfiguration = FileConfiguration.createDefault();

		// Create two nodes and open a new overlay network
		IH2HNode node = H2HNode.createNode(fileConfiguration);
		//node.connect(NetworkConfiguration.createInitial());
		node.connect(NetworkConfiguration.create(InetAddress.getLocalHost()));

		Scanner sc = new Scanner(System.in);
		System.out.print("Username: ");
		String username = sc.nextLine();

		// These two file agents are used to configure the root directory of the logged in users
		IFileAgentImpl fileAgent = new IFileAgentImpl(username);

		// Register and login user 'Alice' at node 1
		UserCredentials user = new UserCredentials(username, "password", "pin");
		if (!node.getUserManager().isRegistered(username)) {
			System.out.println("Registering...");
			node.getUserManager().createRegisterProcess(user).execute();
		}
		System.out.println("Logging in...");
		node.getUserManager().createLoginProcess(user, fileAgent).execute();

		IFileManager fileManager = node.getFileManager(); // The file management of Alice's peer
		FileNode fileNode = fileManager.createFileListProcess().execute();
		printRecursively(fileNode, 0);
		System.out.println("Syncing...");
		sync(fileNode, fileManager);

		node.getFileManager().subscribeFileEvents(new IFileEventListenerImpl(node.getFileManager()));


		// Share a folder
//		System.out.print("Share with: ");
//		String otherUser = sc.nextLine();
//		File sharedFolder = new File(fileAgent.getRoot(), username + "s_shared_folder");
//		sharedFolder.mkdirs();
//		fileManager.createAddProcess(sharedFolder).execute();
//		fileManager.createShareProcess(sharedFolder, otherUser, PermissionType.WRITE).execute();

		// Add new file
		File file = new File(fileAgent.getRoot(), username + "s_file.txt");
		FileUtils.write(file, "file content lorem ipsum w kda");
		fileManager.createAddProcess(file).execute();
	}

	/**
	 * Recursively prints all files and folders in the current user's directory
	 * @param node root directory
	 * @param level
	 */
	private static void printRecursively(FileNode node, int level) {
		if (node.getParent() != null) {
			// skip the root node
			StringBuilder spaces = new StringBuilder("*");
			for (int i = 0; i < level; i++) {
				spaces.append(" ");
			}
			System.out.println(spaces.toString() + node.getName());
		}

		if (node.isFolder()) {
			for (FileNode child : node.getChildren()) {
				printRecursively(child, level + 1);
			}
		}
	}

	/**
	 * Recursively synchronizes files when user logs in on a different machine
	 * @param node root directory
	 * @param fileManager
	 * @throws Exception
	 */
	public static void sync(FileNode node, IFileManager fileManager) throws Exception {
		if (node.getParent() != null) {
			System.out.println("Downloading " + node.getName() + "...");
			fileManager.createDownloadProcess(node.getFile()).execute();
		}

		if (node.isFolder()) {
			for (FileNode child : node.getChildren()) {
				sync(child, fileManager);
			}
		}
	}
}
