package ibis.ipl.impl.registry.statistics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;

import org.apache.log4j.Logger;

public class StatisticsProcessor {
    private static final Logger logger = Logger.getLogger(StatisticsProcessor.class);
    
    private final Experiment[] experiments;

    private final long interval;

    private final long duration;

    StatisticsProcessor(File[] directories, long interval) throws IOException {
        
        experiments = new Experiment[directories.length];
        long duration = 0;
        
        for (int i = 0; i < experiments.length; i++) {
            experiments[i] = new Experiment(directories[i]);
            if (experiments[i].duration() > duration) {
                duration = experiments[i].duration();
            }
        }
        this.duration = duration;
        
        if (interval == 0) {
            this.interval = calculateInterval();
        } else {
            this.interval = interval;
        }
    }
    

    private long calculateInterval() {
        long result = duration / 100;

        if (result < 1) {
            result = 1;
        }

        logger.debug("interval = " + result);

        return result;
    }
    
    //write pool history for all experiments
    private void writePoolHistory(Formatter out) {

        out.format("experiments:\n");
        for (Experiment experiment: experiments) {
            out.format("%s\n", experiment.getName());
        }
        
        
        for(long time = 0; time <= (duration + 2 * interval) ; time += interval) {
            out.format("%d", time);
            
            for (Experiment experiment: experiments) {
                out.format(" %.2f", experiment.averagePoolSize(time));
                out.format(" %.2f", experiment.serverPoolSize(time));
            }
            
            out.format("\n");
        }
        out.flush();
    }
    
//    /**
//     * Write statistics to a file
//     */
//    private void write() {
//
//        File file = null;
//        try {
//            file = new File(poolName + ".stats.server");
//            logger.debug("writing statistic file " + file);
//            if (file.exists()) {
//                file.renameTo(new File("old." + file.getName()));
//            }
//
//            Formatter out = new Formatter(file);
//
//            out.format("#server stats:\n");
//            serverStatistics.printCommStats(out, opcodeNames);
//
//            out.format("#server total data transfer = %.2f\n",
//                serverStatistics.totalTraffic());
//
//            out.format("#pool size\n");
//            writePoolHistory(start, end, interval, out, serverStatistics);
//
//            out.flush();
//            out.close();
//
//            // client stats
//
//            file = new File(poolName + ".stats.clients");
//            logger.debug("writing statistic file " + file);
//            if (file.exists()) {
//                file.renameTo(new File("old." + file.getName()));
//            }
//
//            out = new Formatter(file);
//
//            out.format("#average #total data transfer = %.2f\n",
//                averageClientTraffic());
//
//            out.format("#average pool size\n");
//            writePoolHistory(start, end, interval, out, getClientStatistics());
//
//            out.format("#raw data:\n");
//
//            for (Statistics statistics : clientStatistics.values()) {
//                out.format("offset = %d\n", statistics.getOffset());
//                for (DataPoint point : statistics.getPoolSizeData()) {
//                    out.format("%d %d\n", point.getTime() - start,
//                        point.getValue());
//                }
//                out.format("##############\n");
//            }
//
//            out.flush();
//            out.close();
//
//            logger.debug("done writing statistic file " + file);
//        } catch (IOException e) {
//            logger.error("could not write statistics file: " + file, e);
//        }
//
//    }
    
    public static void main(String[] args) throws IOException {
        ArrayList<File> directories = new ArrayList<File>();
        long interval = 0;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--interval")) {
                i++;
                interval = Long.parseLong(args[i]);
            } else if (args[i].startsWith("-")) {
                System.err.println("unknown option: " + args[i]);
                System.exit(1);
            } else {
                directories.add(new File(args[i]));
            }
        }

        if (directories.size() == 0) {
            System.err.println("usage: StatisticsProcessor [OPTIONS] DIRECTORY [DIRECTORY]...");
            System.err.println("options:");
            System.err.println("--interval INTERVAL Interval of datapoints in graphs (seconds)");
            System.exit(1);
        }
        
        StatisticsProcessor processor = new StatisticsProcessor(directories.toArray(new File[0]), interval);
        
        Formatter out = new Formatter("statistics");
        
        processor.writePoolHistory(out);
        out.close();
    }


}
