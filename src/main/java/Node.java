import de.tum.in.www1.jReto.Connection;
import de.tum.in.www1.jReto.LocalPeer;
import de.tum.in.www1.jReto.RemotePeer;
import de.tum.in.www1.jReto.module.wlan.WlanModule;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.Executors;

public class Node {
	public static void main(String[] args) throws IOException {
		WlanModule wlanModule = new WlanModule("myNet");
		LocalPeer localPeer = new LocalPeer(Collections.singletonList(wlanModule), Executors.newSingleThreadExecutor());

		HashSet<RemotePeer> remotePeers = new HashSet<>();

		localPeer.start(
				discoveredPeer -> {
					remotePeers.add(discoveredPeer);
					System.out.println("Discovered peer: " + discoveredPeer);
				},
				removedPeer -> {
					remotePeers.remove(removedPeer);
					System.out.println("Removed peer: " + removedPeer);
				},
				(peer, incomingConnection) -> {
					System.out.println("Received incoming connection: " + incomingConnection + " from peer: " + peer);
					incomingConnection.setOnData((t, byteBuffer) -> System.out.println(new String(byteBuffer.array())));
				}
		);

		Scanner sc = new Scanner(System.in);
		System.out.print("Name: ");
		String name = sc.nextLine();

		for (RemotePeer peer : remotePeers) {
			Connection connection = peer.connect();
			connection.setOnClose(connection1 -> System.out.println("Connection close."));
//			connection.setOnData(((connection1, byteBuffer) -> {
//				System.out.println("Receiving data...");
//				System.out.println(new String(byteBuffer.array()));
//			}));
			connection.send(ByteBuffer.wrap(("Hello from " + name).getBytes()));
			connection.close();
		}
	}
}
