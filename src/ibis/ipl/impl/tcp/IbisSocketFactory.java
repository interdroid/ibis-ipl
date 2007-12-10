package ibis.ipl.impl.tcp;

import ibis.ipl.impl.IbisIdentifier;
import ibis.server.Client;
import ibis.smartsockets.hub.servicelink.ServiceLink;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualSocketFactory;
import ibis.util.IPUtils;
import ibis.util.TypedProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Provides a factory that can either produce smartsockets sockets or
 * ordinary sockets.
 */
class IbisSocketFactory {

    private final VirtualSocketFactory factory;

    IbisSocketFactory(TypedProperties props) throws IOException {
        boolean useSmartsockets = props.getBooleanProperty(
                "ibis.ipl.impl.tcp.smartsockets", true);
        if (useSmartsockets) {
            TcpIbis.logger.info("Using smartsockets TcpIbis");
            try {
//                factory = VirtualSocketFactory.getOrCreateSocketFactory(
//                        "ibis", props, true);
              factory = Client.getFactory(props);
            } catch(InitializationException e) {
                throw new IOException("Failed to create socket factory");
            }
        } else {
            TcpIbis.logger.info("Using plain TcpIbis");
            factory = null;
        }
    }

    void setIdent(IbisIdentifier id) {
        if (factory != null) {
            try {
                ServiceLink sl = factory.getServiceLink();
                if (sl != null) {
                    sl.registerProperty("smartsockets.viz", "I^"
                            + id.name() + "," + id.location().toString());
                    // sl.registerProperty("ibis", id.toString());
                }
            } catch(Throwable e) {
                // ignored
            }
        }
    }

    IbisServerSocket createServerSocket(int port, int backlog, boolean retry,
            Properties properties) throws IOException {
        if (factory != null) {
            return new IbisServerSocket(factory.createServerSocket(port,
                        backlog, retry, properties));
        } else {
            ServerSocket server = new ServerSocket();
            InetSocketAddress local = new InetSocketAddress(
                    IPUtils.getLocalHostAddress(), port);
            server.bind(local, backlog);
            return new IbisServerSocket(server);
        }
    }

    IbisSocket createClientSocket(IbisSocketAddress addr, int timeout,
            boolean fillTimeout, Map<String, String> properties) 
        throws IOException {
        
        if (factory != null) {
            HashMap<String, Object> h = new HashMap<String, Object>();
            h.putAll(properties);
            return new IbisSocket(factory.createClientSocket(
                        addr.virtualAddress, timeout, fillTimeout, h));
        }
        Socket s = new Socket();
        
        s.connect(addr.address, timeout);
        return new IbisSocket(s);
    }

    void printStatistics(String s) {
        if (factory != null) {
            factory.printStatistics(s);
        }
    }
}
