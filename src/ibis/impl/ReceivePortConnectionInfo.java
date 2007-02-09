/* $Id:$ */

package ibis.impl;

import ibis.io.DataInputStream;
import ibis.io.SerializationBase;
import ibis.io.SerializationInput;
import ibis.util.GetLogger;

import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * This class represents the information about a particular sendport/receiveport
 * connection, on the receiver side.
 */
public class ReceivePortConnectionInfo {

    /** Debugging. */
    protected static final Logger logger
            = GetLogger.getLogger("ibis.impl.ReceivePortConnectionInfo");

    /** Identifies the sendport side of the connection. */
    protected final SendPortIdentifier origin;

    /** The serialization input stream of the connection. */
    protected SerializationInput in;

    /** The receiveport of the connection. */
    protected ReceivePort port;

    /** The message that arrives on this connection. */
    protected ReadMessage message;

    /**
     * The underlying data stream for this connection.
     * The serialization steam lies on top of this.
     */
    protected DataInputStream dataIn;

    /**
     * Constructs a new <code>ReceivePortConnectionInfo</code> with the
     * specified parameters.
     * @param origin identifies the sendport of this connection.
     * @param port the receiveport.
     * @param dataIn the inputstream on which a serialization input stream
     * can be built.
     * @exception IOException is thrown in case of trouble.
     */
    public ReceivePortConnectionInfo(SendPortIdentifier origin,
            ReceivePort port, DataInputStream dataIn) throws IOException {
        this.origin = origin;
        this.port = port;
        this.dataIn = dataIn;
        newStream();
        port.addInfo(origin, this);
    }

    /**
     * Returns the number of bytes read from the data stream.
     * @return the number of bytes.
     */
    protected long bytesRead() {
        return dataIn.bytesRead();
    }

    /**
     * This method must be called each time a connected sendport adds a new
     * connection. This new connection may either be to the current receiveport,
     * or to another one. In both cases, the serialization stream must be
     * recreated.
     * @exception IOException is thrown in case of trouble.
     */
    protected void newStream() throws IOException {
        if (in != null) {
            in.close();
        }
        in = SerializationBase.createSerializationInput(port.serialization,
                dataIn);
        message = new ReadMessage(in, this);
    }

    /**
     * This method closes the connection, as the result of the specified
     * exception. Implementations may need to redefine this method 
     * @param e the exception.
     */
    protected void close(Throwable e) {
        try {
            in.close();
        } catch(Throwable z) {
            // ignore
        }
        in = null;
        if (logger.isDebugEnabled()) {
            logger.debug(port.name + ": connection with " + origin
                    + " closing", e);
        }
        port.lostConnection(origin, e);
    }

    /**
     * This method gets called when the upcall for the message explicitly
     * called {@link ReadMessage#finish()}.
     * The default implementation just allocates a new message.
     */
    protected void upcallCalledFinish() {
        message = new ReadMessage(in, this);
        logger.debug(port.name + ": new connection handler for " + origin
                + ", finish called from upcall");
    }
}
