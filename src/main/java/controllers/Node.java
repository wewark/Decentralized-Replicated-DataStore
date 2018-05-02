package controllers;

import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.LocalPeer;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.module.wlan.WlanModule;
import storage.FileManager;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
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
	private HashMap<UUID, PeerConnection> connections = new HashMap<>();

	// Maps the unique identifier of each peer to its username
	private HashMap<UUID, String> nodesUsernames = new HashMap<>();

	// Maps each username to its corresponding online remote peers
	private HashMap<String, ArrayList<RemotePeer>> remotePeers = new HashMap<>();

	private Node() {
	}

	// Username of the user currently logged in
	private String username;

	public FileManager fileManager;

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

	/**
	 * When a peer is discovered, create a new "peerConnection"
	 * with it, then send it your username.
	 *
	 * @param discoveredPeer
	 */
	private void onPeerDiscovered(RemotePeer discoveredPeer) {
		System.out.println("Discovered peer: " + discoveredPeer);
		UUID peerUUID = discoveredPeer.getUniqueIdentifier();

		if (!connections.containsKey(peerUUID))
			connections.put(peerUUID, new PeerConnection(discoveredPeer));
		connections.get(peerUUID).connect();

		sendMyUsername(discoveredPeer);
	}

	/**
	 * When a peer has logged out, forget its username.
	 *
	 * @param removedPeer
	 */
	private void onPeerRemoved(RemotePeer removedPeer) {
		System.out.println("Removed peer: " + removedPeer);

		removeNodeUsername(removedPeer);

		try {
			updateUserList.call();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * When a new connection received, create or update your "peerConnection"
	 * with its peer.
	 *
	 * @param peer
	 * @param incomingConnection
	 */
	private void onIncomingConnection(RemotePeer peer, Connection incomingConnection) {
		UUID peerUUID = peer.getUniqueIdentifier();
		System.out.println("Received incoming connection: " + incomingConnection + " from peer: " + peerUUID);

		if (!connections.containsKey(peerUUID))
			connections.put(peerUUID, new PeerConnection(peer));
		connections.get(peerUUID).setIncomingConnection(incomingConnection);
	}

	/**
	 * Sends a msg or a small object, files are
	 * sent through outTransfer.
	 *
	 * @param peer The peer to send the data to
	 * @param data The data in the form of byte array
	 */
	private void sendData(RemotePeer peer, byte[] data) {
		PeerConnection connection = connections.get(peer.getUniqueIdentifier());
		connection.sendData(data);
	}

	public void setNodeUsername(RemotePeer peer, byte[] usernameBytes) {
		String nodeUsername = (String) controllers.Helpers.deserialize(usernameBytes);

		if (!remotePeers.containsKey(nodeUsername))
			remotePeers.put(nodeUsername, new ArrayList<>());
		remotePeers.get(nodeUsername).add(peer);

		nodesUsernames.put(peer.getUniqueIdentifier(), nodeUsername);

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
		bytes = controllers.Helpers.concatenate(bytes,
				controllers.Helpers.serialize(username));

		sendData(peer, bytes);
	}


	// A "function" that's is invoked to update GUI
	private Callable<Void> updateUserList;

	public void setUpdateUserList(Callable<Void> updateUserList) {
		this.updateUserList = updateUserList;
	}

	/**
	 * @return Returns array of all online usernames.
	 */
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

	/**
	 * This function is basically a bridge from the file manager to "PeerConnnection",
	 * it gets the "peerConnection" corresponding to this UIID and sends it the rest
	 * of the parameters.
	 *
	 * @param peerUUID    UUID of the peer receiving the file.
	 * @param fileChannel This is like a pointer that reads/writes a file by byte.
	 * @param filename    To be sent before the actual file.
	 * @param fileSize
	 */
	public void sendFile(UUID peerUUID, FileChannel fileChannel, String filename, int fileSize) {
		PeerConnection connection = connections.get(peerUUID);
		connection.sendFile(fileChannel, filename, fileSize);
	}
}
