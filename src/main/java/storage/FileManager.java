package storage;

import controllers.Helpers;
import controllers.Node;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class FileManager {
	public static String mainDir = System.getProperty("user.dir") + "/Data";
	public String name;

	public ArrayList<String> fileList = new ArrayList<>();

	// The file lists of the other computers I am logged in
	public HashMap<UUID, ArrayList<String>> peerFileLists = new HashMap<>();

	private Node node = Node.getInstance();

	public FileManager(String name) {
		this.name = name;
		mainDir += "/" + name;
	}

	public void sendFileList() {
		byte[] bytes = Helpers.serialize(fileList);
		node.broadcastToUser(name, (byte) 1, bytes);
	}

	public void receiveFileList(UUID peerUUID, byte[] data) {
		ArrayList<String> fileList = (ArrayList<String>) Helpers.deserialize(data);
		peerFileLists.put(peerUUID, fileList);

		try {
			compareWithPeer(peerUUID);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void compareWithPeer(UUID peerUUID) throws Exception {
		// difference contains all the files that I have but this peer doesn't have
		ArrayList<String> difference = (ArrayList<String>) fileList.clone();
		difference.removeAll(peerFileLists.get(peerUUID));

		for (String file : difference) {
			Path path = Paths.get(mainDir, file);
			FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
			node.sendFile(peerUUID, fileChannel, file, (int) fileChannel.size());
		}
	}

	public void scan() {
		fileList.clear();
		File file = new File(mainDir);
		file.mkdir();
		scan(file, "");
		sendFileList();
	}

	// Recursive
	public void scan(File file, String path) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File curFile : files) {
				scan(curFile, path);
			}
		}
		else {
			System.out.println("File: " + path + "/" + file.getName());
			fileList.add(path + "/" + file.getName());
		}
	}
}
