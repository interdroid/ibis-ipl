package ibis.ipl.impl.net.tcp;

import ibis.ipl.impl.net.__;
import ibis.ipl.impl.net.NetDriver;
import ibis.ipl.impl.net.NetInput;
import ibis.ipl.impl.net.NetReceiveBuffer;
import ibis.ipl.impl.net.NetSendPortIdentifier;

import ibis.ipl.IbisException;
import ibis.ipl.IbisIOException;
import ibis.ipl.StaticProperties;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;

/* Only for java >= 1.4 
import java.net.SocketTimeoutException;
*/
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import java.util.Hashtable;


/**
 * The TCP input implementation.
 */
public class TcpInput extends NetInput {

	/**
	 * The connection socket.
	 */
	private ServerSocket 	      tcpServerSocket = null;

	/**
	 * The communication socket.
	 */
	private Socket                tcpSocket       = null;

	/**
	 * The peer {@link ibis.ipl.impl.net.NetSendPort NetSendPort}
	 * local number.
	 */
	private Integer               rpn  	      = null;

	/**
	 * The communication input stream.
	 */
	private InputStream  	      tcpIs	      = null;

	/**
	 * The communication output stream.
	 *
	 * <BR><B>Note</B>: this stream is not really needed but may be used 
	 * for debugging purpose.
	 */
	private OutputStream 	      tcpOs	      = null;

	/**
	 * Constructor.
	 *
	 * @param sp the properties of the input's 
	 * {@link ibis.ipl.impl.net.NetSendPort NetSendPort}.
	 * @param driver the TCP driver instance.
	 * @param input the controlling input.
	 */
	TcpInput(StaticProperties sp,
		 NetDriver        driver,
		 NetInput         input)
		throws IbisIOException {
		super(sp, driver, input);
	}


	/*
	 * Sets up an incoming TCP connection.
	 *
	 * @param rpn {@inheritDoc}
	 * @param is {@inheritDoc}
	 * @param os {@inheritDoc}
	 */
	public void setupConnection(Integer                rpn,
				    ObjectInputStream 	   is,
				    ObjectOutputStream	   os)
		throws IbisIOException {
		this.rpn = rpn;
		 
		try {
			tcpServerSocket   = new ServerSocket(0, 1, InetAddress.getLocalHost());
			Hashtable info    = new Hashtable();
			info.put("tcp_address", tcpServerSocket.getInetAddress());
			info.put("tcp_port",    new Integer(tcpServerSocket.getLocalPort()));
			sendInfoTable(os, info);

			tcpSocket  = tcpServerSocket.accept();
			tcpIs 	   = tcpSocket.getInputStream();
			tcpOs 	   = tcpSocket.getOutputStream();
		} catch (IOException e) {
			throw new IbisIOException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * <BR><B>Note</B>: This TCP polling implementation uses the
	 * {@link java.io.InputStream#available()} function to test whether at least one
	 * data byte may be extracted without blocking.
	 *
	 * @return {@inheritDoc}
	 */
	public Integer poll() throws IbisIOException {
                //System.err.println("TCP: doPoll -->");
		activeNum = null;

		if (rpn == null) {
			return null;
		}

		try {
			if (tcpIs.available() > 0) {
				activeNum = rpn;
			}
		} catch (IOException e) {
			throw new IbisIOException(e);
		} 
                //System.err.println("TCP: doPoll <--");

		return activeNum;
	}

	public byte readByte() throws IbisIOException {
                byte result = 0;
                
		try {
                        result = (byte)tcpIs.read();
		} catch (IOException e) {
			throw new IbisIOException(e);
		}
   
                return result;
        }
        
	/**
	 * {@inheritDoc}
	 */
	public void free() throws IbisIOException {
		try {
			if (tcpOs != null) {
                                tcpOs.close();
                                tcpOs = null;
			}
		
			if (tcpIs != null) {
				tcpIs.close();
                                tcpIs = null;
			}

			if (tcpSocket != null) {
                                tcpSocket.close();
                                tcpSocket = null;
			}

			if (tcpServerSocket != null) {
                                tcpServerSocket.close();
                                tcpServerSocket = null;

			}

			rpn = null;
		}
		catch (Exception e) {
			throw new IbisIOException(e);
		}

		super.free();
	}
}
