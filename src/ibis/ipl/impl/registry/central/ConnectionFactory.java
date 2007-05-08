package ibis.ipl.impl.registry.central;

import ibis.ipl.impl.IbisIdentifier;
import ibis.ipl.impl.registry.RegistryProperties;
import ibis.util.IPUtils;
import ibis.util.TypedProperties;
import ibis.util.io.Conversion;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.apache.log4j.Logger;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import ibis.server.Client;

final class ConnectionFactory {
    private static final int CONNECTION_BACKLOG = 50;

    private static final Logger logger =
            Logger.getLogger(ConnectionFactory.class);

    private final boolean smart;

    // smart socket fields

    private final VirtualSocketFactory virtualSocketFactory;

    private final VirtualServerSocket virtualServerSocket;

    private final VirtualSocketAddress virtualServerAddress;

    private final ServerSocket plainServerSocket;

    private final InetAddress plainLocalAddress;

    private final InetSocketAddress plainServerAddress;

    private static InetSocketAddress plainAddressFromBytes(byte[] bytes)
            throws IOException {
        int port = Conversion.defaultConversion.byte2int(bytes, 0);
        byte[] addressBytes = new byte[bytes.length - 4];
        System.arraycopy(bytes, 4, addressBytes, 0, addressBytes.length);
        InetAddress address = InetAddress.getByAddress(addressBytes);

        return new InetSocketAddress(address, port);
    }

    private static byte[] plainAddressToBytes(InetAddress address, int port) {
        byte[] addressBytes = address.getAddress();
        byte[] result = new byte[addressBytes.length + 4];

        System.arraycopy(addressBytes, 0, result, 4, addressBytes.length);
        Conversion.defaultConversion.int2byte(port, result, 0);

        return result;
    }

    ConnectionFactory(TypedProperties properties) throws IOException {
        smart =
                properties.getBooleanProperty(
                        RegistryProperties.CENTRAL_SMARTSOCKETS, true);

        if (smart) {
            plainServerSocket = null;
            plainServerAddress = null;
            plainLocalAddress = null;

            try {
                virtualSocketFactory = Client.getFactory(properties);
            } catch (InitializationException e) {
                throw new IOException("Could not create socket factory: " + e);
            }

            virtualServerSocket =
                    virtualSocketFactory.createServerSocket(0,
                            CONNECTION_BACKLOG, null);

            virtualServerAddress =
                    Client.getServiceAddress(Server.VIRTUAL_PORT, properties);

            logger.debug("local address = "
                    + virtualServerSocket.getLocalSocketAddress());
            logger.debug("server address = " + virtualServerAddress);
        } else {
            virtualSocketFactory = null;
            virtualServerSocket = null;
            virtualServerAddress = null;

            plainServerSocket = new ServerSocket(0, CONNECTION_BACKLOG);
            plainLocalAddress = IPUtils.getLocalHostAddress();

            String serverString =
                    properties.getProperty(RegistryProperties.SERVER_ADDRESS);

            int defaultServerPort =
                    properties.getIntProperty(RegistryProperties.SERVER_PORT);

            if (serverString != null) {
                try {
                    String[] addressParts = serverString.split(":", 2);

                    String serverHost = addressParts[0];
                    int serverPort;

                    if (addressParts.length < 2) {
                        serverPort = defaultServerPort;
                    } else {
                        serverPort = Integer.parseInt(addressParts[1]);
                    }

                    plainServerAddress =
                            new InetSocketAddress(serverHost, serverPort);
                } catch (Throwable t) {
                    throw new IOException("illegal server address ("
                            + serverString + ") : " + t.getMessage());
                }
            } else {
                plainServerAddress = null;
            }
        }
    }

    ConnectionFactory(int port, String hubAddress) throws IOException {
        this.smart = false;

        if (port < 0) {
            throw new IOException("port number cannot be negative " + port);
        }
        logger.debug("port = " + port);

        virtualSocketFactory = null;
        virtualServerSocket = null;
        virtualServerAddress = null;

        plainServerSocket = new ServerSocket(port, CONNECTION_BACKLOG);
        plainLocalAddress = IPUtils.getLocalHostAddress();
        plainServerAddress = null;
    }

