package storage;

import java.io.File;
import java.util.ArrayList;

public class FileManager {
	public static String mainDir = System.getProperty("user.dir") + "/Data";
	public String name;
	public ArrayList<String> fileList = new ArrayList<>();

	public FileManager(String name) {
		this.name = name;
	}

	public void scan() {
		fileList.clear();
		File file = new File(mainDir + "/" + name);
		file.mkdir();
		scan(file, "");
	}

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
