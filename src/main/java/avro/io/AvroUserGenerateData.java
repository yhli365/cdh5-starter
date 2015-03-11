package avro.io;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.hadoop.io.AvroSequenceFile;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapred.AvroValue;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.HadoopUtils;
import example.avro.User;

/**
 * 模拟生成User对象数据.
 * 
 * @author yhli
 * 
 */
public class AvroUserGenerateData extends Configured implements Tool {

	private static final Logger log = LoggerFactory
			.getLogger(AvroUserGenerateData.class);

	public static void main(String[] args) throws Exception {
		ToolRunner.run(new AvroUserGenerateData(), args);
	}

	@Override
	public int run(String[] args) throws Exception {
		Configuration conf = getConf();
		String cmd = args[0];
		if ("-gen.avrofile".equalsIgnoreCase(cmd)) {
			return genAvroFile(conf, args);
		} else if ("-text.avrofile".equalsIgnoreCase(cmd)) {
			return textAvroFile(conf, args);
		} else if ("-gen.seqfile".equalsIgnoreCase(cmd)) {
			return genSeqFile(conf, args);
		} else if ("-text".equalsIgnoreCase(cmd)) {
			return text(conf, args);
		} else if ("-text.user".equalsIgnoreCase(cmd)) {
			return textUser(conf, args);
		} else {
			System.err.println("Unkown command: " + cmd);
		}
		return 0;
	}

	private int genAvroFile(Configuration conf, String[] args)
			throws IOException {
		File file = new File(args[1]);
		String codec = conf.get("codec", "null");

		if (file.getParentFile() != null) {
			file.getParentFile().mkdirs();
		}
		DatumWriter<User> userDatumWriter = new SpecificDatumWriter<User>(
				User.class);
		DataFileWriter<User> dataFileWriter = new DataFileWriter<User>(
				userDatumWriter);
		dataFileWriter.setCodec(CodecFactory.fromString(codec));
		// dataFileWriter.setSyncInterval(DataFileConstants.DEFAULT_SYNC_INTERVAL);
		dataFileWriter.create(User.SCHEMA$, file);

		// Create random users
		int size = conf.getInt("size", 20);
		AvroUserGen aug = new AvroUserGen(size);
		for (int i = 0; i < size; i++) {
			dataFileWriter.append(aug.gen());
		}
		dataFileWriter.close();
		System.out.println("genAvroFile ok: " + file.getAbsolutePath() + " => "
				+ size);

		return 0;
	}

	private int textAvroFile(Configuration conf, String[] args)
			throws IOException {
		File dataFile = new File(args[1]);

		DatumReader<User> userDatumReader = new SpecificDatumReader<User>(
				User.class);
		DataFileReader<User> dataFileReader = new DataFileReader<User>(
				dataFile, userDatumReader);
		try {
			int offset = conf.getInt("offset", 1);
			int len = conf.getInt("len", 10);
			log.info("read data: offset=" + offset + ", len=" + len);
			int seq = 0;
			User user = null;
			while (dataFileReader.hasNext()) {
				seq++;
				if (seq < offset) {
					continue;
				}
				if (seq >= offset + len) {
					break;
				}
				user = dataFileReader.next(user);
				System.out.println(String.format("[%3d] %s", seq, user));
			}
		} finally {
			dataFileReader.close();
		}

		return 0;
	}

	/**
	 * 生成User模拟数据，文件格式为seqfile
	 * 
	 * @param conf
	 * @param args
	 * @return
	 * @throws IOException
	 */
	private int genSeqFile(Configuration conf, String[] args)
			throws IOException {
		Path dst = new Path(args[1]);

		FileSystem fs = FileSystem.get(conf);
		Path p = dst.getParent();
		if (p != null && !fs.exists(p)) {
			fs.mkdirs(p);
		}

		SequenceFile.Writer writer = null;
		try {
			AvroKey<Long> key = new AvroKey<Long>();
			AvroValue<User> value = new AvroValue<User>();
			Schema keyWriterSchema = Schema.create(Schema.Type.LONG);

			AvroSequenceFile.Writer.Options options = new AvroSequenceFile.Writer.Options();
			options.withConfiguration(conf);
			options.withFileSystem(fs);
			options.withOutputPath(dst);
			options.withCompressionType(HadoopUtils.getCompressionType(conf));
			options.withCompressionCodec(HadoopUtils.getCompressionCodec(conf));
			// options.withKeyClass(AvroKey.class);
			options.withKeySchema(keyWriterSchema);
			// options.withValueClass(AvroValue.class);
			options.withValueSchema(User.getClassSchema());
			writer = AvroSequenceFile.createWriter(options);

			int size = conf.getInt("size", 20);
			AvroUserGen aug = new AvroUserGen(size);
			for (int i = 0; i < size; i++) {
				key.datum(System.currentTimeMillis());
				value.datum(aug.gen());
				writer.append(key, value);
			}
			IOUtils.closeStream(writer);
			writer = null;
			System.out.println("genSeqFile ok: " + dst.toString() + " => "
					+ size);
		} finally {
			if (writer != null) {
				IOUtils.closeStream(writer);
			}
		}
		return 0;
	}

