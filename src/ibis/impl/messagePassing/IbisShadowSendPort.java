package ibis.ipl.impl.messagePassing;

import ibis.io.IbisSerializationInputStream;

import ibis.ipl.IbisIOException;

final class IbisShadowSendPort extends ShadowSendPort {

    IbisSerializationInputStream obj_in;

    /* Create a shadow SendPort, used by the local ReceivePort to refer to */
    IbisShadowSendPort(ReceivePortIdentifier rId, SendPortIdentifier sId)
	    throws IbisIOException {
	super(rId, sId);
// System.err.println("In IbisShadowSendPort.<init>");
	try {
	    obj_in = new IbisSerializationInputStream(new ArrayInputStream(in));
	} catch(java.io.IOException e) {
	    throw new IbisIOException("got exception", e);
	}
    }


    ReadMessage getMessage(int msgSeqno) {
	ReadMessage msg = cachedMessage;

	if (Ibis.DEBUG) {
	    System.err.println("Get a Ibis ReadMessage ");
	    System.err.println(" >>>>>> >>>>>>> >>>>>>> Don't forget to set the stream in the ReadMessage");
	}

	if (msg != null) {
	    cachedMessage = null;

	} else {
	    msg = new IbisReadMessage(this, receivePort);
	    if (Ibis.DEBUG) {
		System.err.println("Create an -ibis-serialization- ReadMessage " + msg); 
	    }
	}

	msg.msgSeqno = msgSeqno;
	return msg;
    }

}
