package example;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.util.GenericOptionsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于hadoop命令功能扩展.<br/>
 * hadoop jar x.jar mainClass arg1 arg2... <br/>
 * 1)实现hdfs、local jars自动加载, 通过将第三方jar放在本地与hdfs指定目录下，不用打包到jar中，方便远程调试.<br/>
 * 
 * @author yhli
 * 
 */
public class ExampleAppDriver {
	private static final Logger LOG = LoggerFactory
			.getLogger(ExampleAppDriver.class);

	public static void main(String args[]) {
		int exitCode = -1;
		try {
			// Make sure they gave us a program name.
			if (args.length == 0) {
				System.out
						.println("An example program must be given as the first argument.");
				return;
			}

			// Remove the leading argument and call main
			String[] new_args = new String[args.length - 1];
			for (int i = 1; i < args.length; ++i) {
				new_args[i - 1] = args[i];
			}
			invoke(args[0], new_args);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		System.exit(exitCode);
	}

	private static void invoke(String mainClassName, String[] args)
			throws Throwable {
		try {
			// classpath
			Configuration conf = new Configuration();
			new GenericOptionsParser(conf, args);
			if (processHdfsClasspath(conf)) {
				processLocalClasspath(conf);
			}

			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			Class<?> mainClass = Class.forName(mainClassName, true, loader);
			Class<?>[] paramTypes = new Class<?>[] { String[].class };
			Method main = mainClass.getMethod("main", paramTypes);

			if (conf.get(MRJobConfig.CLASSPATH_FILES) != null) {
				Class<?> superClass = mainClass.getSuperclass();
				while (superClass != null) {
					if (Configured.class.isAssignableFrom(superClass)) {
						String[] new_args = new String[args.length + 2];
						new_args[0] = "-D" + MRJobConfig.CLASSPATH_FILES + "="
								+ conf.get(MRJobConfig.CLASSPATH_FILES);
						new_args[1] = "-D" + MRJobConfig.CACHE_FILES + "="
								+ conf.get(MRJobConfig.CACHE_FILES);
						for (int i = 0; i < args.length; i++) {
							new_args[i + 2] = args[i];
						}
						args = new_args;
						break;
					}
					superClass = superClass.getSuperclass();
				}
			}

			main.invoke(null, new Object[] { args });
		} catch (InvocationTargetException except) {
			throw except.getCause();
		}
	}

	@SuppressWarnings("deprecation")
	private static boolean processHdfsClasspath(Configuration conf)
			throws IOException {
		String cpath = conf.get("share.lib", "/share/lib");
		if (StringUtils.isEmpty(cpath)) {
			return false;
		}
		// LOG.info("addHdfsClassPath# {}", cpath);
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
						org.apache.hadoop.filecache.DistributedCache
								.addFileToClassPath(new Path(p), conf, fs);
						// LOG.info("addHdfsClassPath: seq={}, file={}", count,
						// p);
					}
				}
			}
		}
		LOG.info("addHdfsClassPath# {} => {}", rootdir.toString(), count);
		return true;
	}

	private static void processLocalClasspath(Configuration conf)
			throws MalformedURLException {
		String cpath = conf.get("share.lib.local", "/share/lib");
		if (StringUtils.isEmpty(cpath)) {
			return;
		}

		List<URL> cp = new ArrayList<URL>();
		File rootdir = new File(cpath);
		Stack<File> stack = new Stack<File>();
		stack.push(rootdir);
		int count = 0;
		while (!stack.isEmpty()) {
			File path = stack.pop();
			File[] fsarr = path.listFiles();
			if (fsarr == null) {
				break;
			}
			for (File fss : fsarr) {
				if (fss.isDirectory()) {
					stack.push(fss);
				} else {
					String p = fss.getName();
					if (p.endsWith(".jar") || p.endsWith(".zip")) {
						count++;
						cp.add(fss.toURI().toURL());
					}
				}
			}
		}
		if (count > 0) {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader instanceof URLClassLoader) {
				URL[] jars = ((URLClassLoader) loader).getURLs();
				URL[] libjars = new URL[count + jars.length];
				int idx = 0;
				for (URL url : jars) {
					libjars[idx++] = url;
				}
				for (URL url : cp) {
					libjars[idx++] = url;
				}
				loader = new URLClassLoader(libjars);
				LOG.info("addLocalClassPath# {} => {}/" + cp.size(),
						rootdir.toString(), count);
			} else {
				URL[] libjars = cp.toArray(new URL[0]);
				loader = new URLClassLoader(libjars, Thread.currentThread()
						.getContextClassLoader());
				LOG.info("addLocalClassPath# {} => {}", rootdir.toString(),
						count);
			}
			Thread.currentThread().setContextClassLoader(loader);
		}
	}

}