	@SuppressWarnings("unchecked")
	private int text(Configuration conf, String[] args) throws IOException {
		Path dst = new Path(args[1]);

		AvroSequenceFile.Reader reader = null;
		try {
			AvroSequenceFile.Reader.Options options = new AvroSequenceFile.Reader.Options();
			options.withConfiguration(conf);
			options.withFileSystem(FileSystem.get(conf));
			options.withInputPath(dst);
			reader = new AvroSequenceFile.Reader(options);

			int offset = conf.getInt("offset", 1);
			int len = conf.getInt("len", 10);
			log.info("read data: offset=" + offset + ", len=" + len);

			AvroKey<GenericRecord> key = new AvroKey<GenericRecord>();
			AvroValue<GenericRecord> value = new AvroValue<GenericRecord>();
			int seq = 0;
			while ((key = (AvroKey<GenericRecord>) reader.next(key)) != null) {
				seq++;
				if (seq < offset) {
					continue;
				}
				if (seq >= offset + len) {
					break;
				}

				value = (AvroValue<GenericRecord>) reader
						.getCurrentValue(value);
				System.out.println(String.format("[%3d] %s => %s", seq,
						key.datum(), value.datum()));
			}
		} finally {
			if (reader != null) {
				IOUtils.closeStream(reader);
			}
		}

		return 0;
	}

	@SuppressWarnings("unchecked")
	private int textUser(Configuration conf, String[] args) throws IOException {
		Path dst = new Path(args[1]);

		AvroSequenceFile.Reader reader = null;
		try {
			AvroSequenceFile.Reader.Options options = new AvroSequenceFile.Reader.Options();
			options.withConfiguration(conf);
			options.withFileSystem(FileSystem.get(conf));
			options.withInputPath(dst);
			options.withKeySchema(Schema.create(Schema.Type.LONG));
			options.withValueSchema(User.getClassSchema());
			reader = new AvroSequenceFile.Reader(options);

			int offset = conf.getInt("offset", 1);
			int len = conf.getInt("len", 10);
			log.info("read data: offset=" + offset + ", len=" + len);

			AvroKey<Long> key = new AvroKey<Long>();
			AvroValue<User> value = new AvroValue<User>();
			int seq = 0;
			while ((key = (AvroKey<Long>) reader.next(key)) != null) {
				seq++;
				if (seq < offset) {
					continue;
				}
				if (seq >= offset + len) {
					break;
				}

				value = (AvroValue<User>) reader.getCurrentValue(value);
				System.out.println(String.format("[%3d] %s => %s", seq,
						key.datum(), value.datum()));
			}
		} finally {
			if (reader != null) {
				IOUtils.closeStream(reader);
			}
		}

		return 0;
	}

	public static class AvroUserGen {
		Random random = new Random();
		// String[] NAMES = { "Abby", "Aimee", "Daisy" };
		Integer[] NUMBERS = { 1, 2, 6, 8, 9, null };
		String[] COLORS = { "red", "orange", "yellow", "green", "blue",
				"purple", null };
		int user_num;

		public AvroUserGen(int size) {
			user_num = size / 10;
			if (user_num < 3) {
				user_num = 3;
			}
		}

		public User gen() {
			User user = new User("user" + random.nextInt(user_num),
					NUMBERS[random.nextInt(NUMBERS.length)],
					COLORS[random.nextInt(COLORS.length)]);
			return user;
		}

	}

}
