package controllers;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Helpers {
	/**
	 * @param obj Object to be serialized, can be of any non-primitive type.
	 * @return "obj" serialized in the form of a byte array.
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

	/**
	 * @param data
	 * @return Returns the deserialized data, it has to be casted to
	 * its original type before it is used.
	 */
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
		//TODO, this potentially need double the size of the file in RAM just to add a single byte ? man this is bad! :'D
		//TODO FIND A WORKAROUND.
		byte[] ret = new byte[a.length + b.length];
		int i = 0;
		for (byte cb : a)
			ret[i++] = cb;
		for (byte cb : b)
			ret[i++] = cb;
		return ret;
	}

	/**
	 * @param fileChannel This is like a pointer that reads
	 *                    or writes to a file byte by byte.
	 * @param data        Data to be written.
	 */
	public static void writeData(FileChannel fileChannel, ByteBuffer data) {
		try {
			fileChannel.write(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This function is often passed to jReto transfer function
	 * as a data source to allow it to send a file in chunks.
	 *
	 * @param fileChannel This is like a pointer that reads
	 *                    or writes to a file byte by byte.
	 * @param position    Position to start reading at.
	 * @param length      Number of bytes to be read.
	 * @return
	 */
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
