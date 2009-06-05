package ibis.ipl.server;

import ibis.ipl.IbisProperties;
import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;
import ibis.util.TypedProperties;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience class to retrieve information on the server, and create a
 * suitable VirtualSocketFactory.
 */
public class Client {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    private Client() {
        // DO NOT USE
    }

  
    private static DirectSocketAddress createAddressFromString(
            String serverString, int defaultPort) throws ConfigurationException {

        if (serverString == null) {
            throw new ConfigurationException("serverString undefined");
        }

        // maybe it is a DirectSocketAddress?
        try {
            return DirectSocketAddress.getByAddress(serverString);
        } catch (Throwable e) {
            // IGNORE
        }

        Throwable throwable = null;
        // or only a host address
        try {
            return DirectSocketAddress.getByAddress(serverString, defaultPort);
        } catch (Throwable e) {
            throwable = e;
            // IGNORE
        }

        throw new ConfigurationException(
                "could not create server address from given string: "
                        + serverString, throwable);
    }

    /**
     * Get the address of a service running on a given port
     * 
     * @param port
     *            the port the service is running on
     * @param properties
     *            object containing any server properties needed (such as the
     *            servers address)
     */
    public static VirtualSocketAddress getServiceAddress(int port,
            Properties properties) throws ConfigurationException {
        TypedProperties typedProperties = ServerProperties
                .getHardcodedProperties();
        typedProperties.addProperties(properties);

        String serverAddressString = typedProperties
                .getProperty(IbisProperties.SERVER_ADDRESS);
        if (serverAddressString == null || serverAddressString.equals("")) {
            throw new ConfigurationException(IbisProperties.SERVER_ADDRESS
                    + " undefined, cannot locate server");
        }

        logger.debug("server address = \"" + serverAddressString + "\"");

        int defaultPort = typedProperties.getIntProperty(ServerProperties.PORT);

        DirectSocketAddress serverMachine = createAddressFromString(
                serverAddressString, defaultPort);

        if (serverMachine == null) {
            throw new ConfigurationException("cannot get address of server");
        }

        return new VirtualSocketAddress(serverMachine, port, serverMachine,
                null);
    }

    public static synchronized VirtualSocketFactory getFactory(
            TypedProperties typedProperties) throws ConfigurationException,
            IOException {
        return getFactory(typedProperties, 0);
    }

    public static synchronized VirtualSocketFactory getFactory(
            TypedProperties typedProperties, int port)
            throws ConfigurationException, IOException {

        String hubs = typedProperties.getProperty(IbisProperties.HUB_ADDRESSES);

        // did the server also start a hub?
        boolean serverIsHub = typedProperties
                .getBooleanProperty(IbisProperties.SERVER_IS_HUB);

        String server = typedProperties
                .getProperty(IbisProperties.SERVER_ADDRESS);
        if (server != null && !server.equals("") && serverIsHub) {
            // add server to hub addresses
            DirectSocketAddress serverAddress = createAddressFromString(server,
                    typedProperties.getIntProperty(ServerProperties.PORT,
                            ServerProperties.DEFAULT_PORT));
            if (hubs == null || hubs.equals("")) {
                hubs = serverAddress.toString();
            } else {
                hubs = hubs + "," + serverAddress.toString();
            }
        }

        Properties smartProperties = new Properties();

        if (port > 0) {
            smartProperties.put(SmartSocketsProperties.PORT_RANGE, Integer
                    .toString(port));
        }

        if (hubs != null) {
            smartProperties.put(SmartSocketsProperties.HUB_ADDRESSES, hubs);
        }

        try {
            VirtualSocketFactory result = VirtualSocketFactory
                    .getSocketFactory("ibis");

            if (result == null) {
                result = VirtualSocketFactory.getOrCreateSocketFactory("ibis",
                        smartProperties, true);
            } else if (hubs != null) {
                result.addHubs(hubs.split(","));
            }
            return result;
        } catch (InitializationException e) {
            throw new IOException(e.getMessage());
        }
    }

}