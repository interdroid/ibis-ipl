package ibis.ipl.impl.messagePassing;

import java.util.Vector;

import ibis.ipl.IbisIOException;
import ibis.io.Replacer;
import ibis.ipl.impl.generic.ConditionVariable;

public class SendPort implements ibis.ipl.SendPort, Protocol {

    ibis.ipl.impl.messagePassing.PortType type;
    SendPortIdentifier ident;
    Replacer replacer;

    ReceivePortIdentifier[] splitter;
    Syncer[] syncer;

    String name;

    boolean aMessageIsAlive = false;
    ConditionVariable portIsFree;
    int newMessageWaiters;
    int messageCount;

    /*
     * If one of the connections is a Home connection, do some polls
     * after our send to see to it that the receive side doesn't have
     * to await a time slice.
     */
    private boolean homeConnection;
    final private static int homeConnectionPolls = 4;

    ibis.ipl.impl.messagePassing.WriteMessage message = null;

    OutputConnection outConn;

    ibis.ipl.impl.messagePassing.ByteOutputStream out;


    SendPort() {
    }

    public SendPort(ibis.ipl.impl.messagePassing.PortType type,
		    String name,
		    OutputConnection conn,
		    Replacer r,
		    boolean syncMode,
		    boolean makeCopy)
	    throws IbisIOException {
	this.name = name;
	this.type = type;
	this.replacer = r;
	ident = new SendPortIdentifier(name, type.name());
	portIsFree = ibis.ipl.impl.messagePassing.Ibis.myIbis.createCV();
	outConn = conn;
	out = new ByteOutputStream(this, syncMode, makeCopy);
    }

    public SendPort(ibis.ipl.impl.messagePassing.PortType type, String name, OutputConnection conn) throws IbisIOException {
	this(type, name, conn, null, true, false);
    }


    int addConnection(ReceivePortIdentifier rid)
	    throws IbisIOException {

	int	my_split;
	if (splitter == null) {
	    my_split = 0;
	} else {
	    my_split = splitter.length;
	}

	if (rid.cpu < 0) {
	    throw new IbisIOException("invalid ReceivePortIdentifier");
	}

	// ibis.ipl.impl.messagePassing.Ibis.myIbis.checkLockNotOwned();

	if (ibis.ipl.impl.messagePassing.Ibis.DEBUG) {
	    System.out.println(name + " connecting to " + rid);
	}

	if (!type.name().equals(rid.type())) {
	    throw new IbisIOException("Cannot connect ports of different PortTypes: " + type.name() + " vs. " + rid.type());
	}

	int n;

	if (splitter == null) {
	    n = 0;
	} else {
	    n = splitter.length;
	}

	ReceivePortIdentifier[] v = new ReceivePortIdentifier[n + 1];
	for (int i = 0; i < n; i++) {
	    v[i] = splitter[i];
	}
	v[n] = rid;
	splitter = v;

	Syncer[] s = new Syncer[n + 1];
	for (int i = 0; i < n; i++) {
	    s[i] = syncer[i];
	}
	s[n] = new Syncer();
	syncer = s;

	return my_split;
    }


    public void connect(ibis.ipl.ReceivePortIdentifier receiver,
			int timeout)
	    throws IbisIOException {

	ibis.ipl.impl.messagePassing.Ibis.myIbis.lock();
	try {
	    ReceivePortIdentifier rid = (ReceivePortIdentifier)receiver;

	    // Add the new receiver to our tables.
	    int my_split = addConnection(rid);

	    if (ibis.ipl.impl.messagePassing.Ibis.DEBUG) {
		System.err.println(Thread.currentThread() + "Now do native connect call to " + rid + "; me = " + ident);
	    }
	    IbisIdentifier ibisId = (IbisIdentifier)Ibis.myIbis.identifier();
	    outConn.ibmp_connect(rid.cpu, rid.port, ident.port, ident.type,
				 ibisId.name(), ibisId.getSerialForm(),
				 syncer[my_split], type.serializationType);
	    if (ibis.ipl.impl.messagePassing.Ibis.DEBUG) {
		System.err.println(Thread.currentThread() + "Done native connect call to " + rid + "; me = " + ident);
	    }

	    if (! syncer[my_split].s_wait(timeout)) {
		throw new ibis.ipl.IbisConnectionTimedOutException("No connection to " + rid);
	    }
	    if (! syncer[my_split].accepted) {
		throw new ibis.ipl.IbisConnectionRefusedException("No connection to " + rid);
	    }

	    if (ident.ibis().equals(receiver.ibis())) {
		homeConnection = true;
// System.err.println("This IS a home connection, my Ibis " + ident.ibis() + " their Ibis " + receiver.ibis());
	    } else {
// System.err.println("This is NOT a home connection, my Ibis " + ident.ibis() + " their Ibis " + receiver.ibis());
// Thread.dumpStack();
	    }
	} finally {
	    ibis.ipl.impl.messagePassing.Ibis.myIbis.unlock();
	}
    }


    public void connect(ibis.ipl.ReceivePortIdentifier receiver) throws IbisIOException {
	connect(receiver, 0);
    }


    ibis.ipl.WriteMessage cachedMessage() throws IbisIOException {
	if (message == null) {
	    message = new WriteMessage(this);
	}

	return message;
    }


    public ibis.ipl.WriteMessage newMessage() throws IbisIOException {

	ibis.ipl.impl.messagePassing.Ibis.myIbis.lock();
	while (aMessageIsAlive) {
	    newMessageWaiters++;
	    portIsFree.cv_wait();
	    newMessageWaiters--;
	}

	aMessageIsAlive = true;
	ibis.ipl.impl.messagePassing.Ibis.myIbis.unlock();

	ibis.ipl.WriteMessage m = cachedMessage();
	if (ibis.ipl.impl.messagePassing.Ibis.DEBUG) {
	    System.err.println("Create a new writeMessage SendPort " + this + " serializationType " + type.serializationType + " message " + m);
	}

	return m;
    }


    void registerSend() throws IbisIOException {
	messageCount++;
	if (homeConnection) {
	    for (int i = 0; i < homeConnectionPolls; i++) {
		while (Ibis.myIbis.pollLocked());
	    }
	}
    }


    void reset() {
	// ibis.ipl.impl.messagePassing.Ibis.myIbis.checkLockOwned();
	aMessageIsAlive = false;
	if (newMessageWaiters > 0) {
	    portIsFree.cv_signal();
	}
    }

    public ibis.ipl.DynamicProperties properties() {
	return null;
    }

    public ibis.ipl.SendPortIdentifier identifier() {
	return ident;
    }


    public void free() throws IbisIOException {
	if (ibis.ipl.impl.messagePassing.Ibis.DEBUG) {
	    System.out.println(type.myIbis.name() + ": ibis.ipl.SendPort.free " + this + " start");
	}

	ibis.ipl.impl.messagePassing.Ibis.myIbis.lock();
	try {
	    for (int i = 0; i < splitter.length; i++) {
		ReceivePortIdentifier rid = splitter[i];
		outConn.ibmp_disconnect(rid.cpu, rid.port, ident.port, messageCount);
	    }
	} finally {
	    ibis.ipl.impl.messagePassing.Ibis.myIbis.unlock();
	}

	if (ibis.ipl.impl.messagePassing.Ibis.DEBUG) {
	    System.out.println(type.myIbis.name() + ": ibis.ipl.SendPort.free " + this + " DONE");
	}
    }

}
