package controllers;

import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.LocalPeer;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.module.wlan.WlanModule;

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

	private Connection connection;

	// Maps the unique identifier of each peer to its username
	private HashMap<UUID, String> nodeUsername = new HashMap<>();

	// Maps each username to its corresponding online remote peers
	private HashMap<String, ArrayList<RemotePeer>> remotePeers = new HashMap<>();

	private Node() {}

	private String username;

	public void login(String username) {
		this.username = username;
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
	}

	private void onIncomingConnection(RemotePeer peer, Connection incomingConnection) {
		System.out.println("Received incoming connection: " + incomingConnection + " from peer: " + peer.getUniqueIdentifier());
		incomingConnection.setOnData((t, data) -> acceptData(peer, data));
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
				try {
					updateUserList.call();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
		}
	}

	private void setNodeUsername(RemotePeer peer, byte[] usernameBytes) {
		String otherNodeUsername = (String) controllers.Helpers.deserialize(usernameBytes);
		nodeUsername.put(peer.getUniqueIdentifier(), otherNodeUsername);

		if (remotePeers.containsKey(otherNodeUsername))
			remotePeers.get(otherNodeUsername).add(peer);
		else
			remotePeers.put(otherNodeUsername, new ArrayList<>());

		System.out.println("User logged in: " + otherNodeUsername + ", " + peer.getUniqueIdentifier());
	}

	private void sendMyUsername(RemotePeer peer) {
		connection = peer.connect();
		connection.setOnConnect(c -> System.out.println("Connected!"));
		connection.setOnError((c, e) -> c.attemptReconnect());

		byte msgType = 0;
		byte[] bytes = new byte[1];
		bytes[0] = msgType;
		bytes = controllers.Helpers.concatenate(bytes, controllers.Helpers.serialize(username));

		connection.send(ByteBuffer.wrap(bytes));
		connection.setOnClose(c -> System.out.println("Connection closed."));
		//send.setOnComplete(c -> connection.close());
	}

	// A "function" that's is invoked to update GUI
	private Callable<Void> updateUserList;

	public void setUpdateUserList(Callable<Void> updateUserList) {
		this.updateUserList = updateUserList;
	}

	public String[] getUsernames() {
		String[] users = new String[username.length()];
		int i = 0;
		for (String user : remotePeers.keySet())
			users[i++] = user;
		return users;
	}
}
