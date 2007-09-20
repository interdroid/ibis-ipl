
package ibis.ipl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class describes the capabilities of an ibis instance.
 * Combined with a list of {@link PortType} it is
 * used to select a particular Ibis implementation.
 * See the
 * {@link IbisFactory#createIbis(IbisCapabilities, Properties, boolean, RegistryEventHandler, PortType...) createIbis}
 * method from {@link IbisFactory}.       
 */
public final class IbisCapabilities extends CapabilitySet {

    /**
     * Capability, set when the Ibises that can join the pool are
     * determined at the start of the run. This enables the methods
     * {@link Registry#getPoolSize()} and {@link Registry#waitForAll()}.
     */
    public final static String CLOSEDWORLD = "closedWorld";
    
    /** Capability, indicating that signals are supported. */
    public final static String SIGNALS = "signals";

    /**
     * Capability, indicating that elections are supported
     * but don't have to be reliable, always give the same result, etc.
     */
    public final static String ELECTIONS_UNRELIABLE = "elections.unreliable";

    /**
     * Capability, indicating that elections are supported, reliable,
     * and give consistent results.
     */
    public final static String ELECTIONS_STRICT = "elections.strict";
    
    /**
     * Capability indicating that joins/leaves don't have to be reliable,
     * in order, etc.
     */
    public final static String MEMBERSHIP_UNRELIABLE
            = "membership.unreliable";
    
    /**
     * Capability indicating that joins/leaves are totally ordered.
     * Implies reliability.
     */
    public final static String MEMBERSHIP_TOTALLY_ORDERED
            = "membership.totally.ordered";
    
    /** Capability indicating an Ibis that can deal with malleability. */
    public final static String MALLEABLE = "malleable";
    
    /** 
     * Constructor for an IbisCapabilities object.
     * @param capabilities the capabilities.
     */
    public IbisCapabilities(String... capabilities) {
        super(capabilities);
    }
    
    /**
     * Constructs an IbisCapabilities object from the specified properties.
     * @param properties the properties.
     */
    protected IbisCapabilities(Properties properties) {
        super(properties);
    }

    /**
     * Constructs an IbisCapabilities from the specified capabilityset.
     * @param capabilitySet the capabilityset.
     */
    protected IbisCapabilities(CapabilitySet capabilitySet) {
         super(capabilitySet);
    }
    
    /**
     * Reads and returns the capabilities from the specified file name, which is
     * searched for in the classpath.
     * @param capabilityFileName the file name.
     * @exception IOException is thrown when an IO error occurs.
     */
    public static IbisCapabilities load(String capabilityFileName) throws IOException {
        InputStream input
            = ClassLoader.getSystemClassLoader().getResourceAsStream(capabilityFileName);
        if (input == null) {
            throw new IOException("Could not open " + capabilityFileName);
        }
        return load(input);
    }

    /**
     * Reads and returns the capabilities from the specified input stream.
     * @param input the input stream.
     * @exception IOException is thrown when an IO error occurs.
     */
    public static IbisCapabilities load(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);
        input.close();
        return new IbisCapabilities(properties);
    }
}
