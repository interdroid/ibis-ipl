package ibis.ipl.impl.net.rel;

import ibis.ipl.impl.net.*;

import ibis.ipl.IbisIdentifier;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
* The REL output implementation.
*/
public final class RelOutput
    extends NetBufferedOutput
    // extends NetOutput
    implements RelConstants, RelSweep {

    private final static boolean STATISTICS = Driver.STATISTICS;

    /**
     * The driver used for the 'real' output.
     */
    private NetDriver subDriver = null;

    /**
     * The communication output.
     */
    private NetOutput dataOutput = null;

    private int[]	ackReceiveSet = new int[ACK_SET_IN_INTS];

    private Driver	relDriver;

    /**
     * Index number to identify this port for piggybacking
     */
    private int	myIndex;

    private IbisIdentifier partnerIbisId;

    private RelInput piggyPartner;

    private int	headerStart;	// Header starts here
    private int	ackStart;	// Ack part of header starts here

    static int SEQNO_OFFSET = 0;


    /**
     * The acknowledgement input.
     */
    private NetInput	controlInput = null;
    private int		controlHeaderStart;

    private NetReceiveBuffer ackPacket;



    /**
     * The sliding window descriptor.
     * The window size is in rel.Driver.
     */
    private static final int DEFAULT_WINDOW_SIZE = 32;
    private int		windowSize = DEFAULT_WINDOW_SIZE;
    private int		nextAllocate = FIRST_PACKET_COUNT; // Allocated to send up to here
    private int		windowStart = FIRST_PACKET_COUNT;  // Acked up to here

    /**
     * The sliding window send queue.
     */
    private RelSendBuffer	front = null;
    private RelSendBuffer	nextToSend = null;
    private RelSendBuffer	tail = null;


    /**
     * Retransmission control
     */
    private long	safetyInterval = Driver.sweepInterval;	// Wait at least this before rexmit

    /**
     * Packet cache
     */
    private RelSendBuffer	freeBuffers = null;


    private int nRexmit;
    private int nRexmit_nack;


    java.util.Random discardRandom;


    static {
	if (! USE_PIGGYBACK_ACKS) {
	    System.err.println("Ibis.net.rel: Do not use piggybacked acks");
	}
    }


    /**
     * Constructor.
     *
     * @param sp the properties of the output's
     * {@link ibis.ipl.impl.net.NetSendPort NetSendPort}.
     * @param driver the REL driver instance.
     * @param output the controlling output.
     */
    RelOutput(NetPortType pt, NetDriver driver, NetIO up, String context)
	    throws NetIbisException {

	super(pt, driver, up, context);

	relDriver = (Driver)driver;

	myIndex = relDriver.registerOutput(this);

	// The rexmit watchdog:
	relDriver.registerSweep(this);

	// Override the SendBuffer factory in NetBufferedOutput
	factory = new NetBufferFactory(new RelSendBufferFactoryImpl());

	if (RANDOM_DISCARDS > 0.0) {
	    discardRandom = new java.util.Random();
	}
    }

    /*
     * {@inheritDoc}
     */
    public synchronized void setupConnection(NetConnection cnx)
	    throws NetIbisException {

	/* Main connection */
	NetOutput dataOutput = this.dataOutput;

	if (dataOutput == null) {
	    if (subDriver == null) {
		String subDriverName = getMandatoryProperty("Driver");
		subDriver = driver.getIbis().getDriver(subDriverName);
	    }

	    dataOutput = newSubOutput(subDriver);
	    this.dataOutput = dataOutput;
	} else {
	    System.err.println("No support yet for one-to-many connections in RelOutput.java");
	}

	dataOutput.setupConnection(cnx);

	int _mtu = dataOutput.getMaximumTransfertUnit();
	if (mtu == 0  ||  mtu > _mtu) {
	    mtu = _mtu;
	}

	headerStart = dataOutput.getHeadersLength();
System.err.println("RelOutput: headerStart " + headerStart);
	ackStart    = headerStart + NetConvert.INT_SIZE;
	dataOffset  = ackStart + RelConstants.headerLength;
	if (DEBUG) {
	    SEQNO_OFFSET = headerStart / NetConvert.INT_SIZE;
	}

	factory.setMaximumTransferUnit(mtu);

	String windowPreset = System.getProperty("ibis.rel.window");
	if (windowPreset != null) {
	    windowSize = Integer.parseInt(windowPreset);
	    System.err.println("ibis.net.rel.windowSize set to " + windowSize);
	}

	try {
            NetServiceLink link = cnx.getServiceLink();
	    ObjectInputStream  is = new ObjectInputStream (link.getInputSubStream ("rel"));
            ObjectOutputStream os = new ObjectOutputStream(link.getOutputSubStream("rel"));

	    partnerIbisId = (IbisIdentifier)is.readObject();
            is.close();
	    os.writeObject(relDriver.getIbis().identifier());
	    os.writeInt(myIndex);
	    os.writeInt(windowSize);
            os.close();
	} catch (java.io.IOException e) {
	    throw new NetIbisException(e);
	} catch (java.lang.ClassNotFoundException e) {
	    throw new NetIbisException(e);
	}

	relDriver.registerOutputConnection(myIndex, partnerIbisId);

	NetInput controlInput = this.controlInput;

	/* Reverse connection */
	if (controlInput == null) {
		controlInput = newSubInput(subDriver);
		this.controlInput = controlInput;
	}

	controlInput.setupConnection(new NetConnection(cnx, new Integer(-1)));
	controlHeaderStart = controlInput.getHeadersLength();
    }


    public int getHeadersLength() {
	return dataOffset;
    }


    private void pushPiggy(RelSendBuffer frag) {
	if (! USE_PIGGYBACK_ACKS) {
	    NetConvert.writeInt(-1, frag.data, ackStart);
	    return;
	}

	if (piggyPartner == null) {
	    piggyPartner = relDriver.requestPiggyPartner(this, partnerIbisId);
	}
	if (DEBUG_PIGGY) {
	    System.err.println("My piggy partner is " + piggyPartner);
	}
	if (piggyPartner != null) {
	    piggyPartner.pushAck(frag.data, ackStart);
	}
    }


    static int reportPacket(java.io.PrintStream out,
			    String pre,
			    byte[] data,
			    int offset,
			    int showLength) {
	int[] i_packet = new int[showLength];
	NetConvert.readArraySliceInt(data, offset,
				     i_packet, 0, i_packet.length);
	out.print(pre + "contains ");
	for (int i = 0; i < i_packet.length; i++) {
	    out.print(i_packet[i] + " ");
	}
	out.println();
	return i_packet[SEQNO_OFFSET];
    }


    // Call this synchronized
    private void doSendBuffer(RelSendBuffer frag, long now)
	    throws NetIbisException {
	frag.sent = true;
	frag.lastSent = now;
	/* We don't release our lock here. The locking scheme must
	 * allow holding a RelOutput lock, then (quickly) grabbing
	 * a RelInput lock. The reverse scheme is not allowed: it
	 * would lead to deadlock. */
	if (DEBUG_ACK) {
	    int first_int = reportPacket(System.err,
					 "Before piggy: Send packet ",
					 frag.data,
					 0, // headerStart,
				     headerStart / NetConvert.INT_SIZE +
					 4);
	    if (frag.fragCount != first_int) {
		throw new NetIbisException("Packet corrupted");
	    }
	}
	if (DEBUG) {
	    if (! frag.ownershipClaimed) {
		throw new NetIbisException("Packet ownership bit corrupted");
	    }
	}
	pushPiggy(frag);

	if (RANDOM_DISCARDS > 0.0 &&
		discardRandom.nextDouble() < RANDOM_DISCARDS) {
	    return;
	}

	dataOutput.writeByteBuffer(frag);
	if (DEBUG_ACK) {
	    int first_int = reportPacket(System.err,
					 "After send: Sent packet ",
					 frag.data,
					 0, // headerStart,
				     headerStart / NetConvert.INT_SIZE +
					 4);
	    if (frag.fragCount != first_int) {
		throw new NetIbisException("Packet corrupted");
	    }
	}
    }


    static void reportAck(java.io.PrintStream out,
			  String pre,
			  byte[] data,
			  int offset) {
	if (SEQNO_OFFSET > 0) {
	    System.err.print("Lower layer header = [");
	    for (int i = 0; i < SEQNO_OFFSET; i++) {
		int s = NetConvert.readInt(data, i * NetConvert.INT_SIZE);
		out.print("0x" + Integer.toHexString(s) + ",");
	    }
	    out.print("] -- ");
	}
	out.print(pre + " Ack packet " + ", offset " + offset +
		") start " + NetConvert.readInt(data, offset) +
		" contains [");
	for (int i = 0; i < ACK_SET_IN_INTS; i++) {
	    int s = NetConvert.readInt(data, offset + (i + 1) * NetConvert.INT_SIZE);
	    out.print("0x" + Integer.toHexString(s) + ",");
	}
	out.println("]");
    }


    // Call this synchronized
    private boolean pollControlChannel() throws NetIbisException {
	if (ackPacket == null) {
	    ackPacket = controlInput.createReceiveBuffer(controlHeaderStart + RelConstants.headerLength);
	}
	boolean messageArrived = false;
	if (DEBUG) {
	    System.err.println("Out of credits? Poll control channel");
	}
	while (true) {
	    Integer r = controlInput.poll();
	    if (r == null) {
		return messageArrived;
	    }
	    messageArrived = true;
	    controlInput.readByteBuffer(ackPacket);
	    if (DEBUG_ACK) {
		reportAck(System.err,
			  "<<<<<<<<<<<<<< control packet length " + ackPacket.length,
			  ackPacket.data,
			  controlHeaderStart);
	    }
	    handleAck(ackPacket.data, controlHeaderStart);
	}
    }


    private int allowPoll = 0;

    // Call this synchronized
// synchronized
    private void handleSendContinuation() throws NetIbisException {
	if (DEBUG) {
	    System.err.println("handleSendContinuation, front packet " +
		    (front == null ? -1 : front.fragCount) +
		    " nextToSend packet " +
		    (nextToSend == null ? -1 : nextToSend.fragCount));
	}
	long now = System.currentTimeMillis();

	if (allowPoll > 1) {
	    throw new NetIbisException("Too deep recursion in handleSendContinuation");
	}

	RelSendBuffer scan = nextToSend;
	while (scan != null && scan.fragCount - windowStart < windowSize) {
	    if (DEBUG) {
		System.err.println("Investigate packet " + scan.fragCount +
			" [sent=" + scan.sent + ",acked=" + scan.acked +
			"]; window = [" + windowStart + "," +
			(windowStart + windowSize) + "]");
	    }
	    if (! scan.sent && ! scan.acked) {
		doSendBuffer(scan, now);
		nextToSend = scan.next;
	    }
	    scan = scan.next;
	}

	/* If the send window is closed and we still have packets to
	 * send out, poll the explicit ack channel */
	if (allowPoll == 0 && nextToSend != null) {
	    if (pollControlChannel()) {
		allowPoll++;
		handleSendContinuation();
		allowPoll--;
	    }
	}
    }


    // Call this synchronized
    private void enqueueSendBuffer(RelSendBuffer frag) throws NetIbisException {
	if (front == null) {
	    front = frag;
	} else {
	    tail.next = frag;
	}
	tail = frag;
	frag.next = null;

	if (nextToSend == null) {
	    nextToSend = frag;
	}

	handleSendContinuation();
    }


    synchronized
    void handleAck(byte[] data, int offset) throws NetIbisException {
	RelSendBuffer scan;
	RelSendBuffer prev;

	int	ackOffset = NetConvert.readInt(data, offset);
	offset += NetConvert.INT_SIZE;
	NetConvert.readArraySliceInt(data, offset,
				     ackReceiveSet, 0, ACK_SET_IN_INTS);
	offset += NetConvert.INT_SIZE * ACK_SET_IN_INTS;
	if (DEBUG_ACK) {
	    System.err.println("@@@@@@@@@@@@@@@@@@@ Receive ack seqno " +
		    ackOffset);
	    // Thread.dumpStack();
	}

	/* First all fragments up to ackOffset. */
	for (scan = front;
	     scan != null && scan.fragCount < ackOffset;
	     scan = front) {
	    front = scan.next;
	    windowStart++;
	    if (DEBUG_ACK) {
		System.err.println(".................. Now free packet " +
			scan.fragCount + " windowStart := " + windowStart);
	    }
	    scan.free();
	}

	/* Then all fragments indicated by ackReceiveSet.
	 * Retransmit all fragments missing from ackReceiveSet (except very
	 * recently sent ones):
	 * out-of-order arrival is much less probable than packet loss. */
	int maxAcked = 0;
	int maxNonemptyIndex = 0;
	for (int i = ackReceiveSet.length - 1; i >= 0; i--) {
	    if (ackReceiveSet[i] != 0) {
		maxNonemptyIndex = i + 1;
		int bit = 0;
		for (bit = NetConvert.BITS_PER_INT - 1; bit >= 0; bit--) {
		    if ((ackReceiveSet[i] & (1 << bit)) != 0) {
			break;
		    }
		}
		if (DEBUG_ACK && bit == 0) {
		    System.err.println("HAVOC HAVOC HAVOC loop is wrong");
		    throw new NetIbisException("loop is wrong");
		}
		maxAcked = i * NetConvert.BITS_PER_INT + bit + 1;
		break;
	    }
	}

	if (DEBUG_ACK) {
	    System.err.println("Ack bit set: maxNonemptyIndex=" +
		    maxNonemptyIndex + " maxAcked=" + maxAcked);
	}

	if (maxAcked == 0) {
	    if (DEBUG_ACK) {
		System.err.println("No missing packets in ack bitset");
	    }
	} else {

	    long now = System.currentTimeMillis();
	    int	toAck = ackOffset;

	ack_set_loop:
	    for (int i = 0; i < maxNonemptyIndex; i++) {
		int mask = 1;
		for (int bit = 0; bit < NetConvert.BITS_PER_INT; bit++) {
		    if (scan != null && scan.fragCount > toAck) {
			if (DEBUG_ACK) {
			    System.err.println("It seems data channel and " +
				    "control channel overtake each other; " +
				    "get " + scan.fragCount +
				    " would expect " + toAck);
			}
		    } else {
			if (scan == null ||
				i * NetConvert.BITS_PER_INT + bit >= maxAcked ||
				scan.fragCount - windowStart >= windowSize) {
			    if (DEBUG_ACK) {
				System.err.println("Ack bit set done: scan " +
					(scan == null ? -1 : scan.fragCount) +
					" ack offset " +
					(i * NetConvert.BITS_PER_INT + bit));
			    }
			    break ack_set_loop;
			}

			if (! scan.sent) {
			    System.err.println("HAVOC HAVOC HAVOC ack " +
				    "mechanism is wrong -- missing reported " +
				    "that's not even been sent");
			    throw new NetIbisException("missing reported that " +
				    "has not yet been sent");
			}

			if (scan.acked) {
			    if (DEBUG_ACK) {
				System.err.println("Ack packet " +
					scan.fragCount + " from bitset --- " +
					"already acked before");
			    }
			} else if ((mask & ackReceiveSet[i]) != 0) {
			    scan.acked = true;
			    if (DEBUG_ACK) {
				System.err.println("Ack packet " +
					scan.fragCount + " from bitset");
			    }
			// } else if (now - scan.lastSent < safetyInterval) {
			} else {
			    if (DEBUG_ACK || DEBUG_REXMIT) {
				System.err.println("Packet " + scan.fragCount +
					" misses from bitset, rexmit(NACK)");
			    }
			    nRexmit_nack++;
			    doSendBuffer(scan, now);
			}

			scan = scan.next;
		    }
		    mask <<= 1;
		    toAck++;
		}
		offset += NetConvert.BITS_PER_INT;
	    }

	    while (front != null && front.acked) {
		scan = front;
		front = front.next;
		windowStart++;
		scan.free();
	    }
	}

	handleSendContinuation();
    }


    // call this synchronized
// synchronized
    private void handleRexmit() throws NetIbisException {
	long now = System.currentTimeMillis();

	if (DEBUG_REXMIT) {
	    System.err.println("Rexmit: window start " + windowStart +
		    " nextAllocate " + nextAllocate);
	}
	for (RelSendBuffer scan = front;
	     scan != null &&
		scan.sent &&	/* Don't send unsent packets, that
				     * corrupts nextToSend */
		scan.fragCount - windowStart < windowSize &&
		now - scan.lastSent > safetyInterval;
	     scan = scan.next) {
	    if (DEBUG_REXMIT) {
		System.err.println("Rexmit packet " + scan.fragCount +
			"; now - lastSent " + (now - scan.lastSent));
	    }
	    if (STATISTICS) {
		nRexmit++;
	    }
	    doSendBuffer(scan, now);
	}
    }


    public void invoke() {
	if (controlInput == null) {
	    // Not yet started
	    return;
	}

	try {
	    synchronized (this) {
		pollControlChannel();
		handleRexmit();
	    }
	} catch (NetIbisException e) {
	    System.err.println("RelOutput hanldeRexmit throws " + e);
	}
    }


    /**
     * {@inheritDoc}
     */
    synchronized
    public void sendByteBuffer(NetSendBuffer b) throws NetIbisException {
	if (DEBUG) {
	    System.err.println("Try to send a buffer size " + b.length);
	    Thread.dumpStack();
	}

	RelSendBuffer rb = (RelSendBuffer)b;
	if (rb.ownershipClaimed) {
	    rb = (RelSendBuffer)rb.makeCopy();
	}
	rb.ownershipClaimed = true;

	if (DEBUG) {
	    if (rb.sent) {
		System.err.println(this + ": HAVOC ##################### unsent packet has -sent- bit already set!");
	    }
	    if (rb.acked) {
		System.err.println(this + ": HAVOC ##################### unsent packet has -acked- bit already set!");
	    }
	    if (rb.lastSent != -1) {
		System.err.println(this + ": HAVOC ##################### unsent packet has lastSent field already set!");
	    }
	}

	rb.fragCount = nextAllocate;
	nextAllocate++;
	if (DEBUG) {
	    System.err.println("Write fragCount " +
		    (rb.fragCount & ~LAST_FRAG_BIT) + "(" + rb.fragCount +
		    ") at offset " + headerStart);
	}
	NetConvert.writeInt(rb.fragCount, rb.data, headerStart);
	enqueueSendBuffer(rb);
    }


    /**
     * {@inheritDoc}
     */
    public void initSend() throws NetIbisException {
	    super.initSend();
	    dataOutput.initSend();
    }


    void report() {
	if (STATISTICS) {
	    System.out.println(this + ": Packets " + nextAllocate + " rexmit " + nRexmit + "; nRexmit(nack) " + nRexmit_nack);
	}
    }


    /**
     * {@inheritDoc}
     */
    public void finish() throws NetIbisException {
	    super.finish();
	    dataOutput.finish();
    }

    /**
     * {@inheritDoc}
     */
    public void free() throws NetIbisException {
	Thread.dumpStack();

	report();

	if (dataOutput != null) {
	    dataOutput.free();
	}
	if (controlInput != null) {
	    controlInput.free();
	}

	super.free();
    }

    synchronized public void close(Integer num) throws NetIbisException {
            // to implement
            //
            // - 'num' is the is the Integer identifier of the connection to close
            // - close should not complain is the connection is already closed
            // - close must be synchronized with its counterpart 'setupconnection'
            // - close should not block if possible (except for sync with 
            //   setupconnection)
            // - close should not free the input/output objects which can be used
            //   for subsequent new connections
            // - close is supposed to be called by the NetIbis port layer 
            //   when the peer port is closed
            // - close is also supposed to be called by the user once the IPL
            //   provide the feature, to remove a connection from the connection set
            //   of the port
            // - close must be carefully designed to avoid race conditions with upcall
            //   handling and to avoid distributed deadlocks
    }

}
