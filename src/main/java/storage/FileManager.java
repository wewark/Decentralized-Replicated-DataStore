package storage;

import controllers.Helpers;
import controllers.Node;
import de.tum.in.www1.jReto.RemotePeer;
import io.methvin.watcher.DirectoryWatcher;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The main file structure is
 * "[project_directory]/Data/[username]/[everything]"
 * "Data" is just for separating the data from the program source.
 * <p>
 * UUID is a unique identifier for a peer,
 * it looks like "sfs123-fs64f-sdfsdf-asdf",
 * this is in order to keep the file manager
 * away from the connection logic.
 */
public class FileManager {
	private static FileManager instance;

	// The root directory
	private static String root = System.getProperty("user.dir") + "/Data";

	// The user directory
	private String userDir;

	// Username of the current user, used to username the root directory
	private String username;

	/**
	 * Stores the paths of all the current existing files.
	 * It looks like: {
	 * "folder1/foldergwah/filekda.txt",
	 * "file_fel_root.mp3"
	 * }
	 */
	private Collection<String> fileList;

	/**
	 * Holds all files received via the current session (to avoid resending them again by the watcher)
	 */
	private Collection<String> receivedFiles;

	// The file lists of the other computers I am logged in
	private Map<UUID, Collection<String>> peerFileLists;

	private Node node;

	private Thread watcherThread;

	private FileManager() {
		this.fileList = new HashSet<>();
		this.peerFileLists = new HashMap<>();
		this.receivedFiles = new HashSet<>();
		this.node = Node.getInstance();
		this.username = node.getUsername();

		//set new dir.
		this.userDir = root + "/" + username;

		//create directories if doesn't exist yet (if 1st run)
		File directory = new File(userDir);
		if (!directory.exists())
			directory.mkdirs();

		//Start thread.
		watcherThread = new Thread(() -> {
			try {
				watchDirectoryPath(userDir);
			}
			catch(IOException e) {

			}
		});
		watcherThread.start();

		//populate fileLists.
		checkout();
	}

	public synchronized static FileManager getInstance() {
		if (instance == null)
			return instance = new FileManager();
		return instance;
	}

	/**
	 * Dev Utility to develop instances on the same PC.
	 */
	public static void setRootDir() {
		//DEV CODE
		Scanner sc = new Scanner(System.in);
		String directory = sc.nextLine();
		root = System.getProperty("user.dir") + "/" + directory;
	}

	/**
	 * Serializes fileList and broadcasts it to all
	 * peers with your username.
	 */
	private void sendFileList() {
		byte[] bytes = Helpers.serialize(fileList);
		node.broadcastToUser(username, (byte) 1, bytes);
	}

