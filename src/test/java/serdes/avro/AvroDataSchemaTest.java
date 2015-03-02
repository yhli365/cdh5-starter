package serdes.avro;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.TestUtil;

/**
 * @author yhli
 * @link http://avro.apache.org/docs/current/spec.html#Schema+Resolution
 * 
 */
public class AvroDataSchemaTest {
	private static final Logger log = LoggerFactory
			.getLogger(AvroDataSchemaTest.class);

	private Random rnd = new Random();
	private int recordNum = 5;
	private int subItems = 3;

	private String dataDir = TestUtil.getTempDir();
	private String schemaDir = "src/main/avro/test";

	@Test
	public void testSchemaDataTypes() throws IOException {
		serdes("data_type.avsc", "data_type.avsc", "datatype.avro");
	}

	@Test
	public void testSchemaAddField01() throws IOException {
		// 记录的模式演变(增加字段)
		// 1) 写入 旧 =》 读取 新 操作:通过默认值读取新字段，因为写人时并没有该字段
		// 2) 写人 新 =》 读取 旧 操作:读取时并不知道新写人的新字段，所以忽略该字段(投影)

		// #1
		serdes("data_entity.avsc", "data_entity2.avsc", "data_entity.avro");
	}

	@Test
	public void testSchemaAddField02() throws IOException {
		// #2
		serdes("data_entity2.avsc", "data_entity.avsc", "data_entity2.avro");
	}

	@Test
	public void testSchemaDelField01() throws IOException {
		// 记录的模式演变(删除字段)
		// 1) 写入 旧 =》 读取 新 操作:读取时忽略已删除的字段(投影)
		// 2) 写人 新 =》 读取 旧 操作:写人时不写人已删除的字段。如果旧模式对该字段有默认值，那么读取时可以使用该默认值，
		// ...................否则产生错误。这种情况下，最好同时更新读取模式，或在更新写人模式之前更新读取模式

		// #1
		serdes("data_entity.avsc", "data_entity3.avsc", "data_entity.avro");
	}

	@Test
	public void testSchemaDelField02() throws IOException {
		// #2
		serdes("data_entity3.avsc", "data_entity.avsc", "data_entity3.avro");
	}

	@Test
	public void testSchemaAdjust() throws IOException {
		// 记录的模式演变
		// 1) 字段改名: 使用aliases兼容读取旧字段
		serdes("data_entity.avsc", "data_entity4.avsc", "data_entity.avro");
	}

	private void serdes(String writeSchemaFile, String readSchemaFile,
			String dataFile) throws IOException {
		serialize(writeSchemaFile, dataFile);
		deserialize(readSchemaFile, dataFile);
	}

	private void serialize(String schemaFile, String dataFile)
			throws IOException {
		log.info("serialize#schemaFile={}, dataFile={}", schemaFile, dataFile);
		Schema schema = new Schema.Parser().parse(new File(schemaDir,
				schemaFile));

		DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<GenericRecord>(
				schema);
		DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<GenericRecord>(
				datumWriter);
		dataFileWriter.create(schema, new File(dataDir, dataFile));

		System.out.println("\n-----#Wirter Schema: " + schema.getDoc() + "\n"
				+ schema.toString());
		System.out.println("\n-----#List Records: ");

		List<GenericRecord> records = this.createRecords(schema);
		for (GenericRecord record : records) {
			dataFileWriter.append(record);
			System.out.println(record);
		}
		dataFileWriter.close();
		System.out.println();
	}

	private List<GenericRecord> createRecords(Schema schema) throws IOException {
		List<GenericRecord> records = new ArrayList<GenericRecord>();
		for (int seq = 0; seq < recordNum; seq++) {
			GenericRecord record = createRecord(schema, seq,
					seq != recordNum - 1);
			records.add(record);
		}
		return records;
	}

	private GenericRecord createRecord(Schema schema, int seq, boolean isNull)
			throws IOException {
		GenericRecord record = new GenericData.Record(schema);
		for (Schema.Field f : schema.getFields()) {
			String name = f.name();
			if (name.endsWith("_id")) {
				record.put(name, "record-#" + seq);
			} else {
				Object val = createRecordField(f, seq, isNull);
				record.put(name, val);
			}
		}
		return record;
	}

