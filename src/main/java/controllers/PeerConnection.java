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
 * Maintains a two-way connection between the local peer and a remote one
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
	 * @param data       The data sent
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

	public void sendFile(FileChannel fileChannel, String filename, int fileSize) {
		byte[] data = {2};
		data = Helpers.concatenate(data, filename.getBytes());
		sendData(data);

		OutTransfer transfer = outGoingConnection.send(fileSize,
				(position, length) -> Helpers.readData(fileChannel, position, length));

		transfer.setOnProgress(
				t -> System.out.println("Progress: " + t.getProgress() + ", " + t.getLength()));

		transfer.setOnEnd(t -> {
			try {
				fileChannel.force(true);
				fileChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void receiveFile(InTransfer inTransfer, String filename) {
		FileChannel fileChannel = null;
		try {
			Path path = Paths.get(FileManager.mainDir, filename);
			OpenOption[] read = {
					StandardOpenOption.WRITE,
					StandardOpenOption.CREATE_NEW
			};
			fileChannel = FileChannel.open(path, read);
		} catch (IOException e) {
			e.printStackTrace();
		}

		FileChannel finalFileChannel = fileChannel;
		inTransfer.setOnPartialData(
				(t, data) -> Helpers.writeData(finalFileChannel, data));

		inTransfer.setOnProgress(
				t -> System.out.println("Progress: " + t.getProgress() + ", " + t.getLength()));

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
