package ibis.ipl.impl.registry.central.client;

import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisConfigurationException;
import ibis.ipl.IbisProperties;
import ibis.ipl.impl.IbisIdentifier;
import ibis.ipl.impl.registry.central.Election;
import ibis.ipl.impl.registry.central.ElectionSet;
import ibis.ipl.impl.registry.central.Event;
import ibis.ipl.impl.registry.central.EventList;
import ibis.ipl.impl.registry.central.Member;
import ibis.ipl.impl.registry.central.MemberSet;
import ibis.ipl.impl.registry.central.RegistryProperties;
import ibis.util.ThreadPool;
import ibis.util.TypedProperties;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;

final class Pool implements Runnable {

    private static final Logger logger = Logger.getLogger(Pool.class);

    private final String poolName;

    private final boolean closedWorld;

    private final int size;

    private final MemberSet members;

    private final ElectionSet elections;

    private final EventList eventList;

    private final Registry registry;

    private final Log log;

    private boolean initialized;

    private boolean closed;

    private boolean stopped;

    private int time;

    Pool(IbisCapabilities capabilities, TypedProperties properties,
            Registry registry) {
        this.registry = registry;

        if (properties.getBooleanProperty(RegistryProperties.LOG)) {
            log = new Log(this);
        } else {
            log = null;
        }

        members = new MemberSet();
        elections = new ElectionSet();
        eventList = new EventList();

        time = -1;
        initialized = false;
        closed = false;
        stopped = false;

        // get the pool ....
        poolName = properties.getProperty(IbisProperties.POOL_NAME);
        if (poolName == null) {
            throw new IbisConfigurationException(
                    "cannot initialize registry, property "
                            + IbisProperties.POOL_NAME + " is not specified");
        }

        closedWorld = capabilities.hasCapability(IbisCapabilities.CLOSEDWORLD);

        if (closedWorld) {
            try {
                size = properties.getIntProperty(IbisProperties.POOL_SIZE);
            } catch (final NumberFormatException e) {
                throw new IbisConfigurationException(
                        "could not start registry for a closed world ibis, "
                                + "required property: "
                                + IbisProperties.POOL_SIZE + " undefined", e);
            }
        } else {
            size = -1;
        }

    }

    synchronized Event[] getEventsFrom(int start) {
        return eventList.getList(start);
    }

    String getName() {
        return poolName;
    }

    boolean isClosedWorld() {
        return closedWorld;
    }

    int getSize() {
        return size;
    }

    synchronized Member getRandomMember() {
        return members.getRandom();
    }

    synchronized int getTime() {
        return time;
    }

    synchronized void setTime(int time) {
        this.time = time;
    }

    synchronized boolean isInitialized() {
        return initialized;
    }

    boolean isStopped() {
        return stopped;
    }

    // new incoming events
    synchronized void newEventsReceived(Event[] events) {
        eventList.add(events);
        notifyAll();
    }

    synchronized void purgeHistoryUpto(int time) {
        eventList.purgeUpto(time);
    }

    synchronized void init(DataInput in) throws IOException {
        if (initialized) {
            logger.error("Tried to initialize registry state twice");
            return;
        }

        logger.debug("reading bootstrap state");

        members.init(in);
        elections.init(in);
        int nrOfSignals = in.readInt();
        if (nrOfSignals < 0) {
            throw new IOException("negative number of signals");
        }

        ArrayList<Event> signals = new ArrayList<Event>();
        for (int i = 0; i < nrOfSignals; i++) {
            signals.add(new Event(in));
        }

        closed = in.readBoolean();
        time = in.readInt();

        // Create list of "old" events

        SortedSet<Event> events = new TreeSet<Event>();
        events.addAll(members.getJoinEvents());
        events.addAll(elections.getEvents());
        events.addAll(signals);

        // pass old events to the registry
        for (Event event : events) {
            registry.handleEvent(event);
        }

        // add info to the log
        if (log != null) {
            // start log
            log.start("Ibis-" + registry.getIbisIdentifier().getID() + ".log");

            for (Event event : events) {
                log.log(event, members.size());
            }
        }

        initialized = true;
        notifyAll();

        logger.debug("bootstrap complete");

        ThreadPool.createNew(this, "pool event generator");
    }

    synchronized void writeState(DataOutput out, int joinTime)
            throws IOException {
        if (!initialized) {
            throw new IOException("state not initialized yet");
        }

        members.writeTo(out);
        elections.writeTo(out);

        Event[] signals = eventList.getSignalEvents(joinTime, time);
        out.writeInt(signals.length);
        for (Event event : signals) {
            event.writeTo(out);
        }

        out.writeBoolean(closed);
        out.writeInt(time);

    }

    synchronized IbisIdentifier getElectionResult(String election, long timeout)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeout;

        if (timeout == 0) {
            deadline = Long.MAX_VALUE;
        }

        Election result = elections.get(election);

        while (result == null) {
            final long timeRemaining = deadline - System.currentTimeMillis();

            if (timeRemaining <= 0) {
                logger.debug("getElectionResullt deadline expired");
                return null;
            }

            try {
                logger.debug("waiting " + timeRemaining + " for election");
                wait(timeRemaining);
                logger.debug("DONE waiting " + timeRemaining + " for election");
            } catch (final InterruptedException e) {
                // IGNORE
            }
            result = elections.get(election);
        }
        logger.debug("getElection result = " + result);
        return result.getWinner();
    }

    private synchronized void handleEvent(Event event) {
        switch (event.getType()) {
        case Event.JOIN:
            members.add(new Member(event.getFirstIbis(), event));
            break;
        case Event.LEAVE:
            members.remove(event.getFirstIbis());
            break;
        case Event.DIED:
            IbisIdentifier died = event.getFirstIbis();
            members.remove(died);
            if (died.equals(registry.getIbisIdentifier())) {
                logger.debug("we were declared dead");
                stop();
            }
            break;
        case Event.SIGNAL:
            // Not handled here
            break;
        case Event.ELECT:
            elections.put(new Election(event));
            break;
        case Event.UN_ELECT:
            elections.remove(event.getDescription());
            break;
        case Event.POOL_CLOSED:
            closed = true;
            break;
        default:
            logger.error("unknown event type: " + event.getType());
        }

        if (log != null) {
            log.log(event, members.size());
        }

        // wake up threads waiting for events
        notifyAll();
    }

    synchronized void waitForAll() {
        if (!closedWorld) {
            throw new IbisConfigurationException("waitForAll() called but not "
                    + "closed world");
        }

        while (!(closed || stopped)) {
            try {
                wait();
            } catch (final InterruptedException e) {
                // IGNORE
            }
        }
    }

    synchronized void stop() {
        stopped = true;
        notifyAll();

        if (log != null) {
            log.save();
        }
    }

    /**
     * Handles incoming events, passes events to the registry
     */
    public synchronized void run() {
        while (!(initialized || stopped)) {
            try {
                wait();
            } catch (InterruptedException e) {
                // IGNORE
            }
        }

        while (true) {
            Event event = eventList.get(time);

            while (event == null && !stopped) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // IGNORE
                }
                event = eventList.get(time);
            }

            if (stopped) {
                return;
            }

            if (event == null) {
                logger.error("could not get event!");
                continue;
            }

            handleEvent(event);
            registry.handleEvent(event);
            time++;
        }
    }
}