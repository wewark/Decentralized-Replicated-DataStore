package storage;

import controllers.Helpers;
import controllers.Node;

import java.io.File;
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

	//TODO MAKE SINGLETON

	// The root directory
	public static String root = System.getProperty("user.dir") + "/Data";

	public static String mainDir;

	// Username of the current user, used to username the root directory
	public String username;

	/**
	 * Stores the paths of all the current existing files.
	 * It looks like: {
	 * "folder1/foldergwah/filekda.txt",
	 * "file_fel_root.mp3"
	 * }
	 */
	public List<String> fileList;

	// The file lists of the other computers I am logged in
	public Map<UUID, ArrayList<String>> peerFileLists;

	private Node node;

	public FileManager(String username) {
		this.username = username;
		this.fileList = new ArrayList<>();
		this.peerFileLists = new HashMap<>();
		this.node = Node.getInstance();

		//set new dir.
		mainDir = root + "/" + username;

		//create directories if doesn't exist yet (if 1st run)
		File directory = new File(mainDir);
		if(!directory.exists())
			directory.mkdirs();

		//populate fileLists.
		scan();
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
		ArrayList<String> fileList = (ArrayList<String>) Helpers.deserialize(data);
		peerFileLists.put(peerUUID, fileList);
		try {
			//TODO decompose
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
	 *                 notice it gets appended to "mainDir".
	 */
	private void sendFile(UUID peerUUID, String filePath) {
		try {
			Path path = Paths.get(mainDir, filePath);
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

	/**
	 * When sync button is pressed.
	 */
	public void sync() {
		scan();
		sendFileList();
	}

	/**
	 * Non-recursive function, scans the root directory for new files,
	 * it currently feels the existence of files only, not the changes.
	 */
	private void scan() {
		fileList.clear();
		File file = new File(mainDir);

		// Create the root directory if it doesn't exist
		file.mkdir();

		scan(file, "");
	}

	/**
	 * Scans directory tree starting at "file" and
	 * stores the relative path (relative to the root folder)
	 * of each file in "fileList".
	 *
	 * @param file Starts recurring from that file
	 * @param path The path relative to the root directory,
	 *             it starts as "/" and gets manually built.
	 */
	private void scan(File file, String path) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File curFile : files) {
				scan(curFile, path);
			}
		} else {
			System.out.println("File: " + path + "/" + file.getName());
			fileList.add(path + "/" + file.getName());
		}
	}

	/*
		Dev Utility to develop instances on the same PC.
	*/
	public static void setRootDir(){
		//DEV CODE
		Scanner sc = new Scanner(System.in);
		String directory = sc.nextLine();
		root = System.getProperty("user.dir") + "/" + directory;
	}
}
