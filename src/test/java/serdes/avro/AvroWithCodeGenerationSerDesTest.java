package serdes.avro;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.Test;

import util.TestUtil;
import example.avro.User;

/**
 * @author yhli
 * @link http://avro.apache.org/docs/current/gettingstartedjava.html
 */
public class AvroWithCodeGenerationSerDesTest {

	private File dataFile = new File(TestUtil.getTempDir(), "users.avro");

	@Test
	public void serialize() throws IOException {

		// Serialize user1 and user2 to disk
		DatumWriter<User> userDatumWriter = new SpecificDatumWriter<User>(
				User.class);
		DataFileWriter<User> dataFileWriter = new DataFileWriter<User>(
				userDatumWriter);

		List<User> users = this.createUsers();
		dataFileWriter.create(users.get(0).getSchema(), dataFile);
		for (User user : users) {
			dataFileWriter.append(user);
		}
		dataFileWriter.close();
	}

	@Test
	public void deserialize() throws IOException {
		// Deserialize Users from disk
		DatumReader<User> userDatumReader = new SpecificDatumReader<User>(
				User.class);
		DataFileReader<User> dataFileReader = new DataFileReader<User>(
				dataFile, userDatumReader);
		User user = null;
		while (dataFileReader.hasNext()) {
			// Reuse user object by passing it to next(). This saves us from
			// allocating and garbage collecting many objects for files with
			// many items.
			user = dataFileReader.next(user);
			System.out.println(user);
		}
	}

	@Test
	public void serdesByteArray() throws IOException {
		// Serializing to a byte array
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
		DatumWriter<User> writer = new SpecificDatumWriter<User>(
				User.getClassSchema());

		List<User> users = this.createUsers();
		for (User user : users) {
			writer.write(user, encoder);
		}
		encoder.flush();
		out.close();
		byte[] serializedBytes = out.toByteArray();
		System.out.println("toByteArray: " + serializedBytes.length);

		// Deserializing from a byte array
		SpecificDatumReader<User> reader = new SpecificDatumReader<User>(
				User.getClassSchema());
		BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
				serializedBytes, null);
		User user = null;
		while (!decoder.isEnd()) {
			user = reader.read(user, decoder);
			System.out.println(user);
		}
	}

	private List<User> createUsers() {
		List<User> users = new ArrayList<User>();

		User user1 = new User();
		user1.setName("Alyssa");
		user1.setFavoriteNumber(256);
		// Leave favorite color null
		users.add(user1);

		// Alternate constructor
		User user2 = new User("Ben", 7, "red");
		users.add(user2);

		// Construct via builder
		User user3 = User.newBuilder().setName("Charlie")
				.setFavoriteColor("blue").setFavoriteNumber(null).build();
		users.add(user3);
		return users;
	}
}
