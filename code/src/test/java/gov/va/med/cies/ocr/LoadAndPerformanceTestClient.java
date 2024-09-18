package gov.va.med.cies.ocr;

import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a load generator for the OCR service.
 * It is not invoked during the normal SDLC process.
 */
public class LoadAndPerformanceTestClient {
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public final static Duration STATUS_RETRY_DELAY = Duration.parse("PT5S");
    public final static int STATUS_RETRY = 20;
    public final static int POST_SOCKET_TIMEOUT_MILLIS = 5000;
    public final static int POST_CONNECT_TIMEOUT_MILLIS = 5000;
    public final static int POST_CONNECTIONREQUEST_TIMEOUT_MILLIS = 30000;


    private static Options options = new Options();
    private static Log LOGGER = LogFactory.getLog(LoadAndPerformanceTestClient.class);

    static {
        // add the environment 'v' option, specifies the environment to use
        options.addOption("v", true, "The environment, one of: 'dev', 'prprd' or 'prod'. Default is 'dev'.");
        // add the root URL 'r' option,
        options.addOption("r", true, "The root URL, cannot be specified with the 'v' option.");
        // add the thread count option, defines the number of threads to process documents from
        options.addOption("t", true, "A positive integer specifying the number of threads from which documents will be processed.");
        // add the repeater option
        options.addOption("n", true, "A positive integer specifying the number of documents to process.");
    }

    public static void main(String[] argv) {
        ResourceBundle resourceBundle = null;
        try {
            resourceBundle = ResourceBundle.getBundle("ResourceBundle");
        } catch (Exception e) {
            System.err.println("Failed to load properties from resource bundle, " + e.getMessage());
            System.exit(1);
        }

        CommandLineParser parser = new DefaultParser();
        String hostName = null;
        int threads = 1;
        int iterations = 1;
        int explicitIterations = 0;
        ExecutorService executorService = null;
        ExecutorCompletionService<DocumentProcessorResult> executorCompletionService = null;

        try {
            CommandLine cmd = parser.parse(options, argv);
            if (cmd.hasOption('r')) {
                hostName = cmd.getOptionValue('r');
            } else if (cmd.hasOption('v')) {
                hostName = resourceBundle.getString(cmd.getOptionValue('v').toUpperCase() + "_HOST");
            } else {
                hostName = resourceBundle.getString("DEV_HOST");
            }
            URL hostUrl = new URL(hostName);

            if (cmd.hasOption('t'))
                threads = Integer.parseInt(cmd.getOptionValue('t'));

            if (cmd.hasOption('n'))
                explicitIterations = Integer.parseInt(cmd.getOptionValue('n'));

            executorService = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                AtomicInteger threadSerialNumber = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "DocumentProcessorThread-" + threadSerialNumber.getAndIncrement());
                }
            });
            executorCompletionService = new ExecutorCompletionService<DocumentProcessorResult>(executorService);

            // this gets us a List of all the Files from the command line (directories have been expanded)
            List<File> documentFiles = getSourceFiles(cmd.getArgList());
            if (explicitIterations == 0)
                iterations = documentFiles.size();
            else
                iterations = explicitIterations;

            for (int iteration = 0; iteration < iterations; ++iteration) {
                final String identifier = UUID.randomUUID().toString();
                final File documentFile = documentFiles.get(iteration % (documentFiles.size()));
                executorCompletionService.submit(new DocumentProcessor(identifier, hostUrl, documentFile));
            }

            for (int iteration = 0; iteration < iterations; ++iteration) {
                try {
                    Future<DocumentProcessorResult> result = executorCompletionService.take();
                    DocumentProcessorResult documentResult = result.get();
                    LOGGER.info(documentResult);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (ParseException e) {
            System.err.println(e.getMessage());
        } catch (MalformedURLException e) {
            System.err.println("Unable to parse host URL, " + e.getMessage());
        } finally {
            try {
                executorService.awaitTermination(20, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static List<File> getSourceFiles(final List<String> files) {
        final List<File> result = new ArrayList<>();
        // for each file (or directory) name on the command line
        files.forEach(fileName -> {
            File file = new File(fileName);
            // if the file exists, add it (or its direct descendants if it is a directory
            // NOTE: this is not recursive, it just adds the files under a specified directory
            if (file.exists()) {
                List<File> childFiles = Collections.emptyList();

                if (file.isDirectory()) {
                    // if the file is a directory then collect the child files
                    childFiles = Arrays.asList(Objects.requireNonNull(file.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getAbsolutePath().endsWith(".pdf");
                        }
                    })));
                } else {
                    // if the file is a file, then just create a List with one entry
                    childFiles = Collections.singletonList(file);
                }

                result.addAll(childFiles);
            } else {
                System.err.println("File or directory '" + fileName + "' does not exist, skipping ...");
            }
        });

        return result;
    }
}