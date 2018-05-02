package controllers;

import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.LocalPeer;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.connectivity.InTransfer;
import de.tum.in.www1.jReto.connectivity.OutTransfer;
import de.tum.in.www1.jReto.module.wlan.WlanModule;
import storage.FileManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class Node {
	private static Node ourInstance = new Node();

	public static Node getInstance() {
		return ourInstance;
	}

	private WlanModule wlanModule;
	private LocalPeer localPeer;

	// Maps the unique identifier of each peer to its connection;
	private HashMap<UUID, Connection> connections = new HashMap<>();

	// Maps the unique identifier of each peer to its username
	private HashMap<UUID, String> nodesUsernames = new HashMap<>();

	// Maps each username to its corresponding online remote peers
	private HashMap<String, ArrayList<RemotePeer>> remotePeers = new HashMap<>();

	// Maps each UUID to its peer
	private HashMap<UUID, RemotePeer> remotePeersUUID = new HashMap<>();

	private Node() {
	}

	private String username;

	private FileManager fileManager;

	public void login(String username) {
		this.username = username;
		fileManager = new FileManager(username);

		try {
			wlanModule = new WlanModule("myNet");
		} catch (IOException e) {
			e.printStackTrace();
		}

		localPeer = new LocalPeer(Collections.singletonList(wlanModule), Executors.newSingleThreadExecutor());
		localPeer.start(
				this::onPeerDiscovered,
				this::onPeerRemoved,
				this::onIncomingConnection
		);
	}

	private void onPeerDiscovered(RemotePeer discoveredPeer) {
		System.out.println("Discovered peer: " + discoveredPeer);
		sendMyUsername(discoveredPeer);
	}

	private void onPeerRemoved(RemotePeer removedPeer) {
		System.out.println("Removed peer: " + removedPeer);
		removeNodeUsername(removedPeer);
		try {
			updateUserList.call();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void onIncomingConnection(RemotePeer peer, Connection incomingConnection) {
		System.out.println("Received incoming connection: " + incomingConnection + " from peer: " + peer.getUniqueIdentifier());
		incomingConnection.setOnData((c, data) -> acceptData(peer, data, c));
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
	 * @param peer       The peer that sent the data
	 * @param data       The data sent
	 * @param connection The connection it was sent through
	 */
	private void acceptData(RemotePeer peer, ByteBuffer data, Connection connection) {
		byte[] bytes = data.array();
		byte msgType = bytes[0];
		bytes = Arrays.copyOfRange(bytes, 1, bytes.length);

		switch (msgType) {
			// Login
			case 0:
				setNodeUsername(peer, bytes);
				break;

			// Receive files list
			case 1:
				fileManager.receiveFileList(peer.getUniqueIdentifier(), bytes);
				break;

			// Receive file name
			case 2:
				String filename = new String(bytes);
				connection.setOnTransfer((c, t) -> {
					receiveFile(peer, c, t, filename);
					t.setOnEnd((tt) -> connection.setOnTransfer(null));
				});
		}
	}

	/**
	 * This function sends a msg or a small object, files are
	 * sent through outTransfer
	 *
	 * @param peer The peer to send the data to
	 * @param data The data in the form of byte array
	 */
	private void sendData(RemotePeer peer, byte[] data) {
		Connection connection = connections.get(peer.getUniqueIdentifier());

		if (connection == null) {
			connection = connectTo(peer);
			connections.put(peer.getUniqueIdentifier(), connection);
		}

		connection.send(ByteBuffer.wrap(data));
	}

	// Connects to a peer and returns the connection to be stored
	private Connection connectTo(RemotePeer peer) {
		Connection connection = peer.connect();
		connections.put(peer.getUniqueIdentifier(), connection);
		connection.setOnClose(c -> System.out.println("Connection closed."));
		return connection;
	}

	private void setNodeUsername(RemotePeer peer, byte[] usernameBytes) {
		String nodeUsername = (String) controllers.Helpers.deserialize(usernameBytes);

		if (!remotePeers.containsKey(nodeUsername))
			remotePeers.put(nodeUsername, new ArrayList<>());
		remotePeers.get(nodeUsername).add(peer);

		nodesUsernames.put(peer.getUniqueIdentifier(), nodeUsername);
		remotePeersUUID.put(peer.getUniqueIdentifier(), peer);

		try {
			updateUserList.call();
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("User logged in: " + nodeUsername + ", " + peer.getUniqueIdentifier());
	}

	private void removeNodeUsername(RemotePeer peer) {
		String nodeUsername = nodesUsernames.get(peer.getUniqueIdentifier());
		remotePeers.get(nodeUsername).remove(peer);

		if (remotePeers.get(nodeUsername).isEmpty())
			remotePeers.remove(nodeUsername);

		nodesUsernames.remove(peer.getUniqueIdentifier());
	}

	private void sendMyUsername(RemotePeer peer) {
		byte[] bytes = {0};
		bytes = controllers.Helpers.concatenate(bytes, controllers.Helpers.serialize(username));

		sendData(peer, bytes);
	}


	// A "function" that's is invoked to update GUI
	private Callable<Void> updateUserList;

	public void setUpdateUserList(Callable<Void> updateUserList) {
		this.updateUserList = updateUserList;
	}

	public String[] getUsernames() {
		String[] users = new String[remotePeers.size()];
		int i = 0;
		for (String user : remotePeers.keySet())
			users[i++] = user;
		return users;
	}

	public void scanDirectory() {
		fileManager.sync();
	}

	/**
	 * Broadcasts a msg to all peers with this username
	 *
	 * @param username to send the data to
	 * @param msgType
	 * @param data
	 */
	public void broadcastToUser(String username, byte msgType, byte[] data) {
		if (!remotePeers.containsKey(username)) return;
		data = Helpers.concatenate(new byte[]{msgType}, data);
		for (RemotePeer peer : remotePeers.get(username)) {
			sendData(peer, data);
		}
	}

	public void sendFile(UUID peerUUID, FileChannel fileChannel, String filename, int fileSize) {
		Connection connection = connections.get(peerUUID);
		RemotePeer peer = remotePeersUUID.get(peerUUID);

		byte[] data = {2};
		data = Helpers.concatenate(data, filename.getBytes());
		sendData(peer, data);

		OutTransfer transfer = connection.send(fileSize,
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

	private void receiveFile(RemotePeer peer, Connection connection, InTransfer inTransfer, String filename) {
		FileChannel fileChannel = null;
		try {
			Path path = Paths.get(FileManager.mainDir, filename);
			OpenOption[] read = {StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW};
			fileChannel = FileChannel.open(path, read);
		} catch (IOException e) {
			e.printStackTrace();
		}

		FileChannel finalFileChannel = fileChannel;
		inTransfer.setOnPartialData((t, data) -> Helpers.writeData(finalFileChannel, data));
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
}
