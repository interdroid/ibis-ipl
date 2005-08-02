/* $Id$ */

package ibis.gmi;

import ibis.ipl.Ibis;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.NoMatchingIbisException;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.Registry;
import ibis.ipl.SendPort;
import ibis.ipl.StaticProperties;
import ibis.ipl.Upcall;
import ibis.ipl.WriteMessage;
import ibis.util.IPUtils;
import ibis.util.PoolInfo;
import ibis.util.Ticket;
import ibis.util.GetLogger;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;

/**
 * The {@link Group} class takes care of the startup, and has methods
 * to create, join, lookup, and exit a group.
 */
public final class Group implements GroupProtocol {

    public static Logger logger
            = GetLogger.getLogger(Group.class.getName());
     
    /** Ibis rank number in this run. */
    static int _rank;

    /** The total number of nodes involved in this run. */
    private static int _size;
    
    /** The local hostname. */
    private static String name;
    
    /** To get tickets from. */
    static Ticket ticketMaster = null;

    /** Unicast send ports, one for each node. */
    static SendPort[] unicast;
   
    /** The group registry, only lives on the node elected as the master. */
    static GroupRegistry registry;

    /**
     * Local cache of stub classes, so that not every group lookup has to
     * go through the registry.
     */
    private static Hashtable stubclasses;

    /** My local ibis. */
    static Ibis ibis;

    /** My local Ibis identifier. */
    private static IbisIdentifier localID;

    /** Ibis registry, used for setting up stuff. */
    private static Registry ibisRegistry;

    /** Port type for ports used in GMI. */
    private static PortType portType;

    /** Port on which group messages are received. */
    private static ReceivePort receivePort;

    /** For receiving messages from the GMI master. */
    private static ReceivePort systemIn;

    /** For sending messages to the GMI master. */
    private static SendPort systemOut;

    /** ReceivePort identifiers, one for each node. */
    private static ReceivePortIdentifier[] pool;

    /** Currently allocated multicast send ports. */
    private static Hashtable multicastSendports;

    /** Upcall handler. */
    private static GroupCallHandler groupCallHandler;

    /** Skeletons available through group identification. */
    private static GroupSkeleton[] groups;

    /** The current number of existing groups. */
    private static int groups_max;

    /** Skeletons on this node. */
    private static Vector skeletons;

    /** Stubs and stub identifications. */
    static GroupStub stubIDStack[] = new GroupStub[16];

    /** The stub counter, used to allocate stubs. */
    static int stubCounter;

    /**
     * Container class for group information.
     */
    static private final class GroupStubData {

        /** The group name. */
        String groupName;

        /** The name of the group interface for this group. */
        String typeName;

        /**
         * The stub class for this group, so that stubs can easily be
         * created.
         */
        Class stubClass;

        /** The group identification. */
        int groupID;

        /** The node identifications for all group members. */
        int[] memberRanks;

        /** The skeleton identifications for all group members. */
        int[] memberSkels;

        /**
         * Creates a new stub for the group at hand.
         *
         * @return the new stub.
         */
        GroupStub newStub() {
            try {
                GroupStub s = (GroupStub) stubClass.newInstance();
                int stubID;
                synchronized (stubIDStack) {
                    stubID = stubCounter++;
                    if (stubID >= stubIDStack.length) {
                        GroupStub[] temp = new GroupStub[2 * stubID];
                        for (int i = 0; i < stubIDStack.length; i++) {
                            temp[i] = stubIDStack[i];
                        }
                        temp[stubID] = s;
                        stubIDStack = temp;
                    } else {
                        stubIDStack[stubID] = s;
                    }
                }
                s.init(groupID, memberRanks, memberSkels, stubID);
                return s;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(Group._rank
                        + " GroupStubData.newStub() Failed to create stub of "
                        + "type " + typeName + " for group " + groupName + " "
                        + e);
            }
        }
    }

