package ibis.ipl.impl.messagePassing;

import ibis.ipl.IbisException;
import ibis.ipl.IbisIOException;

import ibis.ipl.impl.messagePassing.ElectionClient;
import ibis.ipl.impl.messagePassing.ElectionServer;

class Registry implements ibis.ipl.Registry {

    ReceivePortNameServer nameServer;
    ReceivePortNameServerClient nameServerClient;
    ElectionClient electionClient;
    ElectionServer electionServer;

    private final static boolean EXPORT_ELECT = true;


    Registry() throws IbisIOException {
	if (ibis.ipl.impl.messagePassing.Ibis.myIbis.myCpu == 0) {
	    if (nameServer != null) {
		throw new IbisIOException("ReceivePortNameServer already initialized");
	    }
	    nameServer = ibis.ipl.impl.messagePassing.Ibis.myIbis.createReceivePortNameServer();
	}
	nameServerClient = ibis.ipl.impl.messagePassing.Ibis.myIbis.createReceivePortNameServerClient();
    }


    void init() throws IbisException, IbisIOException {
	if (EXPORT_ELECT) {
	    if (ibis.ipl.impl.messagePassing.Ibis.myIbis.myCpu == 0) {
		if (electionServer != null) {
		    throw new IbisIOException("ReceivePortNameServer already initialized");
		}
		electionServer = new ElectionServer();
	    }
	    electionClient = new ElectionClient();
	}
    }


    void end() {
	if (EXPORT_ELECT) {
	    if (electionServer != null) {
		electionServer.end();
	    }
	    if (electionClient != null) {
		electionClient.end();
	    }
	}
    }


    public void bind(String name, ibis.ipl.ReceivePortIdentifier id) throws IbisIOException {
	nameServerClient.bind(name, (ReceivePortIdentifier)id);
    }


    public void rebind(String name, ibis.ipl.ReceivePortIdentifier id) throws IbisIOException {
	nameServerClient.unbind(name);
	nameServerClient.bind(name, (ReceivePortIdentifier)id);
    }


    public ibis.ipl.ReceivePortIdentifier lookup(String name) throws IbisIOException {
	return lookup(name, 0);
    }


    public ibis.ipl.ReceivePortIdentifier lookup(String name, long timeout) throws IbisIOException {
	return nameServerClient.lookup(name, timeout);
    }


    public void unbind(String name) throws IbisIOException {
	nameServerClient.unbind(name);
    }


    public ibis.ipl.IbisIdentifier locate(String name) throws IbisIOException {
	/* not implemented yet */
	return locate(name, 0);
    }


    public ibis.ipl.IbisIdentifier locate(String name, long millis) throws IbisIOException {
	/* not implemented yet */
	return null;
    }


    public String[] list(String pattern) throws IbisIOException {
	/* not implemented yet */
	return null;
    }

    public ibis.ipl.ReceivePortIdentifier[] query(ibis.ipl.IbisIdentifier ident) throws
	IbisIOException {
	/* not implemented yet */
	return null;
    }


    public Object elect(String election, Object candidate) throws IbisIOException {
	if (EXPORT_ELECT) {
	    return electionClient.elect(election, candidate);
	}
	throw new IbisIOException("Registry.elect not implemented");
    }
    
}
