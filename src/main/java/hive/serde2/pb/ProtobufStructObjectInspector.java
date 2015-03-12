package hive.serde2.pb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessage;

public class ProtobufStructObjectInspector extends StructObjectInspector {

	protected static class ProtobufField implements StructField {
		private String name;
		private ObjectInspector oi;

		protected ProtobufField(String name, ObjectInspector oi) {
			this.name = name;
			this.oi = oi;
		}

		@Override
		public String getFieldName() {
			return name;
		}

		@Override
		public ObjectInspector getFieldObjectInspector() {
			return oi;
		}

		@Override
		public String toString() {
			return (name + ": " + oi.toString());
		}

		@Override
		public String getFieldComment() {
			return "@TODO: field comment";
		}
	}

	private List<ProtobufField> fields;
	private Descriptor descriptor = null;

	private Map<String, FieldDescriptor> fieldDescriptorMap;
	private List<FieldDescriptor> fieldDescriptorList;

	public ProtobufStructObjectInspector(List<String> structFieldNames,
			List<ObjectInspector> structFieldObjectInspectors,
			Descriptor descriptor) {
		fields = new ArrayList<ProtobufField>(structFieldNames.size());
		for (int i = 0; i < structFieldNames.size(); ++i) {
			fields.add(new ProtobufField(structFieldNames.get(i),
					structFieldObjectInspectors.get(i)));
		}
		this.descriptor = descriptor;
		this.fieldDescriptorList = descriptor.getFields();
		this.fieldDescriptorMap = new HashMap<String, FieldDescriptor>();
		for (FieldDescriptor fd : this.fieldDescriptorList) {
			this.fieldDescriptorMap.put(fd.getName(), fd);
		}
	}

	public Descriptor getDescriptor() {
		return descriptor;
	}

	@Override
	public List<? extends StructField> getAllStructFieldRefs() {
		return fields;
	}

	@Override
	public Object getStructFieldData(Object data, StructField fieldRef) {
		if (data == null) {
			return null;
		}

		GeneratedMessage message = (GeneratedMessage) data;
		FieldDescriptor fd = fieldDescriptorMap.get(fieldRef.getFieldName());
		return getField(message, fd);
	}

	@Override
	public List<Object> getStructFieldsDataAsList(Object data) {
		if (data == null) {
			return null;
		}
		GeneratedMessage message = (GeneratedMessage) data;
		ArrayList<Object> fieldDataList = new ArrayList<Object>(fields.size());
		for (int i = 0; i < fieldDescriptorList.size(); ++i) {
			fieldDataList.add(getField(message, fieldDescriptorList.get(i)));
		}
		return fieldDataList;
	}

	private Object getField(GeneratedMessage message, FieldDescriptor fd) {

		if (fd.isOptional()) {
			if (!message.hasField(fd) && !fd.hasDefaultValue()) {
				return null;
			}
		}
		return message.getField(fd);
	}

	@Override
	public StructField getStructFieldRef(String fieldName) {
		return ObjectInspectorUtils
				.getStandardStructFieldRef(fieldName, fields);
	}

	@Override
	public String toString() {
		String rt = null;
		for (int i = 0; i < fields.size(); ++i) {
			rt += fields.get(i).toString();
		}
		return rt;
	}

	@Override
	public Category getCategory() {
		return Category.STRUCT;
	}

	@Override
	public String getTypeName() {
		return ObjectInspectorUtils.getStandardStructTypeName(this);
	}
}