    /**
     * Handler class for all upcalls.
     */
    private static final class GroupCallHandler implements GroupProtocol,
            Upcall {

        /**
         * Invocation handler.
         *
         * @param r  the message to be read
         */
        private void handleInvocation(ReadMessage r) throws Exception {

            int dest = r.readInt();
            int inv = r.readByte();
            int res = r.readByte();

            switch (inv) {
            case InvocationScheme.I_GROUP:
            case InvocationScheme.I_COMBINED_FLAT_GROUP: {
                GroupSkeleton s = Group.getSkeletonByGroupID(dest);

                if (logger.isDebugEnabled()) {
                    logger.debug(Group._rank + 
                            ": GroupCallHandler.handleInvocation() - "
                            + "It is a GROUP INVOCATION");
                    logger.debug(Group._rank + ": skeleton = " + s);
                }

                s.handleMessage(inv, res, r);
            }
                break;
            default: {
                GroupSkeleton s = Group.getSkeleton(dest);
                s.handleMessage(inv, res, r);
            }
                break;
            }
        }

        /**
         * Upcall method. Handle the upcall, divide and conquer.
         *
         * @param m  the message to be read.
         */
        public void upcall(ReadMessage m) {

            int ticket;
            GroupSkeleton s;
            byte opcode;
            byte resultMode;

            try {
                opcode = m.readByte();

                switch (opcode) {
                case REGISTRY:

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - "
                                + "Got a REGISTRY");
                    }

                    Group.registry.handleMessage(m);
                    break;

                case INVOCATION:

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - "
                                + "Got an INVOCATION");
                    }

                    handleInvocation(m);
                    break;

