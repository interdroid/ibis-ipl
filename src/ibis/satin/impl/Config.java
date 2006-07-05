/* $Id$ */

package ibis.satin.impl;

import ibis.util.TypedProperties;

import org.apache.log4j.Logger;

/**
 * Constants for the configuration of Satin. This interface is public because it
 * is also used in code generated by the Satin frontend.
 */

public interface Config {

    static final String PROPERTY_PREFIX = "satin.";

    static final String s_asserts = PROPERTY_PREFIX + "asserts";

    static final String s_closed = PROPERTY_PREFIX + "closed";

    static final String s_stats = PROPERTY_PREFIX + "stats";

    static final String s_detailed_stats = PROPERTY_PREFIX + "detailedStats";

    static final String s_alg = PROPERTY_PREFIX + "alg";

    static final String s_dump = PROPERTY_PREFIX + "dump";

    static final String s_in_latency = PROPERTY_PREFIX + "messagesInLatency";

    static final String s_so_delay = PROPERTY_PREFIX + "so.delay";

    static final String s_so_size = PROPERTY_PREFIX + "so.size";

    static final String s_so_lrmc = PROPERTY_PREFIX + "so.lrmc";

    static final String s_ft_naive = PROPERTY_PREFIX + "ft.naive";

    static final String s_ft_connectTimeout = PROPERTY_PREFIX
        + "ft.connectTimeout";

    static final String s_masterhost = PROPERTY_PREFIX + "masterHost";

    static final String s_delete_time = PROPERTY_PREFIX + "deleteTime";

    static final String s_delete_cluster_time = PROPERTY_PREFIX
        + "deleteClusterTime";

    static final String s_kill_time = PROPERTY_PREFIX + "killTime";

    static final String[] sysprops = { s_stats,
        s_detailed_stats, s_closed, s_asserts,
        s_ft_naive, s_ft_connectTimeout, s_masterhost, s_in_latency,
        s_delete_time, s_delete_cluster_time, s_kill_time, s_dump, s_so_delay,
        s_so_size, s_alg, s_so_lrmc };

    /** Enable or disable asserts. */
    static final boolean ASSERTS = TypedProperties.booleanProperty(s_asserts,
        false);

    /** true if the node should dump its datastructures during shutdown. */
    static final boolean DUMP = TypedProperties.booleanProperty(s_dump, false);

    /** Enable this if Satin should print statistics at the end. */
    static final boolean STATS = TypedProperties.booleanProperty(s_stats, true);

    /** Enable this if Satin should print statistics per machine at the end. */
    static final boolean DETAILED_STATS = TypedProperties.booleanProperty(
        s_detailed_stats, false);

    /** Enable this if satin should run with a closed world: no nodes can join or leave. */
    static final boolean CLOSED = TypedProperties.booleanProperty(s_closed,
        false);

    /** Determines master hostname. */
    static final String MASTER_HOST = TypedProperties
        .stringProperty(s_masterhost);

    /** Determines which load-balancing algorithm is used. */
    static final String SUPPLIED_ALG = TypedProperties.stringProperty(s_alg);

    /**
     * Fault tolerance with restarting crashed jobs, but without the global result table
     */
    static final boolean FT_NAIVE = TypedProperties.booleanProperty(s_ft_naive,
        false);

    /** Enable or disable an optimization for handling delayed messages. */
    static final boolean HANDLE_MESSAGES_IN_LATENCY = TypedProperties
        .booleanProperty(s_in_latency, false);

    /**
     * Timeout for connecting to other nodes (in joined()) who might be
     * crashed.
     */
    public static final long CONNECT_TIMEOUT = TypedProperties.intProperty(
        s_ft_connectTimeout, 120) * 1000L;

    /** Maximum time that messages may be buffered for message combining.
     * If > 0, it is used for combining shared objects invocations.
     * setting this to 0 disables message combining. */
    static final int SO_MAX_INVOCATION_DELAY = TypedProperties.intProperty(
        s_so_delay, 0);

    /** 
     * The maximum message size if message combining is used for SO Invocations.
     */
    static final int SO_MAX_MESSAGE_SIZE = TypedProperties.intProperty(
        s_so_size, 64 * 1024);

    /** Enable or label routing multicast for shared objects . */
    static final boolean LABEL_ROUTING_MCAST = TypedProperties.booleanProperty(
        s_so_lrmc, true);

    /** Used in automatic ft tests */
    static final int DELETE_TIME = TypedProperties
        .intProperty(s_delete_time, 0);

    /** Used in automatic ft tests */
    static final int DELETE_CLUSTER_TIME = TypedProperties.intProperty(
        s_delete_cluster_time, 0);

    /** Used in automatic ft tests */
    static final int KILL_TIME = TypedProperties.intProperty(s_kill_time, 0);

    /** Logger for communication. */
    public static final Logger commLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.comm");

    /** Logger for job stealing. */
    public static final Logger stealLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.steal");

    /** Logger for spawns. */
    public static final Logger spawnLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.spawn");

    /** Logger for idle. */
    public static final Logger idleLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.idle");

    /** Logger for inlets. */
    public static final Logger inletLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.inlet");

    /** Logger for aborts. */
    public static final Logger abortLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.abort");

    /** Logger for the global result table. */
    public static final Logger grtLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.grt");

    /** Logger for fault tolerance. */
    public static final Logger ftLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.faultTolerance");

    /** Logger for shared objects. */
    public static final Logger soLogger = ibis.util.GetLogger
        .getLogger("ibis.satin.so");
}
