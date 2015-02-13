package example.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class HadoopToolRunner extends ToolRunner {
	private static final Logger LOG = LoggerFactory
			.getLogger(HadoopToolRunner.class);

	public static int run(Tool tool, String[] args) throws Exception {
		return run(tool.getConf(), tool, args);
	}

	public static int run(Configuration conf, Tool tool, String[] args)
			throws Exception {
		if (conf == null) {
			conf = new Configuration();
		}
		GenericOptionsParser parser = new GenericOptionsParser(conf, args);
		// set the configuration back, so that Tool can configure itself
		tool.setConf(conf);

		// get the args w/o generic hadoop args
		String[] toolArgs = parser.getRemainingArgs();

		// add by yhli
		processClasspath(conf);

		return tool.run(toolArgs);
	}

	private static void processClasspath(Configuration conf) throws IOException {
		String cpath = conf.get("classpath", "/share/lib");
		if (StringUtils.isNotEmpty(cpath)) {
			LOG.info("addFileToClassPath# {}", cpath);
			FileSystem fs = FileSystem.get(conf);
			Path rootdir = new Path(cpath);
			Stack<Path> stack = new Stack<Path>();
			stack.push(rootdir);
			int count = 0;
			while (!stack.isEmpty()) {
				Path path = stack.pop();
				FileStatus[] fsarr = fs.listStatus(path);
				if (fsarr == null) {
					break;
				}
				for (FileStatus fss : fsarr) {
					if (fss.isDirectory()) {
						stack.push(fss.getPath());
					} else {
						String p = fss.getPath().toString();
						if (p.endsWith(".jar") || p.endsWith(".zip")) {
							count++;
							if (p.startsWith("hdfs://")) {
								int idx = p.indexOf('/', 8);
								p = p.substring(idx);
							}
							DistributedCache.addFileToClassPath(new Path(p),
									conf, fs);
							LOG.info("addFileToClassPath: seq=" + count
									+ ", file=" + p);
						}
					}
				}
			}
			LOG.info("addFileToClassPath# {} => {}", rootdir.toString(), count);

			List<URL> cp = new ArrayList<URL>();
			Path[] paths = DistributedCache.getFileClassPaths(conf);
			for (Path tmp : paths) {
				if (tmp.getFileSystem(conf).equals(FileSystem.getLocal(conf))) {
					cp.add(FileSystem.getLocal(conf).pathToFile(tmp).toURI()
							.toURL());
				} else {
					LOG.warn("The libjars file " + tmp
							+ " is not on the local " + "filesystem. Ignoring.");
				}
			}
			URL[] libjars = cp.toArray(new URL[0]);
			if (libjars != null && libjars.length > 0) {
				conf.setClassLoader(new URLClassLoader(libjars, conf
						.getClassLoader()));
				Thread.currentThread().setContextClassLoader(
						new URLClassLoader(libjars, Thread.currentThread()
								.getContextClassLoader()));
			}
		}
	}

}