    ConnectionFactory(VirtualSocketFactory factory, int virtualPort)
            throws IOException {
        this.smart = true;

        plainServerSocket = null;
        plainServerAddress = null;
        plainLocalAddress = null;

        virtualSocketFactory = factory;

        virtualServerSocket =
                virtualSocketFactory.createServerSocket(virtualPort,
                        CONNECTION_BACKLOG, null);

        virtualServerAddress = null;

        logger.debug("local address = "
                + virtualServerSocket.getLocalSocketAddress());
        logger.debug("server address = " + virtualServerAddress);
    }

    private static VirtualSocketAddress createAddressFromString(
            String serverString, int defaultPort) throws IOException {

        if (serverString == null) {
            return null;
        }

        VirtualSocketAddress serverAddress = null;

        // first, try to create a complete virtual socket address
        try {
            serverAddress = new VirtualSocketAddress(serverString);
        } catch (IllegalArgumentException e) {
            logger.debug("could not create server address", e);
        }

        // maybe it is a socketaddressset without a virtual port?
        if (serverAddress == null) {
            try {
                DirectSocketAddress directAddress =
                        DirectSocketAddress.getByAddress(serverString);
                int[] ports = directAddress.getPorts(false);
                if (ports.length == 0) {
                    throw new IOException(
                            "cannot determine port from server address: "
                                    + serverString);
                }
                int port = ports[0];
                for (int p : ports) {
                    if (p != port) {
                        throw new IOException(
                                "cannot determine port from server address: "
                                        + serverString);
                    }
                }
                serverAddress = new VirtualSocketAddress(directAddress, port);
            } catch (IllegalArgumentException e) {
                logger.debug("could not create server address", e);
            }
        }

        // maybe it is only a hostname?
        if (serverAddress == null) {
            try {
                DirectSocketAddress directAddress =
                        DirectSocketAddress.getByAddress(serverString,
                                defaultPort);
                serverAddress =
                        new VirtualSocketAddress(directAddress, defaultPort);
            } catch (Exception e) {
                logger.debug("could not create server address", e);
            }
        }

        if (serverAddress == null) {
            throw new IOException("Invalid server address: " + serverString);
        }

        return serverAddress;
    }

    Connection accept() throws IOException {
        if (smart) {
            return new Connection(virtualServerSocket);
        } else {
            return new Connection(plainServerSocket);
        }
    }

    Connection connect(IbisIdentifier ibis, byte opcode, int timeout)
            throws IOException {
        if (smart) {
            VirtualSocketAddress address =
                    VirtualSocketAddress.fromBytes(ibis.getRegistryData(), 0);
            return new Connection(address, virtualSocketFactory, opcode,
                    timeout, false);
        } else {
            InetSocketAddress address =
                    plainAddressFromBytes(ibis.getRegistryData());

            return new Connection(address, opcode, timeout, false);
        }
    }

    void end() {
        try {
            if (smart) {
                virtualServerSocket.close();
            } else {
                plainServerSocket.close();
            }
        } catch (IOException e) {
            // IGNORE
        }
    }

    byte[] getLocalAddress() {
        if (smart) {
            return virtualServerSocket.getLocalSocketAddress().toBytes();
        } else {
            return plainAddressToBytes(plainLocalAddress, plainServerSocket
                    .getLocalPort());
        }
    }

    String getAddressString() {
        if (smart) {
            return virtualServerSocket.getLocalSocketAddress().toString();
        } else {
            return plainLocalAddress.getHostAddress() + ":"
                    + plainServerSocket.getLocalPort();
        }
    }

    Connection connectToServer(byte opcode, int timeout) throws IOException {
        if (smart) {
            if (virtualServerAddress == null) {
                throw new IOException(
                        "could not connect to server, address not specified");
            }
            return new Connection(virtualServerAddress, virtualSocketFactory,
                    opcode, timeout, true);
        } else {
            if (plainServerAddress == null) {
                throw new IOException(
                        "could not connect to server, address not specified");
            }
            return new Connection(plainServerAddress, opcode, timeout, true);
        }
    }

    boolean serverIsLocalHost() {
        if (smart) {
            if (virtualServerAddress == null) {
                return false;
            }

            return virtualServerSocket.getLocalSocketAddress().machine()
                    .sameMachine(virtualServerAddress.machine());
        } else {
            if (plainServerAddress == null) {
                return false;
            }
            if (plainServerAddress.getAddress().isLoopbackAddress()) {
                return true;
            }
            return plainServerAddress.equals(plainLocalAddress);
        }
    }

    int getServerPort() {
        if (smart) {
            return virtualServerAddress.port();
        } else {
            return plainServerAddress.getPort();
        }
    }
}
