/* $Id$ */

package ibis.satin.impl;

import ibis.util.TypedProperties;

import org.apache.log4j.Logger;

/**
 * Constants for the configuration of Satin. This interface is public because it
 * is also used in code generated by the Satin frontend.
 */

public interface Config {

    static final TypedProperties properties = TypedProperties.getDefaultConfigProperties();
    
    static final String PROPERTY_PREFIX = "satin.";

    static final String s_asserts = PROPERTY_PREFIX + "asserts";

    static final String s_queue_steals = PROPERTY_PREFIX + "queueSteals";
    
    static final String s_closed = PROPERTY_PREFIX + "closed";

    static final String s_localports = PROPERTY_PREFIX + "localPorts";

    static final String s_close_connections
            = PROPERTY_PREFIX + "closeConnections";

    static final String s_max_connections = PROPERTY_PREFIX + "maxConnections";

    static final String s_stats = PROPERTY_PREFIX + "stats";

    static final String s_detailed_stats = PROPERTY_PREFIX + "detailedStats";

    static final String s_alg = PROPERTY_PREFIX + "alg";

    static final String s_dump = PROPERTY_PREFIX + "dump";

    static final String s_in_latency = PROPERTY_PREFIX + "messagesInLatency";

    static final String s_so_delay = PROPERTY_PREFIX + "so.delay";

    static final String s_so_size = PROPERTY_PREFIX + "so.size";

    static final String s_so_lrmc = PROPERTY_PREFIX + "so.lrmc";

    static final String s_ft_naive = PROPERTY_PREFIX + "ft.naive";

    static final String s_ft_connectTimeout
            = PROPERTY_PREFIX + "ft.connectTimeout";

    static final String s_masterhost = PROPERTY_PREFIX + "masterHost";

    static final String s_delete_time = PROPERTY_PREFIX + "deleteTime";

    static final String s_steal_wait_timeout
            = PROPERTY_PREFIX + "stealWaitTimeout";
    
    static final String s_delete_cluster_time = PROPERTY_PREFIX
        + "deleteClusterTime";

    static final String s_kill_time = PROPERTY_PREFIX + "killTime";

    static final String[] sysprops = { s_stats, s_queue_steals,
        s_detailed_stats, s_closed, s_localports, s_asserts,
        s_ft_naive, s_ft_connectTimeout, s_masterhost, s_in_latency,
        s_delete_time, s_delete_cluster_time, s_kill_time, s_dump, s_so_delay,
        s_so_size, s_alg, s_so_lrmc, s_close_connections, s_max_connections,
        s_steal_wait_timeout };

    /** Enable or disable asserts. */
    static final boolean ASSERTS = properties.getBooleanProperty(s_asserts, true);

    /** True if the node should dump its datastructures during shutdown. */
    static final boolean DUMP = properties.getBooleanProperty(s_dump, false);

    /** Enable this if Satin should print statistics at the end. */
    static final boolean STATS = properties.getBooleanProperty(s_stats, true);

    /** Enable this if Satin should print statistics per machine at the end. */
    static final boolean DETAILED_STATS = properties.getBooleanProperty(
        s_detailed_stats, false);

    /**
     * Enable this if satin should run with a closed world: no nodes can join
     * or leave.
     */
    static final boolean CLOSED = properties.getBooleanProperty(s_closed, false);

    /** Determines master hostname. */
    static final String MASTER_HOST = properties.getProperty(s_masterhost);

    /** Determines which load-balancing algorithm is used. */
    static final String SUPPLIED_ALG = properties.getProperty(s_alg);

    /**
     * Fault tolerance with restarting crashed jobs, but without the global
     * result table.
     */
    static final boolean FT_NAIVE = properties.getBooleanProperty(s_ft_naive, false);

    /** Enable or disable an optimization for handling delayed messages. */
    static final boolean HANDLE_MESSAGES_IN_LATENCY = properties.getBooleanProperty(
            s_in_latency, false);

    /**
     * Timeout for connecting to other nodes.
     */
    public static final long CONNECT_TIMEOUT = properties.getLongProperty(
        s_ft_connectTimeout, 60) * 1000L;

    /** Timeout for waiting on a steal reply from another node. */
    public static final long STEAL_WAIT_TIMEOUT = properties.getLongProperty(
        s_steal_wait_timeout, CONNECT_TIMEOUT * 2 + 20 * 1000L);

    /**
     * Maximum time that messages may be buffered for message combining.
     * If > 0, it is used for combining shared objects invocations.
     * setting this to 0 disables message combining.
     */
    static final int SO_MAX_INVOCATION_DELAY = properties.getIntProperty(
            s_so_delay, 0);

    /** 
     * The maximum message size if message combining is used for SO Invocations.
     */
    static final int SO_MAX_MESSAGE_SIZE = properties.getIntProperty(s_so_size,
            64 * 1024);

    /** Enable or disable label routing multicast for shared objects . */
    static final boolean LABEL_ROUTING_MCAST = properties.getBooleanProperty(
            s_so_lrmc, true);

    /** Used in automatic ft tests */
    static final int DELETE_TIME = properties.getIntProperty(s_delete_time, 0);

    /** Used in automatic ft tests */
    static final int DELETE_CLUSTER_TIME = properties.getIntProperty(
            s_delete_cluster_time, 0);

    /** Used in automatic ft tests */
    static final int KILL_TIME = properties.getIntProperty(s_kill_time, 0);

    /**
     * Enable or disable using a seperate queue for work steal requests to 
     * avoid thread creation.
     */
    static final boolean QUEUE_STEALS = properties.getBooleanProperty(s_queue_steals,
            true);

    /** Close connections after use. Used for scalability. */
    static final boolean CLOSE_CONNECTIONS = properties.getBooleanProperty(
            s_close_connections, true); 

    /** When using CLOSE_CONNECTIONS, keep open MAX_CONNECTIONS connections. */
    static final int MAX_CONNECTIONS = properties.getIntProperty(s_max_connections,
            64); 

    /** Logger for communication. */
    public static final Logger commLogger = Logger.getLogger("ibis.satin.comm");

    /** Logger for job stealing. */
    public static final Logger stealLogger = Logger.getLogger(
            "ibis.satin.steal");

    /** Logger for spawns. */
    public static final Logger spawnLogger = Logger.getLogger(
            "ibis.satin.spawn");

    /** Logger for inlets. */
    public static final Logger inletLogger = Logger.getLogger(
            "ibis.satin.inlet");

    /** Logger for aborts. */
    public static final Logger abortLogger = Logger.getLogger(
            "ibis.satin.abort");

    /** Logger for fault tolerance. */
    public static final Logger ftLogger = Logger.getLogger("ibis.satin.ft");

    /** Logger for the global result table. */
    public static final Logger grtLogger = Logger.getLogger(
            "ibis.satin.ft.grt");

    /** Logger for shared objects. */
    public static final Logger soLogger = Logger.getLogger("ibis.satin.so");

    /** Logger for shared objects broadcasts. */
    public static final Logger soBcastLogger = Logger.getLogger("ibis.satin.so.bcast");
}
