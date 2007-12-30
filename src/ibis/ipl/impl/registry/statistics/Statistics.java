package ibis.ipl.impl.registry.statistics;

import ibis.util.ThreadPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

public final class Statistics implements Runnable {

    public static final int VERSION = 1;

    private static final Logger logger = Logger.getLogger(Statistics.class);

    private final long start;

    private long offset;

    private final String[] opcodes;

    private final double[] totalTimes;

    private final long[] incomingRequestCounter;

    private final long[] outgoingRequestCounter;

    private final long[] bytesIn;

    private final long[] bytesOut;

    private String id;

    private String poolName;
    
    private File file;

    private long writeInterval;

    List<DataPoint> poolSizeHistory;

    int currentPoolSize;

    List<DataPoint> electionEventHistory;

    public Statistics(String[] opcodes) {
        this.opcodes = opcodes;
        this.id = "unknown";
        this.poolName = "unknown";

        start = System.currentTimeMillis();
        offset = 0;

        totalTimes = new double[opcodes.length];
        incomingRequestCounter = new long[opcodes.length];
        outgoingRequestCounter = new long[opcodes.length];
        bytesIn = new long[opcodes.length];
        bytesOut = new long[opcodes.length];

        poolSizeHistory = new LinkedList<DataPoint>();
        electionEventHistory = new LinkedList<DataPoint>();

        currentPoolSize = 0;
    }

    public Statistics(File file) throws IOException {
        DataInputStream in = new DataInputStream(new FileInputStream(file));

        int version = in.readInt();

        if (version != VERSION) {
            throw new IOException("cannot read statistics file version: "
                    + version);
        }

        start = in.readLong();
        offset = in.readLong();

        id = in.readUTF();

        int nrOfOpcodes = in.readInt();

        if (nrOfOpcodes < 0) {
            throw new IOException("negative number of opcodes");
        }

        opcodes = new String[nrOfOpcodes];
        totalTimes = new double[nrOfOpcodes];
        incomingRequestCounter = new long[nrOfOpcodes];
        outgoingRequestCounter = new long[nrOfOpcodes];
        bytesIn = new long[nrOfOpcodes];
        bytesOut = new long[nrOfOpcodes];

        for (int i = 0; i < nrOfOpcodes; i++) {
            opcodes[i] = in.readUTF();
            totalTimes[i] = in.readDouble();
            incomingRequestCounter[i] = in.readLong();
            outgoingRequestCounter[i] = in.readLong();
            bytesIn[i] = in.readLong();
            bytesOut[i] = in.readLong();
        }

        poolSizeHistory = new LinkedList<DataPoint>();
        electionEventHistory = new LinkedList<DataPoint>();

        int nrOfSizeDataPoints = in.readInt();
        if (nrOfSizeDataPoints < 0) {
            throw new IOException("negative list size");
        }
        for (int i = 0; i < nrOfSizeDataPoints; i++) {
            poolSizeHistory.add(new DataPoint(in.readLong(), in.readLong()));
        }

        int nrOfElectionDataPoints = in.readInt();
        if (nrOfElectionDataPoints < 0) {
            throw new IOException("negative list size");
        }

        for (int i = 0; i < nrOfElectionDataPoints; i++) {
            electionEventHistory.add(new DataPoint(in.readLong(), in.readLong()));
        }

        currentPoolSize = in.readInt();

        in.close();
    }

    public synchronized void write() {
        logger.debug("writing statistics for: " + id);
        try {
            File file = new File("statistics" + File.separator + poolName + File.separator + id);

            if (file.exists()) {
                file.renameTo(new File(file.getPath() + ".old"));
            }

            file.getParentFile().mkdirs();

            DataOutputStream out =
                new DataOutputStream(new FileOutputStream(file));

            out.writeInt(VERSION);

            out.writeLong(start);
            out.writeLong(offset);
            out.writeUTF(id);

            out.writeInt(opcodes.length);

            for (int i = 0; i < opcodes.length; i++) {
                out.writeUTF(opcodes[i]);
                out.writeDouble(totalTimes[i]);
                out.writeLong(incomingRequestCounter[i]);
                out.writeLong(outgoingRequestCounter[i]);
                out.writeLong(bytesIn[i]);
                out.writeLong(bytesOut[i]);
            }

            out.writeInt(poolSizeHistory.size());

            for (DataPoint point : poolSizeHistory) {
                out.writeLong(point.getTime());
                out.writeLong(point.getValue());
            }

            out.writeInt(electionEventHistory.size());

            for (DataPoint point : electionEventHistory) {
                out.writeLong(point.getTime());
                out.writeLong(point.getValue());
            }

            out.writeInt(currentPoolSize);

            out.close();

        } catch (IOException e) {
            logger.error("cannot write statistics to " + file, e);
        }
        logger.debug("DONE writing statistics for: " + id);
    }

    public synchronized void add(byte opcode, long time, long bytesReceived,
            long bytesSend, boolean incoming) {
        if (opcode >= opcodes.length) {
            logger.error("unknown opcode in handling stats: " + opcode);
        }

        totalTimes[opcode] = totalTimes[opcode] + time;
        if (incoming) {
            incomingRequestCounter[opcode]++;
        } else {
            outgoingRequestCounter[opcode]++;
        }
        bytesIn[opcode] += bytesReceived;
        bytesOut[opcode] += bytesSend;
    }

