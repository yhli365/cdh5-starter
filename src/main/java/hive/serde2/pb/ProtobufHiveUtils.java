package hive.serde2.pb;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hive.common.JavaUtils;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

public class ProtobufHiveUtils {

	public static Descriptor getMsgDescriptor(Class<?> msgClass) {
		try {
			Method getDescriptorMethod = msgClass.getMethod("getDescriptor",
					(Class<?>[]) null);
			Descriptor msgDescriptor = (Descriptor) getDescriptorMethod.invoke(
					null, (Object[]) null);
			return msgDescriptor;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Class<?> loadTableMsgClass(String outerName, String name) {
		try {
			StringBuilder fullName = new StringBuilder();
			String pkgName = "tdw";
			fullName.append(pkgName);
			fullName.append(".");

			ArrayList<String> pieces = new ArrayList<String>();
			pieces.add(outerName);
			pieces.add(name);
			fullName.append(StringUtils.join(pieces, "$"));

			Class<?> cls = Class.forName(fullName.toString(), true,
					getpbClassLoader());
			return cls;
		} catch (java.lang.ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Class<?> loadFieldMsgClass(Descriptor msgDescriptor) {
		try {
			String fullName = getFieldMsgFullName(msgDescriptor);
			Class<?> cls = Class.forName(fullName, true,
					JavaUtils.getClassLoader());
			return cls;
		} catch (java.lang.ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String getFieldMsgFullName(Descriptor msgDescriptor) {
		FileDescriptor fileDescriptor = msgDescriptor.getFile();

		StringBuilder fullName = new StringBuilder();
		assert (fileDescriptor.getOptions().hasJavaPackage());
		fullName.append(fileDescriptor.getOptions().getJavaPackage());
		fullName.append(".");

		ArrayList<String> pieces = new ArrayList<String>();
		Descriptor msg = msgDescriptor;
		while (true) {
			if (msg.getContainingType() == null) {
				if (!fileDescriptor.getOptions().hasJavaOuterClassname()) {
					assert (false);
				}
				String outerClassName = fileDescriptor.getOptions()
						.getJavaOuterClassname();
				pieces.add(0, msg.getName());
				pieces.add(0, outerClassName);
				break;
			} else {
				pieces.add(0, msg.getName());
				msg = msgDescriptor.getContainingType();
			}
		}
		fullName.append(StringUtils.join(pieces, "$"));
		return fullName.toString();
	}

	public static ClassLoader getpbClassLoader() {
		return JavaUtils.getClassLoader();
	}

}
