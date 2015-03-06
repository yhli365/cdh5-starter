package util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.DefaultCodec;

/**
 * @author yhli
 * 
 */
public class HadoopUtils {

	public static SequenceFile.CompressionType getCompressionType(
			Configuration conf) {
		String str = conf.get("compression.type", "block");
		CompressionType compress = CompressionType.valueOf(str.toUpperCase());
		return compress;
	}

	public static CompressionCodec getCompressionCodec(Configuration conf) {
		CompressionCodecFactory codecFactory = new CompressionCodecFactory(conf);
		CompressionCodec codec = codecFactory.getCodecByName(conf.get("codec",
				"SnappyCodec")); // LzoCodec,SnappyCodec
		if (codec == null) {
			codec = new DefaultCodec();
		}
		return codec;
	}

}
