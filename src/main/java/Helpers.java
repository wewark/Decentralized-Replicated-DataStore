import java.io.*;

public class Helpers {
	public static byte[] serialize(Object obj) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream os;

		try {
			os = new ObjectOutputStream(out);
			os.writeObject(obj);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out.toByteArray();
	}

	public static Object deserialize(byte[] data) {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		ObjectInputStream is;

		try {
			is = new ObjectInputStream(in);
			return is.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return new Object();
	}

	public static byte[] concatenate(byte[] a, byte[] b) {
		byte[] ret = new byte[a.length + b.length];
		int i = 0;
		for (byte cb : a)
			ret[i++] = cb;
		for (byte cb : b)
			ret[i++] = cb;
		return ret;
	}
}