                case REGISTRY_REPLY:

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank
                                + ": GroupCallHandler.upcall() - "
                                + "Got a REGISTRY_REPLY");
                    }

                    ticket = m.readInt();
                    RegistryReply r = (RegistryReply) m.readObject();

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - " +
                                "REGISTRY_REPLY forwarded to ticketMaster (" +
                                ticket + ")");
                    }

                    m.finish(); // ticketMaster may block.
                    logger.debug(Group._rank
                            + ": REGISTRY_REPLY forwarded to ticketMaster ("
                            + ticket + ")");
                    Group.ticketMaster.put(ticket, r);
                    break;

                case INVOCATION_FLATCOMBINE:

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - "
                                + "Got a INVOCATION_FLATCOMBINE");
                    }

                    stubIDStack[m.readInt()].handleFlatInvocationCombineMessage(
                            m);
                    break;

                case INVOCATION_BINCOMBINE:

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - " +
                                "Got a INVOCATION_BINCOMBINE");
                    }

                    stubIDStack[m.readInt()].handleBinInvocationCombineMessage(
                            m);
                    break;

                case INVOCATION_REPLY:
                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - " +
                                "Got a INVOCATION_REPLY");
                    }

                    resultMode = m.readByte();
                    ticket = m.readInt();
                    int stub = ticket >> 16;
                    ticket = ticket & 0xFFFF;
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - " +
                                "INVOCATION_REPLY forwarded to stub (" + 
                                stub + ", " + ticket + ")");
                    }

                    stubIDStack[stub].handleResultMessage(m,
                            ticket, resultMode);
                    break;

                case COMBINE:
                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - "
                                + "Got a COMBINE");
                    }

                    s = Group.getSkeleton(m.readInt());
                    s.handleCombineMessage(m);
                    break;

                case COMBINE_RESULT:

                    if (logger.isDebugEnabled()) {
                        logger.debug(Group._rank + 
                                ": GroupCallHandler.upcall() - "
                                + "Got a COMBINE_RESULT");
                    }
                    
                    s = Group.getSkeletonByGroupID(m.readInt());
                    s.handleCombineMessage(m);
                    break;
                default:                    
                    logger.warn(Group._rank + 
                            ": GroupCallHandler.upcall() - "
                            + "Got an illegal opcode !");
                }
            } catch (Exception e) {
                logger.warn(Group._rank + 
                        ": GroupCallHandler.upcall() - " +
                        ": Got an exception in GroupCallHandler !", e);                
            }
        }
    }

    /**
     * Initialization code. Starts up Ibis, elects a node to run the
     * group registry, creates send and receive ports.
     */
    static {
        try {
            ticketMaster = new Ticket();
            groups = new GroupSkeleton[10];
            groups_max = 10;
            skeletons = new Vector();
            stubclasses = new Hashtable();
            multicastSendports = new Hashtable();

            name = IPUtils.getLocalHostAddress().getHostName();

            if (logger.isDebugEnabled()) {
                logger.debug("?: <static> - " +
                        name + "- Init Group RTS");
            }

            StaticProperties reqprops = new StaticProperties();
            reqprops.add("serialization", "object");
            reqprops.add("worldmodel", "closed");
            reqprops.add("communication",
                    "OneToOne, ManyToOne, OneToMany, Reliable, "
                    + "AutoUpcalls, ExplicitReceipt");
            try {
                ibis = Ibis.createIbis(reqprops, null);
            } catch (NoMatchingIbisException e) {
                logger.warn("?: <static> - " + 
                        "Could not find an Ibis that can run this "
                        + "GMI implementation");
                System.exit(1);
            }
            
            localID = ibis.identifier();
            ibisRegistry = ibis.registry();

            portType = ibis.createPortType("GMI", reqprops);

            groupCallHandler = new GroupCallHandler();

            receivePort = portType.createReceivePort("GMI port on "
                    + localID, groupCallHandler);
            receivePort.enableConnections();

            PoolInfo info = PoolInfo.createPoolInfo();

            if (info.rank() == 0) {

                if (logger.isDebugEnabled()) {
                    logger.debug(_rank + ": <static> - " + name +
                            " I am master");
                }

                registry = new GroupRegistry();

                _size = info.size();
                _rank = 0;

                pool = new ReceivePortIdentifier[_size];
                pool[0] = receivePort.identifier();

                if (_size > 1) {

                    systemIn = portType.createReceivePort("GMI Master");
                    systemIn.enableConnections();

                    systemOut = portType.createSendPort("GMI Master");

                    for (int j = 1; j < _size; j++) {
                        ReadMessage r = systemIn.receive();
                        ReceivePortIdentifier reply
                                = (ReceivePortIdentifier) r.readObject();
                        ReceivePortIdentifier id
                                = (ReceivePortIdentifier) r.readObject();
                        r.finish();

                        systemOut.connect(reply);
                        pool[j] = id;
                    }

                    WriteMessage w = systemOut.newMessage();
                    w.writeObject(pool);
                    w.finish();
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(_rank + ": <static> - " + 
                            name + " I am client");
                }

                systemIn = portType.createReceivePort("GMI Client "
                        + localID);
                systemIn.enableConnections();

                systemOut = portType.createSendPort("GMI Client "
                        + localID);

                ReceivePortIdentifier master = ibisRegistry.lookupReceivePort(
                        "GMI Master");

                while (master == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    master = ibisRegistry.lookupReceivePort("GMI Master");
                }

                systemOut.connect(master);

                WriteMessage w = systemOut.newMessage();
                w.writeObject(systemIn.identifier());
                w.writeObject(receivePort.identifier());
                w.finish();

                ReadMessage r = systemIn.receive();
                pool = (ReceivePortIdentifier[]) r.readObject();
                r.finish();

                _size = pool.length;

                for (int j = 1; j < _size; j++) {
                    if (pool[j].equals(receivePort.identifier())) {
                        _rank = j;
                        break;
                    }
                }
            }

            unicast = new SendPort[_size];

            for (int j = 0; j < _size; j++) {
                unicast[j] = portType.createSendPort("Unicast on " + name
                        + " to " + pool[j].name());

                if (logger.isDebugEnabled()) {
                    logger.debug(_rank + ": <static> - " + 
                            "Connecting unicast sendport "
                            + unicast[j].identifier() + " to " + pool[j]);
                }

                unicast[j].connect(pool[j]);

                if (logger.isDebugEnabled()) {
                    logger.debug(_rank + ": <static> - " + 
                            "Connecting unicast sendport "
                            + unicast[j].identifier() + " done");
                }
            }

            if (_size > 1) {
                if (_rank == 0) {
                    for (int j = 1; j < _size; j++) {
                        ReadMessage r = systemIn.receive();
                        r.finish();
                    }

                    WriteMessage w = systemOut.newMessage();
                    w.finish();
                } else {
                    WriteMessage w = systemOut.newMessage();
                    w.finish();
                    ReadMessage r = systemIn.receive();
                    r.finish();
                }
            }

            receivePort.enableUpcalls();

            if (logger.isDebugEnabled()) {
                logger.debug(_rank + ": <static> - " + name + ": Group init");
            }

            /****
             * This is only supported in SDK 1.3 and upwards. Comment out
             * if you run an older SDK.
             */
            Runtime.getRuntime().addShutdownHook(
                    new Thread("gmi.Group ShutdownHook") {
                        public void run() {
                            if (ibis != null) {
                                try {
                                    ibis.end();
                                } catch (IOException e) {
                                    throw new ibis.ipl.IbisError(e);
                                }
                                ibis = null;
                            }
                            // System.err.println("Ended Ibis");
                        }
                    });
            /* End of 1.3-specific code */

        } catch (Exception e) {
            logger.fatal(name + ": Could not init Group RTS " + e, e);
            System.exit(1);
        }
    }

    /**
     * Gets a multicast send port using an identification to see if we
     * already have such a send port. If not, a new one is created.
     *
     * @param ID    identification of the send port
     * @param hosts the target hosts of the multicast
     * @return the multicast send port.
     */
    public static SendPort getMulticastSendport(String ID, int[] hosts) {

        // Note: for efficiency the ranks in hosts should be sorted
        // (low->high) !!!

        if (hosts.length == 1) {
            return unicast[hosts[0]];
        }
     
        if (logger.isDebugEnabled()) { 
            logger.debug(_rank + ": Group.getMulticastSendport - " + 
                    "Looking for multicast sendport " + ID);
        }

        SendPort temp = (SendPort) multicastSendports.get(ID);

        if (temp == null) {
            // there is no multicast sendport to this combination of hosts yet.
            // so create it and add it to the table.
            if (logger.isDebugEnabled()) { 
                logger.debug(_rank + ": Group.getMulticastSendport - " + 
                        "Creating multicast sendport " + ID);
            }

            try {
                temp = portType.createSendPort("Multicast on " + name + " to "
                        + ID);
            } catch (IOException e) {
                logger.fatal(name + ": Could not create multicast group "
                        + ID + " " + e, e);
                System.exit(1);
            }

            try {
                for (int i = 0; i < hosts.length; i++) {
                    temp.connect(pool[hosts[i]]);
                    
                    if (logger.isDebugEnabled()) { 
                        logger.debug(_rank + ": Group.getMulticastSendport - " +    
                                "Connected to " + hosts[i] + ", " + pool[hosts[i]]);
                    }
                }
            } catch (IOException e) {
                logger.fatal(name
                        + ": Could not interconnect multicast group " + ID
                        + " " + e, e);
                System.exit(1);
            }

            multicastSendports.put(ID, temp);
        }
        
        if (logger.isDebugEnabled()) { 
            logger.debug(_rank + ": Group.getMulticastSendport - " +                     
                    "Found multicast send port " + temp);
        }
        return temp;
    }

    /**
     * Gets a new (local) skeleton identification.
     *
     * @param skel the group skeleton
     * @return the new skeleton identification.
     */
    protected static int getNewSkeletonID(GroupSkeleton skel) {

        synchronized (skeletons) {
            int next = skeletons.size();

            skeletons.add(next, skel);

            return next;
        }
    }

    /**
     * Makes a skeleton available through its group identification.
     *
     * @param groupID  the group identification
     * @param skeleton the group skeleton to be made available
     */
    protected static void registerGroupMember(int groupID,
            GroupSkeleton skeleton) {

        if (logger.isDebugEnabled()) {
            logger.debug(_rank + ": <static> - " + 
                    "Group.registerGroupMember(" + groupID + " "
                    + skeleton.getClass().getName());
        }

        if (groupID >= groups_max) {
            int new_max = 2 * groupID + 1;
            GroupSkeleton[] g = new GroupSkeleton[new_max];
            for (int i = 0; i < groups_max; i++) {
                g[i] = groups[i];
            }
            for (int i = groups_max; i < new_max; i++) {
                g[i] = null;
            }
            groups_max = new_max;
            groups = g;
        }
        groups[groupID] = skeleton;
    }

    /**
     * Gets a skeleton through its local identification.
     * @param skel the local skeleton identification
     * @return the group skeleton.
     */
    static GroupSkeleton getSkeleton(int skel) {
        return (GroupSkeleton) skeletons.get(skel);
    }

    /**
     * Gets a skeleton through its group identification.
     * @param groupID the group identification
     * @return the group skeleton
     */
    static GroupSkeleton getSkeletonByGroupID(int groupID) {
        return groups[groupID];
    }

    /**
     * Creates a group.
     *
     * @param nm  the name of the group to be created
     * @param type  the group interface that this group will provide
     * @param size  the number of group members that this group will have
     * @exception RuntimeException when the group already exists or in case
     * of a communication error.
     */
    public static void create(String nm, Class type, int size)
            throws RuntimeException {

        try {
            if (logger.isDebugEnabled()) {
                logger.debug(_rank + ": create(" + nm + ", " + type
                        + ", " + size + ") starting");
            }
            
            // Check if the size is legal
            if (size <= 0) { 
                throw new RuntimeException("Illegal group size (" + size 
                        + ") specified!");
            }

            // Check if the name is legal 
            if (nm == null || nm.length() == 0) { 
                throw new RuntimeException("Illegal group name (" + name
                        + ") specified!");
            }
                      
            // Check if the type is legal 
            if (type == null || !type.isInterface() ||
                    !GroupInterface.class.isAssignableFrom(type)) { 
                throw new RuntimeException("Illegal group type (" + type
                        + ") specified!");                
            }
            
            int ticket = ticketMaster.get();

            WriteMessage w = unicast[0].newMessage();
            w.writeByte(REGISTRY);
            w.writeByte(CREATE_GROUP);
            w.writeInt(_rank);
            w.writeInt(ticket);
            w.writeString(nm);
            w.writeString(type.getName());
            w.writeInt(size);
            w.finish();

            logger.debug(_rank + ": Group.create(" + nm + ", " + size
                    + ") waiting for reply on ticket(" + ticket + ")");

            RegistryReply r = (RegistryReply) ticketMaster.collect(ticket);

            if (r.result == CREATE_FAILED) {
                throw new RuntimeException(_rank + " Group.create(" + nm + ", "
                        + size + ") Failed : Group already exists!");
            }

            logger.debug(_rank + ": Group.create(" + nm + ", " + size
                    + ") done");

        } catch (IOException e) {
            throw new RuntimeException(_rank + " Group.create(" + nm + ", "
                    + size + ") Failed : communication error !"
                    + e.getMessage());
        }
    }

    /**
     * Joins the group with this name, by communicating with
     * the group registry, and blocks until the group is ready.
     *
     * @param nm  the name of the group we are joining
     * @param o   the member that is joining the group
     * @param rank the rank that the member wishes to have in the group
     * @param timeout the maximum time the operation may block (in milliseconds). 
     * Join will block indefinitely if timeout is 0 or smaller.  
     * @exception RuntimeException when the group is not found,
     *            the group is already full, or something else goes wrong.
     */
    public static void join(String nm, GroupMember o, int rank, long timeout) throws RuntimeException {
        
        int groupnumber = 0;
        int[] memberRanks = null;
        int[] memberSkels = null;
        boolean retry;
        int ticket;
        WriteMessage w;
        RegistryReply r;
        RuntimeException exception = null;
        
        if (logger.isDebugEnabled()) {
            logger.debug(_rank + ": join(" + nm + ", " + o
                    + ", " + rank + ") starting");
        }

        try {
            do { 
                retry = false;

                ticket = ticketMaster.get();

                w = unicast[0].newMessage();
                w.writeByte(REGISTRY);
                w.writeByte(JOIN_GROUP);
                w.writeInt(_rank);
                w.writeInt(rank);                
                w.writeInt(ticket);
                w.writeString(nm);
                w.writeObject(o.groupInterfaces);
                w.writeInt(o.mySkel);
                w.writeLong(timeout);
                w.finish();

                logger.debug(_rank + ": join(" + nm
                        + ") waiting for reply on ticket(" + ticket + ")");

                r = (RegistryReply) ticketMaster.collect(ticket);

                switch (r.result) {
                case JOIN_UNKNOWN:

                    if (logger.isDebugEnabled()) {
                        logger.debug(_rank + ": join(" + nm
                                + ", " + rank + ") group not found, retry");
                    }
                    retry = true;
                    break;

                case JOIN_WRONG_TYPE:
                    exception = new RuntimeException(_rank + " Group.join(" + nm
                            + ", " + rank + ") Failed : Group member has wrong type!");
                    break;
                    
                case JOIN_FULL:
                    exception = new RuntimeException(_rank + " Group.join(" + nm
                            + ", " + rank + ") Failed : Group full!");
                    break;
                    
                case JOIN_RANK_TAKEN:
                    exception = new RuntimeException(_rank + " Group.join(" + nm
                            + ", " + rank + ") Failed : Group rank already taken!");
                    break;
                    
                case JOIN_ILLEGAL_RANK:
                    exception = new RuntimeException(_rank + " Group.join(" + nm
                            + ", " + rank + ") Failed : Illegal rank specified!");
                    break;
                
                case JOIN_TIMEOUT:
                    exception = new RuntimeException(_rank + " Group.join(" + nm
                            + ", " + rank + ") Failed : Timeout");
                    break;
                
                case JOIN_OK:
                    groupnumber = r.groupnum;
                    memberRanks = r.memberRanks;
                    memberSkels = r.memberSkels;
                    break;
                    
                default:
                    logger.fatal(_rank + "Internal error: join(" + 
                            nm + ") Failed - Got illegal opcode");
                    System.exit(1);
                }
                
            } while (retry);
        
        } catch (Throwable e) {
            logger.warn(_rank + "Unexpected exception: join(" + 
                            nm + ") Failed - Communication error ?", e);
            
            exception = new RuntimeException(_rank + " Group.join(" + nm
                    + ") Failed - Communication error !", e);
        }    
            
        if (exception != null) {
            throw exception;        
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug(_rank + ": join(" + nm + ") group("
                    + groupnumber + ") found !");
        }

        o.init(groupnumber, memberRanks, memberSkels);
            
        if (logger.isDebugEnabled()) {
            logger.debug(_rank + ": join(" + nm + ", " + o
                    + ") done");
        }
    } 
    
    /**
     * Joins the group with this name, by communicating with
     * the group registry, and blocks until the group is ready.
     *
     * @param nm  the name of the group we are joining
     * @param o     the member that is joining the group
     * @exception RuntimeException when the group is not found,
     *            the group is already full, or something else goes wrong.
     */
    public static void join(String nm, GroupMember o) throws RuntimeException {
        join(nm, o, -1, 0L);
    }

    /**
     * Joins the group with this name, by communicating with
     * the group registry, and blocks until the group is ready.
     *
     * @param nm the name of the group we are joining
     * @param o the member that is joining the group
     * @param rank the rank that the member wishes to have in the group 
     * @exception RuntimeException when the group is not found,
     *            the group is already full, or something else goes wrong.
     */
    public static void join(String nm, GroupMember o, int rank) throws RuntimeException {
        join(nm, o, rank, 0L);
    }

    /**
     * Looks up the group with this name, if necessary by communicating with
     * the group registry, and possibly waiting until the group is ready.
     *
     * @param nm  the name of the group we are looking for
     * @return the group interface we were looking for
     * @exception RuntimeException some network error has occurred
     */
    public static GroupInterface lookup(String nm) throws RuntimeException {
        return lookup(nm, -1);
    }
        
    /**
     * Looks up the group with this name, if necessary by communicating with
     * the group registry, and possibly waiting until the group is ready.
     *
     * @param nm  the name of the group we are looking for
     * @param timeout maximum time the operation may block, in milliseconds. 
     * (0 indicates that the lookup returns immediately, negative value indicates
     *  no timeout)   
     * @return the group interface we were looking for
     * @exception RuntimeException when the group is not found, or something
     *            else goes wrong.
     */
    public static GroupInterface lookup(String nm, long timeout) throws RuntimeException {
        
        GroupStubData data = (GroupStubData) stubclasses.get(nm);

        long time = System.currentTimeMillis();
               
        if (data == null) {
            // No stub info available yet, so get it from the registry.            
            data = new GroupStubData();
            
            try {                
                boolean done = false;

                while (!done) {
                    // Send registry a request.
                    int ticket = ticketMaster.get();
                    WriteMessage w = unicast[0].newMessage();
                    w.writeByte(REGISTRY);
                    w.writeByte(FIND_GROUP);
                    w.writeInt(_rank);
                    w.writeInt(ticket);
                    w.writeString(nm);
                    w.finish();

                    // Wait for the reply 
                    RegistryReply r = (RegistryReply) ticketMaster.collect(ticket);
                    
                    // Look at the reply
                    switch (r.result) {
                    
                    // Unknown group 
                    case GROUP_UNKNOWN:
                        
                        if (logger.isDebugEnabled()) {  
                            logger.debug(_rank + ": lookup(" + nm
                                    + ", " + timeout + ") - Group not known.");
                        } 
                        
                        if (timeout == 0) {                         
                            throw new RuntimeException(Group._rank
                                    + " Group.lookup(" + nm + ", " + timeout
                                    + ") Failed : unknown group!");                        
                        } 
                        break;

                    // Known, group which isn't complete yet
                    case GROUP_NOT_READY:
                        
                        if (logger.isDebugEnabled()) {  
                            logger.debug(_rank + ": lookup(" + nm
                                    + ", " + timeout + ") - Group not ready.");
                        } 
                        
                        if (timeout == 0) {
                            throw new RuntimeException(Group._rank
                                    + " Group.lookup(" + nm + ", " + timeout
                                    + ") Failed : group not ready!");
                        }                            
                        break;

                    // Found a group      
                    case GROUP_OK:
                        
                        if (logger.isDebugEnabled()) {  
                            logger.debug(_rank + ": lookup(" + nm
                                    + ", " + timeout + ") - Group OK.");
                        } 
                        
                        done = true;

                        data.groupName = nm;
                        data.typeName = r.str;
                        data.groupID = r.groupnum;
                        data.memberRanks = r.memberRanks;
                        data.memberSkels = r.memberSkels;
                        
                        String classname = "";
                        
                        try {
                            String temp = data.typeName;
                            StringTokenizer s = new StringTokenizer(temp, ".");
                            int tokens = s.countTokens();

                            if (tokens > 1) {
                                classname = s.nextToken();

                                for (int i = 1; i < tokens - 1; i++) {
                                    classname += "." + s.nextToken();
                                }
                                classname += ".";
                            }

                            classname += "group_stub_" + s.nextToken();
                            data.stubClass = Class.forName(classname);
                        } catch (Exception e) {
                            
                            if (logger.isDebugEnabled()) {  
                                logger.debug(_rank + ": lookup(" + nm
                                        + ", " + timeout + ") - class "
                                        + classname + " not found!" + e);
                            }                             
                            throw new Error(_rank + " Group.lookup(" + nm + ", "
                                    + timeout + ") - class " + classname + 
                                    " not found!", e);
                        }
                        break;

                    // Registry returned gibberish     
                    default:
                        
                        if (logger.isDebugEnabled()) {  
                            logger.debug(_rank + ": lookup(" + nm
                                    + ", " + timeout + ") - internal error - "
                                    + "got unexpected answer " + r.result);
                        }                             
                        
                        throw new Error(_rank + " Group.lookup(" + nm + ", "
                                + timeout + ") - internal error - "   
                                + "got unexpected answer " + r.result);                                           
                    }
                
                    if (!done) {  
                        // We're not done yet, but maybe there was a timeout 
                        // which has expired.
                        if (timeout > 0) { 
                            long current = System.currentTimeMillis();
                        
                            if (current - time >= timeout) {
                            
                                if (logger.isDebugEnabled()) {  
                                    logger.debug(_rank + ": lookup(" 
                                            + nm + ", " + timeout 
                                            + ") - timeout!");
                                }                             
                                                       
                                throw new RuntimeException(Group._rank
                                        + " Group.lookup(" + nm + ", " + timeout  
                                        + ") - timeout!");                              
                            }                        
                        }
                    
                        // Not sleep for a short time to prevent 
                        // overloading the GroupRegistry
                        try { 
                            Thread.sleep(250); 
                        } catch (Exception e) { 
                            // ignore
                        }
                    }
                }
                
            } catch (Exception e) {
                throw new RuntimeException(Group._rank + " Group.lookup(" + nm
                        + ") Failed : " + e);
            }

            // Add the info to the cache.
            stubclasses.put(nm, data);
        }

        return data.newStub();
    }

    
    
    /**
     * Creates a combined invocation info structure by communication with
     * the registry. Note that this method blocks until all invokers involved
     * in this combined invocation have registered (configured, in users terms).
     *
     * @param ci      the combined invocation scheme
     * @param groupID the group identification
     * @param method  the method descriptor
     * @param nm      the name used for registering this combined invocation
     * @param mode    summary of the combined invocation scheme
     * @param rank    node identification of caller
     * @param size    total number of nodes that will be involved in this
     *                combined invocation
     * @return the combined invocation info structure.
     * @exception RuntimeException when there is a failure.
     */
    protected static CombinedInvocationInfo defineCombinedInvocation(
            CombinedInvocation ci, int groupID, String method, String nm,
            int mode, int rank, int size) throws RuntimeException {
        try {
            int ticket = ticketMaster.get();
            WriteMessage w = unicast[0].newMessage();
            w.writeByte(REGISTRY);
            w.writeByte(DEFINE_COMBINED);
            w.writeInt(groupID);
            w.writeInt(_rank);
            w.writeInt(ticket);
            w.writeString(nm);
            w.writeString(method);
            w.writeInt(rank);
            w.writeInt(size);
            w.writeInt(mode);
            w.finish();

            RegistryReply r = (RegistryReply) ticketMaster.collect(ticket);

            switch (r.result) {
            case COMBINED_FAILED:
                throw new RuntimeException(r.str);
            case COMBINED_OK:
                return r.inf;
            default:
                throw new RuntimeException(
                        "Unexpected answer on DEFINE_COMBINED");
            }
        } catch (Exception e) {
            throw new RuntimeException(Group._rank
                    + " defineCombinedInvocation(" + method
                    + ") Failed : " + e);
        }
    }

    /**
     * Implements an identifier barrier with a specified size.
     *
     * @param id      the barrier identification.
     * @param cpu     the invoker
     * @param size    the number of invokers before the barrier is released.
     */
    protected static void barrier(String id, int cpu, int size) {
        try {
            int ticket = ticketMaster.get();
            WriteMessage w = unicast[0].newMessage();
            w.writeByte(REGISTRY);
            w.writeByte(BARRIER);
            w.writeInt(ticket);
            w.writeString(id);
            w.writeInt(size);
            w.writeInt(cpu);
            w.finish();

            RegistryReply r = (RegistryReply) ticketMaster.collect(ticket);

            switch (r.result) {
            case BARRIER_FAILED:
                throw new RuntimeException(r.str);
            case BARRIER_OK:
                return;
            default:
                throw new RuntimeException(
                        "Unexpected answer on DEFINE_COMBINED");
            }
        } catch (Exception e) {
            throw new RuntimeException(Group._rank + " barrier(" + id
                    + ") Failed : " + e);
        }
    }

    /**
     * Returns the node identification of the invoker.
     *
     * @return node identification.
     */
    public static int rank() {
        return _rank;
    }

    /**
     * Returns the total number of nodes in this run.
     *
     * @return total number of nodes.
     */
    public static int size() {
        return _size;
    }

    /**
     * Returns the sendport for the specified group member.
     * @param mem the group member.
     * @return the sendport for the specified group member.
     */
    public static SendPort unicast(int mem) {
        return unicast[mem];
    }

    /**
     * Exits from a group and cleans up resources. This method
     * must be called by all members of the group, making it a sort
     * of combined invocation.
     */
    public static void exit() {
        // TODO: Remove the information that is registered in the registry???
        try {

            if (_rank == 0) {

                if (logger.isDebugEnabled()) {
                    logger.debug(_rank + ": exit() - " +name + " master doing exit");
                }

                if (_size > 1) {

                    for (int j = 1; j < _size; j++) {
                        ReadMessage r = systemIn.receive();
                        r.finish();
                    }

                    WriteMessage w = systemOut.newMessage();
                    w.finish();

                    systemOut.close();
                    systemIn.close();
                }

            } else {

                if (logger.isDebugEnabled()) {
                    logger.debug(_rank + ": exit() - " +name + " client doing exit");
                }

                WriteMessage w = systemOut.newMessage();
                w.finish();

                ReadMessage r = systemIn.receive();
                r.finish();

                systemOut.close();
                systemIn.close();

            }

            for (int i = 0; i < _size; i++) {
                unicast[i].close();
            }

            Enumeration hash_elts = multicastSendports.elements();

            while (hash_elts.hasMoreElements()) {
                SendPort p = (SendPort) (hash_elts.nextElement());
                p.close();
            }

            receivePort.close();
            ibis.end();
            ibis = null;

            if (logger.isDebugEnabled()) {
                logger.debug(_rank + ": exit()- " +"Group exit done");
            } 

        } catch (Exception e) {
            logger.warn("EEEEEK", e);
        }
    }

    /**
     * Looks up the method described by descr in the group interface i.
     * If found, its {@link GroupMethod} is returned.
     *
     * @param i    the group interface in which to look
     * @param desc the method descriptor, as a string. Example format:
     *             "int lookmeup(int[],int)"
     * @return The {@link GroupMethod} object of the method.
     * @exception NoSuchMethodException is thrown when the method is not found.
     */
    public static GroupMethod findMethod(GroupInterface i, String desc)
            throws NoSuchMethodException {
        GroupStub stub = (GroupStub) i;
        GroupMethod m = stub.getMethod(desc);
        if (m == null) {
            throw new NoSuchMethodException("Method " + desc + " not found");
        }
        return m;
    }
}