	/**
	 * Receive the file list of a peer and compare it with mine.
	 * TBH I can't remember why I store it in "peerFileLists" first
	 * instead of just sending it to the "compareWithPeer()" directly.
	 *
	 * @param peerUUID UUID of the peer sending the file list
	 * @param data     The serialized file list
	 */
	public void receiveFileList(UUID peerUUID, byte[] data) {
		Collection<String> fileList = (Collection<String>) Helpers.deserialize(data);
		peerFileLists.put(peerUUID, fileList);
		try {
			sendFiles(peerUUID, compareWithPeer(peerUUID));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Compare my file list with the file list of another peer,
	 * get the files that I have and he does NOT and send them to him.
	 * PS: I don't think it waits for the file to be sent before sending another,
	 * because jReto sending function is a runnable and we just loop here,
	 * what a big TODO.
	 *
	 * @param peerUUID UUID of the other peer.
	 */
	private List<String> compareWithPeer(UUID peerUUID) {
		// difference contains all the files that I have but this peer doesn't have
		return fileList.stream().filter(x -> !peerFileLists.get(peerUUID).contains(x)).collect(Collectors.toList());
	}

	/**
	 * Just sends a file to this peer.
	 *
	 * @param peerUUID UUID of the other peer.
	 * @param filePath Path of the file to be sent, relative to the root directory,
	 *                 notice it gets appended to "userDir".
	 */
	private void sendFile(UUID peerUUID, String filePath) {
		try {
			Path path = Paths.get(userDir, filePath);
			FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
			node.sendFile(peerUUID, fileChannel, filePath, (int) fileChannel.size());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendFiles(UUID peerUUID, List<String> filePaths) {
		for (String filePath : filePaths)
			sendFile(peerUUID, filePath);
	}

	// Send filePath to all online peers.
	private Boolean broadcastFile(String filePath) {

		List<RemotePeer> peerList = node.getRemotePeers().get(username);

		if(peerList == null)
			return false;

		for (RemotePeer remotePeer : peerList) {
			sendFile(remotePeer.getUniqueIdentifier(), filePath);
		}

		return true;
	}

	// Send all file-paths to all online peers.
	private void broadcastFiles(List<String> filePaths) {
		List<RemotePeer> peerList = node.getRemotePeers().get(username);
		for (RemotePeer remotePeer : peerList) {
			sendFiles(remotePeer.getUniqueIdentifier(), filePaths);
		}
	}

	/**
	 * Broadcast your fileList to all peers, by-which they're going to send you their last committed files.
	 */
	public synchronized void sync() {
		sendFileList();
		System.out.println("Synchronization Successful!");
	}

	/**
	 * Non-recursive function, scans the root directory for new files,
	 * it currently feels the existence of files only, not the changes.
	 */
	public synchronized void checkout() {
		File file = new File(userDir);

		// Create the root directory if it doesn't exist
		if (!file.exists())
			file.mkdirs();

		Collection<String> directory = new ArrayList<>();

		scan(file, "", directory);

		//Print new Files
		Collection<String> newFiles    = directory.stream().filter(x -> !fileList.contains(x)).collect(Collectors.toList());
		Collection<String> deleteFiles = fileList.stream().filter(x -> !directory.contains(x)).collect(Collectors.toList());

		for (String newFilePath : newFiles) {
			addToFileList(newFilePath);
			System.out.println("Checked in file: " + newFilePath);
		}

		for (String deleteFilePath : deleteFiles) {
			removeFromFileList(deleteFilePath);
			System.out.println("Checked out file: " + deleteFilePath);
		}

		System.out.println("All files are checked-out and updated! Total: " + fileList.size() + " files.");
	}

	/**
	 * Scans directory tree starting at "file" and
	 * stores the relative path (relative to the root folder)
	 * of each file in "fileList".
	 *
	 * @param file     Starts recurring from that file
	 * @param rootPath The path relative to the root directory,
	 *                 it starts as "/" and gets manually built.
	 */
	private void scan(File file, String rootPath, Collection<String> newPaths) {
		File[] files = file.listFiles();

		for (File curFile : files) {

			String fileRelativePath = rootPath + "/" + curFile.getName();

			//If file is a Directory/Folder, recurse and explore it.
			if (curFile.isDirectory())
				scan(curFile, fileRelativePath, newPaths);

			//If file is a... file.
			else {
				//Add new found file
				newPaths.add(fileRelativePath);
			}
		}
	}

	//Called when Directory watcher detects a new created file.
	private void watcherCreatedFile(Path path) {
		Path rootPath = Paths.get(userDir);                              // Replace to standardize URI(s) to UNIX uris.
		String relativePath = '/' + rootPath.relativize(path).toString().replace('\\', '/');
		File newFile = new File(userDir, relativePath);
		if (!newFile.isDirectory()) {
			//Save to files table.
			addToFileList(relativePath);

			//Check if file is not created by an another peer push ( so that no-need to broadcast it back)
			if (!receivedFiles.contains(relativePath)) {
				//if not received it means it's a new file! -> print and broadcast.
				System.out.println("File Created! Added: " + relativePath);

				System.out.println("Broadcasting File: '" + newFile.getName() + "' to all online peers...");

				if(broadcastFile(relativePath))
					System.out.println("File broadcast-ed to all peers!");
				else
					System.out.println("Broadcasting failed, no peers online.");

			} else
				System.out.println("File Received! Added: " + relativePath);
		}
	}

	//TODO - Called when Directory watcher detects modify change.
	private void watcherModifiedFile() {
	}

	private void watcherDeletedFile(Path path) {
		Path rootPath = Paths.get(userDir);                              // Replace to standardize URI(s) to UNIX uris.
		String relativePath = '/' + rootPath.relativize(path).toString().replace('\\', '/');
		File newFile = new File(userDir, relativePath);
		if (!newFile.isDirectory()) {
			//Save to files table.
			removeFromFileList(relativePath);
			System.out.println("Removed file: " + relativePath);
			//TODO send a remove request to all other peers.
		}
	}

	private void watchDirectoryPath(String pathString) throws IOException {
		Path pathToWatch = Paths.get(pathString);

		DirectoryWatcher watcher = DirectoryWatcher.create(pathToWatch, event -> {
			switch (event.eventType()) {
				case CREATE:
					watcherCreatedFile(event.path());
					break;
				case MODIFY:
					watcherModifiedFile();
					break;
				case DELETE:
					watcherDeletedFile(event.path());
					break;
			}
		});

		//start watching ((blocking))
		watcher.watch();
	}

	@Override
	protected void finalize() throws Throwable {
		if (watcherThread.isAlive())
			watcherThread.interrupt();
	}

	public synchronized void addToFileList(String path) {
		fileList.add(path);
	}

	public synchronized void removeFromFileList(String path) {
		fileList.remove(path);
	}

	//SETTERS & GETTERS

	public String getUserDir() {
		return userDir;
	}

	public void setUserDir(String userDir) {
		this.userDir = userDir;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Collection<String> getReceivedFiles() {
		return receivedFiles;
	}

	public Collection<String> getFileList() {
		return fileList;
	}
}
