package ibis.ipl.apps.benchmarks.registry;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.log4j.Logger;

public final class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    private final IbisApplication[] apps;

    Main(int threads, boolean generateEvents) throws Exception {

        apps = new IbisApplication[threads];
        for (int i = 0; i < threads; i++) {
            logger.debug("starting thread " + i + " of " + threads);
            apps[i] = new IbisApplication(generateEvents);
        }
    }

    void printStats() {
        int totalSeen = 0;
        for (int i = 0; i < apps.length; i++) {
            totalSeen += apps[i].nrOfIbisses();
        }
        double average = (double) totalSeen / (double) apps.length;

        String date = DateFormat.getTimeInstance().format(
                new Date(System.currentTimeMillis()));

        System.out.printf(date + " average seen members = %.2f\n", average,
                apps.length);
    }

    public static void main(String[] args) throws Exception {
        int threads = 1;
        boolean generateEvents = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--threads")) {
                i++;
                threads = new Integer(args[i]);
            } else if (args[i].equalsIgnoreCase("--events")) {
                generateEvents = true;
            } else {
                System.err.println("unknown option: " + args[i]);
                System.exit(1);
            }
        }

        Main main = new Main(threads, generateEvents);

        logger.debug("created ibisses, running main loop");

        while (true) {
            Thread.sleep(10000);
        }
    }

}
