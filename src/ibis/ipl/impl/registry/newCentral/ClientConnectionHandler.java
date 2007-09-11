package ibis.ipl.impl.registry.newCentral;

import ibis.ipl.impl.IbisIdentifier;
import ibis.util.ThreadPool;

import java.io.IOException;

import org.apache.log4j.Logger;

final class ClientConnectionHandler implements Runnable {

    private static final Logger logger = Logger
            .getLogger(ClientConnectionHandler.class);

    private final ConnectionFactory connectionFactory;

    private final Registry registry;

    ClientConnectionHandler(ConnectionFactory connectionFactory,
            Registry registry) {
        this.connectionFactory = connectionFactory;
        this.registry = registry;

        ThreadPool.createNew(this, "client connection handler");
    }

    private void handleGossip(Connection connection) throws IOException {
        logger.debug("got a gossip request");

        IbisIdentifier identifier = new IbisIdentifier(connection.in());
        String poolName = identifier.poolName();
        int peerTime = connection.in().readInt();

        if (!poolName.equals(registry.getPoolName())) {
            logger.error("wrong pool: " + poolName + " instead of "
                    + registry.getPoolName());
            connection.closeWithError("wrong pool: " + poolName
                    + " instead of " + registry.getPoolName());
            return;
        }

        int localTime = registry.getTime();

        connection.sendOKReply();
        connection.out().writeInt(localTime);
        connection.out().flush();

        if (localTime > peerTime) {
            Event[] sendEvents = registry.getEventsFrom(peerTime);

            connection.out().writeInt(sendEvents.length);
            for (Event event : sendEvents) {
                event.writeTo(connection.out());
            }
            connection.out().flush();

        } else if (localTime < peerTime) {
            int nrOfEvents = connection.in().readInt();

            if (nrOfEvents > 0) {
                Event[] newEvents = new Event[nrOfEvents];
                for (int i = 0; i < newEvents.length; i++) {
                    newEvents[i] = new Event(connection.in());
                }

                connection.close();

                registry.newEventsReceived(newEvents);
            }
        }
        connection.close();
    }

    private void handlePush(Connection connection) throws IOException {
        logger.debug("got a push from the server");

        String poolName = connection.in().readUTF();

        if (!poolName.equals(registry.getPoolName())) {
            logger.error("wrong pool: " + poolName + " instead of "
                    + registry.getPoolName());
            connection.closeWithError("wrong pool: " + poolName
                    + " instead of " + registry.getPoolName());
        }

        connection.out().writeInt(registry.getTime());
        connection.out().flush();

        connection.getAndCheckReply();

        int events = connection.in().readInt();

        if (events < 0) {
            connection.closeWithError("negative event size");
            return;
        }

        Event[] newEvents = new Event[events];
        for (int i = 0; i < newEvents.length; i++) {
            newEvents[i] = new Event(connection.in());
        }

        int minEventTime = connection.in().readInt();

        connection.sendOKReply();

        connection.close();

        registry.newEventsReceived(newEvents);
        registry.purgeHistoryUpto(minEventTime);
    }

    private void handlePing(Connection connection) throws IOException {
        logger.debug("got a ping request");
        connection.sendOKReply();
        registry.getIbisIdentifier().writeTo(connection.out());
        connection.out().flush();
        connection.close();
    }

    private void handleGetState(Connection connection) throws IOException {
        logger.debug("got a state request");

        IbisIdentifier identifier = new IbisIdentifier(connection.in());
        int minTime = connection.in().readInt();

        String poolName = identifier.poolName();

        if (!poolName.equals(registry.getPoolName())) {
            logger.error("wrong pool: " + poolName + " instead of "
                    + registry.getPoolName());
            connection.closeWithError("wrong pool: " + poolName
                    + " instead of " + registry.getPoolName());
        }

        if (!registry.isInitialized()) {
            connection.closeWithError("state not available");
            return;
        }

        int time = registry.getTime();
        if (time < minTime) {
            connection.closeWithError("minimum time requirement not met: "
                    + minTime + " vs " + time);
            return;
        }

        connection.sendOKReply();

        registry.writeState(connection.out());

        connection.out().flush();
        connection.close();
    }

    public void run() {
        Connection connection = null;
        try {
            logger.debug("accepting connection");
            connection = connectionFactory.accept();
            logger.debug("connection accepted");
        } catch (IOException e) {
            if (registry.isStopped()) {
                return;
            }
            logger.error("Accept failed, waiting a second, will retry", e);

            // wait a bit
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
                // IGNORE
            }
        }

        // create new thread for next connection
        ThreadPool.createNew(this, "client connection handler");

        if (connection == null) {
            return;
        }

        try {
            byte magic = connection.in().readByte();

            if (magic != Protocol.CLIENT_MAGIC_BYTE) {
                throw new IOException(
                        "Invalid header byte in accepting connection");
            }

            byte opcode = connection.in().readByte();

            logger.debug("received request: " + Protocol.opcodeString(opcode));

            switch (opcode) {
            case Protocol.OPCODE_GOSSIP:
                handleGossip(connection);
                break;
            case Protocol.OPCODE_PUSH:
                handlePush(connection);
                break;
            case Protocol.OPCODE_PING:
                handlePing(connection);
                break;
            case Protocol.OPCODE_GET_STATE:
                handleGetState(connection);
                break;
            default:
                logger.error("unknown opcode in request: " + opcode + "("
                        + Protocol.opcodeString(opcode) + ")");
            }
            logger.debug("done handling request");
        } catch (IOException e) {
            logger.error("error on handling request", e);
        } finally {
            connection.close();
        }
    }
}
