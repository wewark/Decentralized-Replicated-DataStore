import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.LocalPeer;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.connectivity.OutTransfer;
import de.tum.in.www1.jReto.module.wlan.WlanModule;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;

public class Node {
	private static Node ourInstance = new Node();

	public static Node getInstance() {
		return ourInstance;
	}

	private WlanModule wlanModule;
	private LocalPeer localPeer;

	// Maps the unique identifier of each peer to its username
	private HashMap<UUID, String> nodeUsername = new HashMap<>();

	// Maps each username to its corresponding online remote peers
	private HashMap<String, ArrayList<RemotePeer>> remotePeers = new HashMap<>();

	private Node() {
	}

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
				this::discoverPeer,
				removedPeer -> {
					System.out.println("Removed peer: " + removedPeer);
				},
				this::incomingConnectionHandler
		);
	}

	private void discoverPeer(RemotePeer discoveredPeer) {
		System.out.println("Discovered peer: " + discoveredPeer);
		sendMyUsername(discoveredPeer);
	}

	private void incomingConnectionHandler(RemotePeer peer, Connection incomingConnection) {
		System.out.println("Received incoming connection: " + incomingConnection + " from peer: " + peer.getUniqueIdentifier());

		if (!nodeUsername.containsKey(peer.getUniqueIdentifier()))
			incomingConnection.setOnData((t, usernameBytes) -> setNodeUsername(peer, usernameBytes));
	}

	private void setNodeUsername(RemotePeer peer, ByteBuffer usernameBytes) {
		String otherNodeUsername = new String(usernameBytes.array());
		nodeUsername.put(peer.getUniqueIdentifier(), otherNodeUsername);

		if (remotePeers.containsKey(otherNodeUsername))
			remotePeers.get(otherNodeUsername).add(peer);
		else
			remotePeers.put(otherNodeUsername, new ArrayList<>());

		System.out.println("User logged in: " + otherNodeUsername + ", " + peer.getUniqueIdentifier());
	}

	private void sendMyUsername(RemotePeer peer) {
		Connection connection = peer.connect();
		OutTransfer send = connection.send(ByteBuffer.wrap(username.getBytes()));
		send.setOnComplete((t) -> connection.close());
	}
}
