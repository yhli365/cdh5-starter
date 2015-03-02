package util;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * @author yhli
 * 
 */
public class TestUtil {

	private static byte[] RANDOM_BYTES;
	private static Random rand = new Random();

	static {
		RANDOM_BYTES = new byte[('z' - 'a' + 1) * 2];
		int k = 0;
		for (int i = 'a'; i <= 'z'; i++) {
			RANDOM_BYTES[k++] = (byte) i;
		}
		for (int i = 'A'; i <= 'Z'; i++) {
			RANDOM_BYTES[k++] = (byte) i;
		}
	}

	public static String getTempDir() {
		return "D:/ydata/tmp";
	}

	public static byte[] randBytes(int len) {
		byte[] bytes = new byte[len];
		for (int i = 0; i < len; i++) {
			bytes[i] = RANDOM_BYTES[rand.nextInt(RANDOM_BYTES.length)];
		}
		return bytes;
	}

	public static void execMain(Class<?> mainClass, String... commandLine)
			throws Exception {
		String[] args;
		if (commandLine == null) {
			args = new String[0];
		} else if (commandLine.length == 1) {
			args = commandLine[0].split("\\s+");
		} else {
			args = commandLine;
		}

		Method main = null;
		try {
			Class<?>[] paramTypes = new Class<?>[] { String[].class };
			main = mainClass.getMethod("main", paramTypes);
			main.invoke(null, new Object[] { args });
		} catch (NoSuchMethodException e) {
			e.printStackTrace(System.out);
		}
	}

}
