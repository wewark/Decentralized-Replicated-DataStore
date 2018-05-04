package controllers;

import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.connectivity.InTransfer;
import de.tum.in.www1.jReto.connectivity.OutTransfer;
import storage.FileManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Maintains a two-way connection between the local peer and a remote one.
 */
public class PeerConnection {
	private Node node = Node.getInstance();
	private FileManager fileManager = node.fileManager;

	private RemotePeer remotePeer;
	private Connection incomingConnection;
	private Connection outGoingConnection;


	public PeerConnection(RemotePeer remotePeer) {
		this.remotePeer = remotePeer;
	}

	public void connect() {
		outGoingConnection = remotePeer.connect();
		outGoingConnection.setOnClose(c -> System.out.println("Connection closed."));
	}

	/**
	 * The data will mainly be an array of bytes.
	 * The first byte is the type of the data sent,
	 * currently 0 means login, i.e "I am sending you my username"
	 * the rest of the array is the data itself, serialized into bytes
	 * <p>
	 * msg types: TODO: Convert to enum
	 * 1 -> Login
	 * 2 -> Receive files list
	 * 3 -> Receive file name
	 *
	 * @param data Received data.
	 */
	private void acceptData(ByteBuffer data) {
		byte[] bytes = data.array();
		byte msgType = bytes[0];
		bytes = Arrays.copyOfRange(bytes, 1, bytes.length);

		switch (msgType) {
			// Login
			case 0:
				node.setNodeUsername(remotePeer, bytes);
				break;

			// Receive files list
			case 1:
				fileManager.receiveFileList(remotePeer.getUniqueIdentifier(), bytes);
				break;

			// Receive file name
			case 2:
				String filename = new String(bytes);
				incomingConnection.setOnTransfer((c, t) -> {
					receiveFile(t, filename);
					t.setOnEnd((tt) -> incomingConnection.setOnTransfer(null));
				});
		}
	}

	/**
	 * This function sends a msg or a small object, files are
	 * sent through outTransfer
	 *
	 * @param data The data in the form of byte array
	 */
	public void sendData(byte[] data) {
		outGoingConnection.send(ByteBuffer.wrap(data));
	}

	/**
	 * First sends the name of the file, then starts transferring
	 * the file itself.
	 *
	 * @param fileChannel This is like a pointer that reads/writes
	 *                    a file byte by byte.
	 * @param filename    The path of the file not just its name,
	 *                    relative to the root directory.
	 * @param fileSize
	 */
	public void sendFile(FileChannel fileChannel, String filename, int fileSize) {
		// Send array [2, *filename in bytes* ]
		// 2 is the msg type
		byte[] data = {2};
		data = Helpers.concatenate(data, filename.getBytes());
		sendData(data);

		// This "send()" function takes the file size
		// and a data source to read from, it allows it
		// to read and send the file in chunks.
		OutTransfer transfer = outGoingConnection.send(fileSize,
				(position, length) -> Helpers.readData(fileChannel, position, length));

		// Loading bar
		transfer.setOnProgress(
				t -> System.out.println("Progress: " + t.getProgress() + ", " + t.getLength()));

		// When transfer ends, close the file channel
		transfer.setOnEnd(t -> {
			try {
				fileChannel.force(true);
				fileChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * This function is invoked when a file transfer is started,
	 * it creates a file channel that creates a new file with that
	 * filename and starts writing to it as it receives chunks of data.
	 *
	 * @param inTransfer
	 * @param filename   The path of the file not just its name,
	 *                   relative to the root directory.
	 */
	private void receiveFile(InTransfer inTransfer, String filename) {
		FileChannel fileChannel = null;
		try {
			Path path = Paths.get(FileManager.mainDir, filename);

			//Create Directories if doesn't exist.
			path.getParent().toFile().mkdirs();

			OpenOption[] read = {
					StandardOpenOption.WRITE,
					StandardOpenOption.CREATE_NEW
			};
			fileChannel = FileChannel.open(path, read);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// CLion just wanted me to create this lol
		// because of "final" bla bla
		FileChannel finalFileChannel = fileChannel;

		// When a chunk of data is received, write it.
		inTransfer.setOnPartialData(
				(t, data) -> Helpers.writeData(finalFileChannel, data));

		// Loading bar
		inTransfer.setOnProgress(
				t -> System.out.println("Progress: " + t.getProgress() + ", " + t.getLength()));

		// When transfer ends, close the file channel
		inTransfer.setOnEnd(t -> {
			try {
				finalFileChannel.force(true);
				finalFileChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	public void setIncomingConnection(Connection incomingConnection) {
		this.incomingConnection = incomingConnection;
		incomingConnection.setOnData((c, data) -> acceptData(data));
	}
}
