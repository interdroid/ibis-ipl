/* $Id$ */

package ibis.ipl;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

/**
 * The starting point of all Ibis communication, created using the {@link ibis.ipl.IbisFactory}.
 */
public interface Ibis extends Managable {

    /**
     * When running closed-world, returns the total number of Ibis instances
     * in the pool.
     * @return the number of Ibis instances
     * @exception NumberFormatException is thrown when the property
     *   ibis.pool.total_hosts is not defined or does not represent a number.
     * @exception IbisConfigurationException is thrown when this is not a
     * closed-world run.
     */
    public int getPoolSize();

    /**
     * When running closed-world, wait for all Ibis instances
     * in the pool.
     * @exception IbisConfigurationException is thrown when this is not a
     * closed-world run, or when registry events are not enabled yet.
     */
    public void waitForAll();

    /**
     * Allows reception of {@link ibis.ipl.RegistryEventHandler RegistryEventHandler}
     * upcalls. Registry events are saved up until the event handler is enabled,
     * and are then delivered, one by one. Ibis instances are always started with
     * the registry event handler disabled, so that this method must be called to
     * allow for the reception of registry handler upcalls.
     */
    public void enableRegistryEvents();

    /**
     * Disables reception of
     * {@link ibis.ipl.RegistryEventHandler RegistryEventHandler} upcalls.
     * Registry events will be saved until the handler is enabled again, and
     * then be delivered, one by one.
     */
    public void disableRegistryEvents();

    /**
     * Returns all Ibis recources to the system.
     * The Ibis instance also deregisters itself from the registry. As a
     * result, other Ibis instances may receive a 
     * {@link RegistryEventHandler#left(IbisIdentifier)} upcall.
     * @exception IOException is thrown when an error occurs.
     */
    public void end() throws IOException;

    /** 
     * Returns the Ibis {@linkplain ibis.ipl.Registry Registry}.
     * @return the Ibis registry.
     */
    public Registry registry();

    /**
     * Polls the network for new messages.
     * A message upcall may be generated by the poll. 
     * There is one poll for the entire Ibis, as this
     * can sometimes be implemented more efficiently than polling per
     * port. Polling per port is provided in the receiveport itself.
     * @exception IOException is thrown when a communication error occurs.
     */
    public void poll() throws IOException;

    /**
     * Returns an Ibis {@linkplain ibis.ipl.IbisIdentifier identifier} for
     * this Ibis instance.
     * An Ibis identifier identifies an Ibis instance in the network.
     * @return the Ibis identifier of this Ibis instance.
     */
    public IbisIdentifier identifier();

    /**
     * Returns the current Ibis version.
     * @return the ibis version.
     */
    public String getVersion();

    /**
     * May print Ibis-implementation-specific statistics.
     * @param out the stream to print to.
     */
    public void printStatistics(PrintStream out);

    /**
     * Returns the properties as provided when instantiating Ibis.
     * @return the properties.
     */
    public Properties properties();

    /**
     * Returns the Ibis instances that joined the pool.
     * Returns the changes since the last joinedIbises call,
     * or, if this is the first call, all Ibis instances that joined.
     * This call only works if this Ibis is configured to support
     * registry downcalls.
     * If no Ibis instances joined, an array with 0 entries is returned.
     * @exception IbisConfigurationException is thrown when the port was
     * not configured to support registry downcalls.
     * @return the joined Ibises.
     */
    public IbisIdentifier[] joinedIbises();

    /**
     * Returns the Ibis instances that left the pool.
     * Returns the changes since the last leftIbises call,
     * or, if this is the first call, all Ibis instances that left.
     * This call only works if this Ibis is configured to support
     * registry downcalls.
     * If no Ibis instances left, an array with 0 entries is returned.
     * @exception IbisConfigurationException is thrown when ibis was
     * not configured to support registr downcalls.
     * @return the left Ibises.
     */
    public IbisIdentifier[] leftIbises();
    
