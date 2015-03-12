package hive.serde2.pb;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class ProtobufObjectInspectorFactory {

	public static ProtobufStructObjectInspector getProtobufStructObjectInspector(
			List<String> structFieldNames,
			List<ObjectInspector> structFieldObjectInspectors,
			Descriptor descriptor) {

		ArrayList<Object> signature = new ArrayList<Object>();
		signature.add(structFieldNames);
		signature.add(structFieldObjectInspectors);
		signature.add(descriptor);

		ProtobufStructObjectInspector result = new ProtobufStructObjectInspector(
				structFieldNames, structFieldObjectInspectors, descriptor);
		return result;
	}

	public static ObjectInspector getFieldObjectInspectorFromTypeInfo(
			TypeInfo ti, FieldDescriptor fieldDescriptor) {
		try {
			switch (ti.getCategory()) {
			case PRIMITIVE:
				return PrimitiveObjectInspectorFactory
						.getPrimitiveJavaObjectInspector(((PrimitiveTypeInfo) ti)
								.getPrimitiveCategory());
			case STRUCT: {
				assert (fieldDescriptor.getType() == FieldDescriptor.Type.MESSAGE);
				Descriptor fieldMsgDescriptor = fieldDescriptor
						.getMessageType();
				List<FieldDescriptor> subFieldDescriptors = fieldMsgDescriptor
						.getFields();

				StructTypeInfo sti = (StructTypeInfo) ti;
				List<TypeInfo> subFieldTypeInfos = sti
						.getAllStructFieldTypeInfos();
				assert (subFieldDescriptors.size() == subFieldTypeInfos.size());
				int subFieldSize = subFieldDescriptors.size();

				ArrayList<ObjectInspector> subFieldObjectInspectors = new ArrayList<ObjectInspector>(
						subFieldSize);
				for (int i = 0; i < subFieldSize; i++) {
					ObjectInspector subFieldObjectInspector = getFieldObjectInspectorFromTypeInfo(
							subFieldTypeInfos.get(i),
							subFieldDescriptors.get(i));
					subFieldObjectInspectors.add(subFieldObjectInspector);
				}

				return new ProtobufStructObjectInspector(
						sti.getAllStructFieldNames(), subFieldObjectInspectors,
						fieldMsgDescriptor);
			}
			case LIST: {
				ListTypeInfo lti = (ListTypeInfo) ti;
				ObjectInspector elementObjectInspector = getFieldObjectInspectorFromTypeInfo(
						lti.getListElementTypeInfo(), fieldDescriptor);
				return ObjectInspectorFactory
						.getStandardListObjectInspector(elementObjectInspector);
			}
			default:
				throw new Exception("SerDe do not support type : "
						+ ti.getTypeName());
			}
		} catch (Exception e) {
			e.getStackTrace();
		}
		return null;
	}

}
