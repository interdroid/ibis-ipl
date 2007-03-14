/* $Id$ */

package ibis.impl.registry.tcp;

import ibis.impl.Ibis;
import ibis.impl.IbisIdentifier;
import ibis.impl.Location;
import ibis.impl.registry.RegistryProperties;
import ibis.io.Conversion;
import ibis.ipl.ConnectionRefusedException;
import ibis.ipl.ConnectionTimedOutException;
import ibis.ipl.IbisConfigurationException;
import ibis.util.IPUtils;
import ibis.util.TypedProperties;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.log4j.Logger;

public class NameServerClient extends ibis.impl.Registry implements Runnable,
        Protocol {

    private ElectionClient electionClient;

    private ServerSocket serverSocket;

    private final Ibis ibisImpl;

    private final boolean needsUpcalls;

    private final IbisIdentifier id;

    private volatile boolean stop = false;

    private InetSocketAddress serverSocketAddress;

    private String poolName;

    private InetAddress myAddress;

    private int localPort;

    private boolean left = false;

    private static Logger logger = Logger.getLogger(NameServerClient.class);

    private final NameServer server;

    public NameServerClient(Ibis ibis, boolean ndsUpcalls, byte[] data)
            throws IOException, IbisConfigurationException {
        this.ibisImpl = ibis;
        this.needsUpcalls = ndsUpcalls;

        myAddress = IPUtils.getAlternateLocalHostAddress();

        logger.debug("myAddress = " + myAddress);
        
        TypedProperties typedProperties = new TypedProperties(ibis.properties());

        String serverString = typedProperties.getProperty(
                RegistryProperties.SERVER_ADDRESS);

        if (serverString == null) {
            throw new IOException("server address not specified");
        }

        try {
            String[] addressParts = serverString.split(":", 2); 
            
            String serverHost = addressParts[0];
            int serverPort;
            
            if (addressParts.length < 2) {
                serverPort = typedProperties.getIntProperty(RegistryProperties.SERVER_PORT);
            } else {
                serverPort = Integer.parseInt(addressParts[1]);
            }

            serverSocketAddress = new InetSocketAddress(serverHost, serverPort);
            logger.debug("serverSocketAddress = " + serverSocketAddress);
        } catch (Throwable t) {
            throw new IOException("illegal server address (" + serverString
                    + ") : " + t.getMessage());
        }

        poolName = ibis.properties().getProperty(RegistryProperties.POOL);
        if (poolName == null) {
            throw new IbisConfigurationException(
                    "property ibis.registry.pool is not specified");
        }

        InetAddress localHost = InetAddress.getLocalHost();

        logger.debug("localHost = " + localHost);

        if (serverSocketAddress.getAddress().isLoopbackAddress()
                || localHost.equals(serverSocketAddress.getAddress())) {
            server = runNameServer(serverSocketAddress.getPort(),
                    new Properties(ibis.properties()));
        } else {
            server = null;
        }

        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(myAddress, 0), 50);

        localPort = serverSocket.getLocalPort();

        Socket s = nsConnect(serverSocketAddress, myAddress, true, 60);

        DataOutputStream out = null;
        DataInputStream in = null;

        try {

            out = new DataOutputStream(new BufferedOutputStream(s
                    .getOutputStream()));

            logger.debug("NameServerClient: contacting nameserver");
            out.writeByte(IBIS_JOIN);
            out.writeUTF(poolName);
            byte[] buf = Conversion.object2byte(myAddress);
            out.writeInt(buf.length);
            out.write(buf);
            out.writeInt(localPort);
            out.writeBoolean(ndsUpcalls);
            out.writeInt(data.length);
            out.write(data);
            Location l = getLocation(ibis.properties());
            l.writeTo(out);
            out.flush();

            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            int opcode = in.readByte();

            if (logger.isDebugEnabled()) {
                logger.debug("NameServerClient: nameserver reply, opcode "
                        + opcode);
            }

            switch (opcode) {
            case IBIS_REFUSED:
                throw new ConnectionRefusedException("NameServerClient: "
                        + "registry did not accept new pool");
            case IBIS_ACCEPTED:
                // read the ports for the other name servers and start the
                // receiver thread...
                int temp = in.readInt(); /* Port for the ElectionServer */
                electionClient = new ElectionClient(myAddress,
                        new InetSocketAddress(serverSocketAddress.getAddress(),
                                temp));
                id = new IbisIdentifier(in);
                if (ndsUpcalls) {
                    int poolSize = in.readInt();

                    if (logger.isDebugEnabled()) {
                        logger.debug("NameServerClient: accepted by nameserver"
                                + ", poolsize " + poolSize);
                    }

                    // Read existing nodes (including this one).
                    IbisIdentifier[] ids = new IbisIdentifier[poolSize];
                    for (int i = 0; i < poolSize; i++) {
                        ids[i] = new IbisIdentifier(in);
                        logger.debug("NameServerClient: join of " + ids[i]);
                    }
                    addMessage(IBIS_JOIN, ids);
                }

                Thread t = new Thread(this, "NameServerClient accept thread");
                t.setDaemon(true);
                t.start();
                t = new Thread("upcaller") {
                    public void run() {
                        upcaller();
                    }
                };
                t.setDaemon(true);
                t.start();
                break;
            default:
                throw new StreamCorruptedException(
                        "NameServerClient: got illegal opcode " + opcode);
            }
        } finally {
            NameServer.closeConnection(in, out, s);
        }
    }

    public IbisIdentifier getIbisIdentifier() {
        return id;
    }

    static Socket nsConnect(InetSocketAddress dest, InetAddress me,
            boolean verbose, int timeout) throws IOException {
        Socket s = null;
        int cnt = 0;
        while (s == null) {
            try {
                cnt++;
                s = NameServer.createClientSocket(dest, 1000);
            } catch (IOException e) {
                if (cnt >= timeout) {
                    if (verbose) {
                        logger.error("Nameserver client " + me + " failed"
                                + " to connect to nameserver\n at " + dest);
                        logger.error("Gave up after " + timeout + " seconds");
                    }
                    throw new ConnectionTimedOutException(e);
                }
                if (cnt == 10 && verbose) {
                    // Rather arbitrary, 10 seconds, print warning
                    logger.error("Nameserver client " + me + " failed"
                            + " to connect to nameserver\n at " + dest
                            + ", will keep trying");
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e2) {
                    // don't care
                }
            }
        }
        return s;
    }

    NameServer runNameServer(int port, Properties properties) {
        try {
            properties.setProperty(RegistryProperties.SERVER_PORT, Integer
                    .toString(port));
            properties.setProperty(RegistryProperties.SERVER_SINGLE, "true");
            NameServer server = new NameServer(properties);
            server.setDaemon(true);
            server.start();
            logger.warn("Automagically created "
                    + server.toString());
            return server;
        } catch (Throwable t) {
            // IGNORE
            return null;
        }
    }

    private static class Message {
        byte type;

        IbisIdentifier[] list;

        Message(byte type, IbisIdentifier[] list) {
            this.type = type;
            this.list = list;
        }
    }

    private LinkedList<Message> messages = new LinkedList<Message>();

    private void addMessage(byte type, IbisIdentifier[] list) {
        synchronized (messages) {
            messages.add(new Message(type, list));
            if (messages.size() == 1) {
                messages.notifyAll();
            }
        }
    }

    private void upcaller() {
        for (;;) {
            Message m;
            synchronized (messages) {
                while (messages.size() == 0) {
                    try {
                        messages.wait(60000);
                    } catch (Exception e) {
                        // ignored
                    }
                    if (messages.size() == 0 && stop) {
                        return;
                    }
                }
                m = (Message) messages.removeFirst();
            }
            switch (m.type) {
            case IBIS_JOIN:
                ibisImpl.joined(m.list);
                break;
            case IBIS_LEAVE:
                ibisImpl.left(m.list);
                break;
            case IBIS_DEAD:
                ibisImpl.died(m.list);
                break;
            case IBIS_MUSTLEAVE:
                ibisImpl.mustLeave(m.list);
                break;
            default:
                logger
                        .warn("Internal error, unknown opcode in message, ignored");
                break;
            }
        }
    }

    /**
     * Initializes the <code>location</code> field.
     */
    private static Location getLocation(Properties tp) {
        String location = tp.getProperty("ibis.location");
        if (location == null) {
            return Location.defaultLocation();
        }
        return new Location(location);
    }

    public void maybeDead(ibis.ipl.IbisIdentifier ibisId) throws IOException {
        Socket s = null;
        DataOutputStream out = null;

        try {
            logger.debug("Sending maybeDead(" + ibisId + ") to nameserver");
            s = nsConnect(serverSocketAddress, myAddress, false, 2);

            out = new DataOutputStream(new BufferedOutputStream(s
                    .getOutputStream()));

            out.writeByte(IBIS_ISALIVE);
            out.writeUTF(poolName);
            out.writeUTF(((IbisIdentifier) ibisId).myId);
            out.flush();
            logger.debug("NS client: isAlive sent");

        } catch (ConnectionTimedOutException e) {
            // Apparently, the nameserver left. Assume dead.
            return;
        } finally {
            NameServer.closeConnection(null, out, s);
        }
    }

    public void dead(ibis.ipl.IbisIdentifier corpse) throws IOException {
        Socket s = null;
        DataOutputStream out = null;

        try {
            s = nsConnect(serverSocketAddress, myAddress, false, 10);

            out = new DataOutputStream(new BufferedOutputStream(s
                    .getOutputStream()));

            out.writeByte(IBIS_DEAD);
            out.writeUTF(poolName);
            out.writeUTF(((IbisIdentifier) corpse).myId);
            logger.debug("NS client: kill sent");
        } catch (ConnectionTimedOutException e) {
            return;
        } finally {
            NameServer.closeConnection(null, out, s);
        }
    }

    public void mustLeave(ibis.ipl.IbisIdentifier[] ibisses) throws IOException {
        Socket s = null;
        DataOutputStream out = null;

        try {
            s = nsConnect(serverSocketAddress, myAddress, false, 10);

            out = new DataOutputStream(new BufferedOutputStream(s
                    .getOutputStream()));

            out.writeByte(IBIS_MUSTLEAVE);
            out.writeUTF(poolName);
            out.writeInt(ibisses.length);
            for (int i = 0; i < ibisses.length; i++) {
                out.writeUTF(((IbisIdentifier) ibisses[i]).myId);
            }
            logger.debug("NS client: mustLeave sent");
        } catch (ConnectionTimedOutException e) {
            return;
        } finally {
            NameServer.closeConnection(null, out, s);
        }
    }

    public long getSeqno(String name) throws IOException {
        Socket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;
        try {
            s = nsConnect(serverSocketAddress, myAddress, false, 10);

            out = new DataOutputStream(new BufferedOutputStream(s
                    .getOutputStream()));

            out.writeByte(SEQNO);
            out.writeUTF(name);
            out.flush();

            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            return in.readLong();

        } finally {
            NameServer.closeConnection(in, out, s);
        }
    }

    public void leave() {
        Socket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;

        logger.info("NS client: leave");

        try {
            s = nsConnect(serverSocketAddress, myAddress, false, 10);
        } catch (IOException e) {
            if (logger.isInfoEnabled()) {
                logger.info("leave: connect got exception", e);
            }
            return;
        }

        try {
            out = new DataOutputStream(new BufferedOutputStream(s
                    .getOutputStream()));

            out.writeByte(IBIS_LEAVE);
            out.writeUTF(poolName);
            out.writeUTF(id.myId);
            out.flush();
            logger.info("NS client: leave sent");

            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            in.readByte();
            logger.info("NS client: leave ack received");
        } catch (IOException e) {
            logger.info("leave got exception", e);
        } finally {
            NameServer.closeConnection(in, out, s);
        }

        if (!needsUpcalls) {
            synchronized (this) {
                stop = true;
            }
        } else {
            synchronized (this) {
                while (!left) {
                    try {
                        wait();
                    } catch (Exception e) {
                        // Ignored
                    }
                }
            }
        }

        if (server != null) {
            try {
                server.join();
            } catch (InterruptedException e) {
                // ignored
            }
        }

        logger.info("NS client: leave DONE");
    }

    public void run() {
        logger.info("NameServerClient: thread started");

        while (!stop) {

            Socket s;

            try {
                s = serverSocket.accept();

                logger.debug("NameServerClient: incoming connection " + "from "
                        + s.toString());

            } catch (Throwable e) {
                if (stop) {
                    logger.info("NameServerClient: thread dying");
                    try {
                        serverSocket.close();
                    } catch (IOException e1) {
                        /* do nothing */
                    }
                    break;
                }
                if (needsUpcalls) {
                    synchronized (this) {
                        stop = true;
                        left = true;
                        notifyAll();
                    }
                }
                throw new RuntimeException("NameServerClient: got an error", e);
            }

            byte opcode = 0;
            DataInputStream in = null;
            DataOutputStream out = null;

            try {
                in = new DataInputStream(new BufferedInputStream(s
                        .getInputStream()));
                int count;
                IbisIdentifier[] ids;

                opcode = in.readByte();
                logger.debug("NameServerClient: opcode " + opcode);

                switch (opcode) {
                case (IBIS_PING): {
                    out = new DataOutputStream(new BufferedOutputStream(s
                            .getOutputStream()));
                    out.writeUTF(poolName);
                    out.writeUTF(id.myId);
                }
                    break;

                case (IBIS_JOIN):
                case (IBIS_LEAVE):
                case (IBIS_DEAD):
                case (IBIS_MUSTLEAVE):
                    count = in.readInt();
                    ids = new IbisIdentifier[count];
                    for (int i = 0; i < count; i++) {
                        ids[i] = new IbisIdentifier(in);
                        if (opcode == IBIS_LEAVE && ids[i].equals(this.id)) {
                            // received an ack from the nameserver that I left.
                            logger.info("NameServerClient: thread dying");
                            stop = true;
                        }
                    }
                    if (stop) {
                        synchronized (this) {
                            left = true;
                            notifyAll();
                        }
                    } else {
                        addMessage(opcode, ids);
                    }
                    break;

                default:
                    logger.error("NameServerClient: got an illegal "
                            + "opcode " + opcode);
                }
            } catch (Exception e1) {
                if (!stop) {
                    logger.error("Got an exception in "
                            + "NameServerClient.run " + "(opcode = " + opcode
                            + ")", e1);
                }
            } finally {
                NameServer.closeConnection(in, out, s);
            }
        }
        logger.debug("NameServerClient: thread stopped");
    }

    public ibis.ipl.IbisIdentifier elect(String election) throws IOException {
        return electionClient.elect(election, id);
    }

    public ibis.ipl.IbisIdentifier getElectionResult(String election)
            throws IOException {
        return electionClient.elect(election, null);
    }
}