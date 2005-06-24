/* $Id$ */

package ibis.connect.plainSocketFactories;

import ibis.connect.ConnectionProperties;
import ibis.connect.IbisServerSocket;
import ibis.connect.IbisSocket;
import ibis.util.IPUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

// SocketType descriptor for PortRange sockets
// -------------------------------------------
public class PortRangeSocketFactory extends PlainTCPSocketFactory {

    static Logger logger = ibis.util.GetLogger.getLogger(PortRangeSocketFactory.class
            .getName());

    private static int portNumber;

    private static int startRange;

    private static int endRange;

    private static PlainTCPSocketFactory plainSocketType;

    static {
        plainSocketType = new PlainTCPSocketFactory();
        Properties p = System.getProperties();
        String range = p.getProperty(ConnectionProperties.PORT_RANGE);
        if (range != null && range.length() != 0) {
            try {
                int pos = range.indexOf('-');
                if (pos == -1) {
                    pos = range.indexOf(',');
                }
                String from = range.substring(0, pos);
                String to = range.substring(pos + 1, range.length());
                startRange = Integer.parseInt(from);
                endRange = Integer.parseInt(to);
                portNumber = startRange;
                logger.debug("# PortRange: ports = " + startRange + "-"
                        + endRange);

            } catch (Exception e) {
                throw new Error(
                        "# PortRange : specify a port range property: "
                                + "ibis.connect.PORT_RANGE=3000-4000 or ibis.connect.PORT_RANGE=3000,4000");
            }
        }
    }

    public PortRangeSocketFactory() {
    }

    public IbisSocket createClientSocket(InetAddress destAddr, int destPort,
            InetAddress localAddr, int localPort, int timeout, Map properties)
            throws IOException {
        if(localPort == 0) {
            localPort = allocLocalPort();
        }
        return plainSocketType.createClientSocket(destAddr, destPort,
                localAddr, localPort, timeout, properties);
    }

    public IbisSocket createClientSocket(InetAddress remoteAddress, int remotePort, Map p)
            throws IOException {
            int localPort = allocLocalPort();
            InetAddress localAddress = IPUtils.getLocalHostAddress();

            return plainSocketType.createClientSocket(remoteAddress, remotePort, localAddress, localPort, 0, p);
    }

    public IbisServerSocket createServerSocket(InetSocketAddress addr,
            int backlog, Map p) throws IOException {
        if(addr.getPort() == 0) {
            addr = new InetSocketAddress(addr.getAddress(), allocLocalPort());
        }
        return plainSocketType.createServerSocket(addr, backlog, p);
    }

    public IbisSocket createBrokeredSocket(InputStream in, OutputStream out,
            boolean hintIsServer, Map p) throws IOException {
        // since createBrokeredSocket uses the above create methods for client and server sockets,
        // we can just call super.
        return super.createBrokeredSocket(in, out, hintIsServer, p);
    }

    private synchronized int allocLocalPort() {
        if(startRange ==0 && endRange == 0) {
            return 0;
        }
        
        int res = portNumber++;
        if (portNumber >= endRange) {
            portNumber = startRange;
            logger.warn("WARNING, used more ports than available within "
                    + "firewall range. Wrapping around");
        }
        return res;
    }
}