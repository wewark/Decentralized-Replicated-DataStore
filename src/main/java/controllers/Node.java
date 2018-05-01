package controllers;

import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.LocalPeer;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.module.wlan.WlanModule;
import storage.FileManager;

import java.io.IOException;
import java.nio.ByteBuffer;
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
		incomingConnection.setOnData((c, data) -> acceptData(peer, data));
	}


	/**
	 * The data will mainly be an array of bytes.
	 * The first byte is the type of the data sent,
	 * currently 0 means login, i.e "I am sending you my username"
	 * the rest of the array is the data itself, serialized into bytes
	 *
	 * @param peer
	 * @param data
	 */
	private void acceptData(RemotePeer peer, ByteBuffer data) {
		byte[] bytes = data.array();
		byte msgType = bytes[0];
		bytes = Arrays.copyOfRange(bytes, 1, bytes.length);

		switch (msgType) {
			case 0:
				setNodeUsername(peer, bytes);
				break;
		}
	}

	public void sendData(RemotePeer peer, byte[] data) {
		Connection connection = connections.get(peer.getUniqueIdentifier());

		if (connection == null) {
			connection = connectTo(peer);
			connections.put(peer.getUniqueIdentifier(), connection);
		}

		connection.send(ByteBuffer.wrap(data));
	}

	private Connection connectTo(RemotePeer peer) {
		Connection connection = peer.connect();
		connections.put(peer.getUniqueIdentifier(), connection);
		connection.setOnClose(c -> System.out.println("Connection closed."));
		return connection;
	}

	private void setNodeUsername(RemotePeer peer, byte[] usernameBytes) {
		String nodeUsername = (String) controllers.Helpers.deserialize(usernameBytes);
		nodesUsernames.put(peer.getUniqueIdentifier(), nodeUsername);

		if (!remotePeers.containsKey(nodeUsername))
			remotePeers.put(nodeUsername, new ArrayList<>());
		remotePeers.get(nodeUsername).add(peer);

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
		byte msgType = 0;
		byte[] bytes = new byte[1];
		bytes[0] = msgType;
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
		fileManager.scan();
	}
}