	private Object createRecordField(Schema.Field field, int seq, boolean isNull)
			throws IOException {
		Schema schema = field.schema();
		if (schema.getType() == Schema.Type.UNION) {
			List<Schema> schemas = schema.getTypes();
			if (isNull) {
				schema = schemas.get(rnd.nextInt(schemas.size()));
			} else {
				List<Schema> schemas2 = new ArrayList<Schema>();
				for (Schema s : schemas) {
					if (s.getType() != Schema.Type.NULL) {
						schemas2.add(s);
					}
				}
				if (schemas2.isEmpty()) {
					schema = schemas.get(rnd.nextInt(schemas.size()));
				} else {
					schema = schemas2.get(rnd.nextInt(schemas2.size()));
				}
			}
		}

		Object val = null;
		Schema.Type type = schema.getType();
		switch (type) {
		case NULL:
			break;
		case BOOLEAN:
			val = seq % 3 == 0;
			break;
		case INT:
			val = rnd.nextInt();
			break;
		case LONG:
			val = rnd.nextLong();
			break;
		case FLOAT:
			val = rnd.nextFloat();
			break;
		case DOUBLE:
			val = rnd.nextDouble();
			break;
		case BYTES:
			val = ByteBuffer.wrap(TestUtil.randBytes(5));
			break;
		case STRING:
			val = field.name() + "_r" + seq;
			break;
		case RECORD:
			GenericRecord rec = createRecord(schema, 1, false);
			val = rec;
			break;
		case ENUM:
			String[] enums = new String[] { "SPADES", "HEARTS", "DIAMONDS",
					"CLUBS" };
			val = enums[rnd.nextInt(enums.length)];
			break;
		case ARRAY:
			List<String> arr = new ArrayList<String>();
			for (int s = 0; s < subItems; s++) {
				arr.add(field.name() + "_r" + seq + "c" + s);
			}
			val = arr;
			break;
		case MAP:
			Map<String, Integer> map = new HashMap<String, Integer>();
			for (int s = 0; s < subItems; s++) {
				map.put(field.name() + "_r" + seq + "c" + s, rnd.nextInt(10000));
			}
			val = map;
			break;
		case FIXED:
			byte[] bytes = TestUtil.randBytes(schema.getFixedSize());
			val = new GenericData.Fixed(schema, bytes);
			break;
		default:
			throw new IOException("Unsupported type: " + type);
		}
		return val;
	}

	private void deserialize(String schemaFile, String dataFile)
			throws IOException {
		log.info("deserialize#schemaFile={}, dataFile={}", schemaFile, dataFile);
		Schema schema = new Schema.Parser().parse(new File(schemaDir,
				schemaFile));

		DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(
				schema);
		DataFileReader<GenericRecord> dataFileReader = new DataFileReader<GenericRecord>(
				new File(dataDir, dataFile), datumReader);

		Schema schemaWrite = dataFileReader.getSchema();
		System.out.println("\n-----#Reader Schema: " + schema.getDoc() + "\n"
				+ schema.toString());
		System.out.println("\n-----#Wirter Schema: " + schemaWrite.getDoc()
				+ "\n" + schemaWrite.toString());
		System.out.println("\n-----#List Records: ");

		GenericRecord record = null;
		while (dataFileReader.hasNext()) {
			record = dataFileReader.next(record);
			System.out.println(record);
		}
		if (record != null) {
			detailRecord(record);
		}
	}

	private void detailRecord(GenericRecord record) {
		System.out.println("\n-----#Last Record Detail: ");
		System.out.println("Reader schema: " + record.getSchema().getDoc());
		int seq = 1;
		List<Schema.Field> flists = record.getSchema().getFields();
		for (Schema.Field f : flists) {
			Object val = record.get(f.name());
			String msg;
			if (f.schema().getType() == Schema.Type.UNION) {
				msg = "UNION " + f.schema().getTypes();
			} else {
				msg = f.schema().getType().toString();
			}
			System.out.println("#" + (seq++) + " " + f.name() + " => " + msg);
			if (StringUtils.isNotEmpty(f.doc())) {
				System.out.println("  [doc] = " + f.doc());
			}

			if (!f.aliases().isEmpty()) {
				System.out.println("  [alias] = " + f.aliases());
			}

			if (f.defaultValue() != null) {
				System.out.println("  [defaultValue] = "
						+ f.defaultValue().asText());
			}

			System.out.println("  [valueType] = "
					+ (val == null ? "null" : val.getClass().getName()));
			System.out.println("  [value] = " + (val == null ? "null" : val));
		}

	}

}