    /**
     * Returns the Ibis instances that died.
     * Returns the changes since the last diedIbises call,
     * or, if this is the first call, all Ibis instances that died.
     * This call only works if this Ibis is configured to support
     * registry downcalls.
     * If no Ibis instances died, an array with 0 entries is returned.
     * @exception IbisConfigurationException is thrown when ibis was
     * not configured to support registry downcalls.
     * @return the Ibises that died.
     */
    public IbisIdentifier[] diedIbises();

    /**
     * Returns the signals received.
     * Returns the changes since the last receivedSignals call,
     * or, if this is the first call, all signals received so far.
     * This call only works if this Ibis is configured to support
     * registry downcalls.
     * If no signals were received, an array with 0 entries is returned.
     * @exception IbisConfigurationException is thrown when ibis was
     * not configured to support registry downcalls.
     * @return the received signals.
     */
    public String[] receivedSignals();

    /**
     * Creates a anonymous {@link SendPort} of the specified port type.
     * 
     * @param portType the port type.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     */
    public SendPort createSendPort(PortType portType) throws IOException;

    /**
     * Creates a named {@link SendPort} of the specified port type.
     * The name does not have to be unique.
     *
     * @param portType the port type.
     * @param sendPortName the name of this sendport.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public SendPort createSendPort(PortType portType, String sendPortName)
            throws IOException;

    /** 
     * Creates a named {@link SendPort} of the specified port type.
     * The name does not have to be unique.
     * When a connection is lost, a ConnectUpcall is performed.
     *
     * @param portType the port type.
     * @param sendPortName the name of this sendport.
     * @param sendPortDisconnectUpcall object implementing the
     * {@link SendPortDisconnectUpcall#lostConnection(SendPort,
     * ReceivePortIdentifier, Throwable)} method.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public SendPort createSendPort(PortType portType, String sendPortName,
        SendPortDisconnectUpcall sendPortDisconnectUpcall) throws IOException;

    /**
     * Creates a named {@link ReceivePort} of the specified port type.
     * with explicit receipt communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     *
     * @param portType the port type.
     * @param receivePortName the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(PortType portType, String receivePortName)
        throws IOException;

    /** 
     * Creates a named {@link ReceivePort} of the specified port type.
     * with upcall-based communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     * Upcalls will not be invoked until
     * {@link ReceivePort#enableMessageUpcalls()} has been called.
     * This is done to avoid message upcalls during initialization.
     *
     * @param portType the port type.
     * @param receivePortName the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @param messageUpcall the upcall handler.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(PortType portType, String receivePortName,
            MessageUpcall messageUpcall) throws IOException;

    /** 
     * Creates a named {@link ReceivePort} of the specified port type.
     * with explicit receipt communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     * When a new connection request arrives, or when a connection is lost,
     * a ConnectUpcall is performed.
     *
     * @param portType the port type.
     * @param receivePortName the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @param receivePortConnectUpcall object implementing <code>gotConnection</code>() and
     * <code>lostConnection</code>() upcalls.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(PortType portType, String receivePortName,
            ReceivePortConnectUpcall receivePortConnectUpcall) throws IOException;

    /** 
     * Creates a named {@link ReceivePort} of the specified port type.
     * with upcall-based communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     * When a new connection request arrives, or when a connection is lost,
     * a ConnectUpcall is performed.
     * Upcalls will not be invoked until
     * {@link ReceivePort#enableMessageUpcalls()} has been called.
     * This is done to avoid message upcalls during initialization.
     *
     * @param portType the port type.
     * @param receivePortName the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @param messageUpcall the upcall handler.
     * @param receivePortConnectUpcall object implementing <code>gotConnection</code>() and
     * <code>lostConnection</code>() upcalls.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    
    public ReceivePort createReceivePort(PortType portType, String receivePortName,
            MessageUpcall messageUpcall, ReceivePortConnectUpcall receivePortConnectUpcall)
            throws IOException;
}
