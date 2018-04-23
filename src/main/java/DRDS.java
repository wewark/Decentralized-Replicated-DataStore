import org.hive2hive.core.api.H2HNode;
import org.hive2hive.core.api.configs.FileConfiguration;
import org.hive2hive.core.api.configs.NetworkConfiguration;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.api.interfaces.IH2HNode;
import org.hive2hive.core.api.interfaces.INetworkConfiguration;

import java.net.InetAddress;
import java.net.UnknownHostException;

// Decentralized Replicated Data Store
public class DRDS {
	public static void main(String[] args) throws UnknownHostException {
		// TODO: Dynamic peer discovery
		INetworkConfiguration netConfig = NetworkConfiguration.create(InetAddress.getByName("192.168.1.101"));
		IFileConfiguration fileConfig = FileConfiguration.createDefault();

		IH2HNode node1 = H2HNode.createNode(fileConfig);
		IH2HNode node2 = H2HNode.createNode(fileConfig);
		node1.connect(NetworkConfiguration.createInitial());
		node2.connect(netConfig);

		System.out.println("Node 1 is connected: " + node1.isConnected());
		System.out.println("Node 2 is connected: " + node2.isConnected());
	}
}