    synchronized void clear() {
        for (int i = 0; i < opcodes.length; i++) {
            totalTimes[i] = 0;
            incomingRequestCounter[i] = 0;
            outgoingRequestCounter[i] = 0;
        }
    }

    public synchronized boolean empty() {
        for (byte i = 0; i < opcodes.length; i++) {
            if (totalTimes[i] != 0) {
                return false;
            }
        }

        if (poolSizeHistory.size() > 0) {
            return false;
        }

        if (electionEventHistory.size() > 0) {
            return false;
        }

        return true;
    }
    
    public synchronized void print(Formatter out) {
        out.format("#statistics for %s\n", id);
        
        printCommStats(out);
        
        out.format("#total traffic = %.2f MB\n",totalTraffic());
        
        printPoolSizeHistory(out);
    }
        

    public synchronized void printCommStats(Formatter out) {
        long totalTraffic = 0;

        out.format("#communication statistics\n");
        out.format("#TYPE          IN_COUNT OUT_COUNT BYTES_IN BYTES_OUT TOTAL_TIME   AVG_TIME\n");
        out.format("#                                                        (sec)       (ms)\n");
        for (byte i = 0; i < opcodes.length; i++) {
            totalTraffic += bytesIn[i] + bytesOut[i];

            double average =
                totalTimes[i]
                        / (incomingRequestCounter[i] + outgoingRequestCounter[i]);
            if (incomingRequestCounter[i] == 0
                    && outgoingRequestCounter[i] == 0) {
                average = 0;
            }

            out.format("#%-12s %9d %9d %8d %9d %10.2f %10.2f\n",
                opcodes[i], incomingRequestCounter[i],
                outgoingRequestCounter[i], bytesIn[i], bytesOut[i],
                totalTimes[i] / 1000.0, average);
        }
        out.format("#distance from server: %d Ms\n", offset);
    }

    public synchronized void printPoolSizeHistory(Formatter out) {
        out.format("#pool size history\n");
        out.format("#TIME POOL_SIZE\n");
        for(DataPoint point: poolSizeHistory) {
            double time  = ((double) point.getTime() - start + offset) / 1000.0;
            out.format("%.2f %d\n", time, point.getValue());
        }
    }
    
    public synchronized void newPoolSize(int poolSize) {
        if (!poolSizeHistory.isEmpty()) {
            long lastPoolSize = poolSizeHistory.get(poolSizeHistory.size() - 1).getValue();
            
            if (poolSize == lastPoolSize) {
                //ignore this update, value equal to last
                return;
            }
        }
        
        poolSizeHistory.add(new DataPoint(poolSize));

        logger.trace("reported pool size now: " + poolSize);
    }

    public synchronized void electionEvent() {
        electionEventHistory.add(new DataPoint(electionEventHistory.size() + 1));
    }

    public synchronized long getStartTime() {
        return start;
    }

    public synchronized long getEndTime() {
        long result = start;

        if (poolSizeHistory.size() > 0) {
            long time =
                poolSizeHistory.get(poolSizeHistory.size() - 1).getTime();

            if (time > result) {
                result = time;
            }
        }

        if (electionEventHistory.size() > 0) {
            long time =
                electionEventHistory.get(electionEventHistory.size() - 1).getTime();

            if (time > result) {
                result = time;
            }
        }

        return result;
    }

    public synchronized long poolSizeAt(long time) {
        time += offset;

        if (poolSizeHistory.size() == 0) {
            return 0;
        }

        long result = 0;

        for (DataPoint point : poolSizeHistory) {
            if (point.getTime() > time) {
                // previous point is result
                return result;
            } else {
                result = point.getValue();
            }
        }
        // return -1 (we don't know)
        return -1;
    }

    public synchronized DataPoint[] getPoolSizeData() {
        return poolSizeHistory.toArray(new DataPoint[0]);
    }

    /**
     * Total data send/received by the registry (in Mib)
     */
    public synchronized double totalTraffic() {
        double totalTraffic = 0;

        for (byte i = 0; i < opcodes.length; i++) {
            totalTraffic = totalTraffic + bytesIn[i] + bytesOut[i];
        }

        return totalTraffic / 1024.0 / 1024.0;
    }

    public synchronized String getID() {
        return id;
    }

    public synchronized void setID(String id, String poolName) {
        this.id = id;
        this.poolName = poolName;
    }

    public synchronized long getOffset() {
        return offset;
    }

    public synchronized void setOffset(long offset) {
        this.offset = offset;
    }

    public void startWriting(long writeInterval) {
        this.writeInterval = writeInterval;

        ThreadPool.createNew(this, "statistics writer");
    }

    public synchronized void run() {
        while (true) {
            write();

            try {
                wait(writeInterval);
            } catch (InterruptedException e) {
                // IGNORE
            }
        }
    }

    public String toString() {
        return id;
    }
    
    public static void main(String[] args) throws IOException {
        Formatter formatter = new Formatter(System.out);

        for (int i = 0; i < args.length; i++) {
            File file = new File(args[i]);
            
            Statistics statistics = new Statistics(file);
            
            statistics.print(formatter);
        }
        
        formatter.flush();

        
    }

    
    

}