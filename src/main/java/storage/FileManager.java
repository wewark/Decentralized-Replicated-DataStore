package storage;

import com.sun.nio.file.ExtendedWatchEventModifier;
import controllers.Helpers;
import controllers.Node;
import de.tum.in.www1.jReto.RemotePeer;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
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
	 * Holds all files received via the current session (to avoid resending them again by the watcher) */
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
		if(!directory.exists())
			directory.mkdirs();

		//Start thread.
		watcherThread = new Thread(() -> watchDirectoryPath(userDir));
		watcherThread.start();

		//populate fileLists.
		checkout();
	}

	public synchronized static FileManager getInstance() {
		if(instance == null)
			return instance = new FileManager();
		return instance;
	}

	/**
	 * Dev Utility to develop instances on the same PC.
	*/
	public static void setRootDir(){
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

	private void sendFiles(UUID peerUUID, List<String> filePaths){
		for (String filePath : filePaths)
			sendFile(peerUUID, filePath);
	}

	// Send filePath to all online peers.
	private void broadcastFile(String filePath){
		List<RemotePeer> peerList = node.getRemotePeers().get(username);
		for (RemotePeer remotePeer : peerList) {
			sendFile(remotePeer.getUniqueIdentifier(), filePath);
		}
	}

	// Send all file-paths to all online peers.
	private void broadcastFiles(List<String> filePaths){
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
		if(!file.exists())
			file.mkdirs();

		Collection<String> newPaths = new ArrayList<>();

		scan(file, "", newPaths);

		//Print new Files
		for (String newFilePath : newPaths) {
			addToFileList(newFilePath);
			System.out.println("Checked file: " + newFilePath);
		}

		System.out.println("All files are checked-out and updated! Total: " + fileList.size() + " files.");
	}

	/**
	 * Scans directory tree starting at "file" and
	 * stores the relative path (relative to the root folder)
	 * of each file in "fileList".
	 *
	 * @param file Starts recurring from that file
	 * @param rootPath The path relative to the root directory,
	 *             it starts as "/" and gets manually built.
	 */
	private void scan(File file, String rootPath, Collection<String> newPaths) {
		File[] files = file.listFiles();

		for (File curFile : files) {

			String fileRelativePath = rootPath + "/" + curFile.getName();

			//If file is a Directory/Folder, recurse and explore it.
			if (curFile.isDirectory())
				scan(curFile, fileRelativePath, newPaths);

			//If file is a... file. check if it new/old.
			else {
				if (fileList.contains(fileRelativePath))
					return;

				//Add new found file
				newPaths.add(fileRelativePath);
			}
		}
	}

	//Called when Directory watcher detects a new created file.
	private void watcherCreatedFile(String relativePath){
		File newFile = new File(userDir,relativePath);
		if(!newFile.isDirectory()) {
			//Check if file is not created by an another peer push ( so that no-need to broadcast it back)
			if(!receivedFiles.contains(relativePath)) {
				//if not received it means it's a new file! -> print and broadcast.
				System.out.println("File Created! Added: " + relativePath);

				System.out.println("Broadcasting File: '" + newFile.getName() + "' to all online peers...");

				broadcastFile(relativePath);

				System.out.println("File broadcast-ed to all peers!");
			}
			else
			{
				System.out.println("File Received! Added: " + relativePath);
				addToFileList(relativePath);
			}
		}
	};

	//TODO - Called when Directory watcher detects modify change.
	private void watcherModifiedFile(){};

	//TODO - Called when Directory watcher detects a file deletion
	private void watcherDeletedFile(){};


	private void watchDirectoryPath(String pathString) {
		Path pathToWatch = Paths.get(pathString);
		try {
			WatchService watchService = pathToWatch.getFileSystem().newWatchService();

			WatchEvent.Kind[] standardEventsArray = {
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY,
					StandardWatchEventKinds.ENTRY_DELETE};

			pathToWatch.register(watchService, standardEventsArray, ExtendedWatchEventModifier.FILE_TREE);

			// loop forever to watch directory
			while (true) {
				WatchKey watchKey;
				// This call is blocking until events are present
				watchKey = watchService.take();

				// Poll for file system events on the WatchKey
				for (final WatchEvent<?> event : watchKey.pollEvents()) {
					String path = '/' + event.context().toString().replace('\\', '/');
					if(event.kind() == StandardWatchEventKinds.ENTRY_CREATE)
						watcherCreatedFile(path); // .context().toString() == relative path.
					else if(event.kind() == StandardWatchEventKinds.ENTRY_MODIFY)
						watcherModifiedFile();
					else if(event.kind() == StandardWatchEventKinds.ENTRY_DELETE)
						watcherDeletedFile();
					//TODO IMPLEMENT MODIFIED/DELETED
				}

				if(!watchKey.reset()) {
					System.out.println("Path deleted");
					watchKey.cancel();
					watchService.close();
					break;
				}
			}

		} catch (InterruptedException ex) {
			//El mafrod only happens at finalize AKA destructor.
			System.out.println("Directory Watcher Thread interrupted, Closing now...");
			return;
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		if(watcherThread.isAlive())
			watcherThread.interrupt();
	}

	public synchronized void addToFileList(String path){
		fileList.add(path);
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
