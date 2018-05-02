package controllers;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Helpers {
	/**
	 * @param obj to be serialized, can be of any non-primitive type
	 * @return obj serialized in the form of a byte array
	 */
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

	public static void writeData(FileChannel fileChannel, ByteBuffer data) {
		try {
			fileChannel.write(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static ByteBuffer readData(FileChannel fileChannel, int position, int length) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(length);
		try {
			fileChannel.read(byteBuffer, position);
		} catch (IOException e) {
			e.printStackTrace();
		}
		byteBuffer.rewind();
		return byteBuffer;
	}
}
