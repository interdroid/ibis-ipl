package ibis.impl.net.tcp_plain;

import ibis.impl.net.NetAllocator;
import ibis.impl.net.NetConnection;
import ibis.impl.net.NetDriver;
import ibis.impl.net.NetInput;
import ibis.impl.net.NetInputUpcall;
import ibis.impl.net.NetPortType;
import ibis.ipl.ConnectionClosedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;

/**
 * The TCP input implementation.
 */
public final class TcpInput extends NetInput {

	/**
	 * The connection socket.
	 */
	private ServerSocket 	      tcpServerSocket = null;

	/**
	 * The communication socket.
	 */
	private Socket                tcpSocket       = null;

	/**
	 * The peer {@link ibis.impl.net.NetSendPort NetSendPort}
	 * local number.
	 */
	private volatile Integer      spn  	      = null;

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
	 * The buffer block allocator.
	 */
	private NetAllocator          allocator      = null;

        private boolean               first          = false;
        private byte                  firstbyte      = 0;
        private InetAddress           addr           = null;
        private int                   port           =    0;

	/**
	 * Constructor.
	 *
	 * @param pt the properties of the input's
	 * {@link ibis.impl.net.NetSendPort NetSendPort}.
	 * @param driver the TCP driver instance.
	 */
	TcpInput(NetPortType pt, NetDriver driver, String context, NetInputUpcall inputUpcall)
		throws IOException {
		super(pt, driver, context, inputUpcall);
		headerLength = 0;
	}

	/*
	 * Sets up an incoming TCP connection.
	 *
	 * @param spn {@inheritDoc}
	 * @param is {@inheritDoc}
	 * @param os {@inheritDoc}
	 */
	public synchronized void setupConnection(NetConnection cnx)
		throws IOException {
                if (this.spn != null) {
                        throw new Error("connection already established");
                }

		tcpServerSocket   = new ServerSocket(0, 1, InetAddress.getLocalHost());
		Hashtable lInfo    = new Hashtable();
		lInfo.put("tcp_address", tcpServerSocket.getInetAddress());
		lInfo.put("tcp_port",    new Integer(tcpServerSocket.getLocalPort()));
		ObjectOutputStream os = new ObjectOutputStream(cnx.getServiceLink().getOutputSubStream(this, "tcp_plain"));
		os.writeObject(lInfo);
		os.close();

		tcpSocket  = tcpServerSocket.accept();

		tcpSocket.setSendBufferSize(0x8000);
		tcpSocket.setReceiveBufferSize(0x8000);
		tcpSocket.setTcpNoDelay(true);
		addr = tcpSocket.getInetAddress();
		port = tcpSocket.getPort();

		tcpIs 	   = tcpSocket.getInputStream();
		tcpOs 	   = tcpSocket.getOutputStream();

		mtu = 0;

		this.spn = cnx.getNum();
                startUpcallThread();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <BR><B>Note</B>: This TCP polling implementation uses the
	 * {@link java.io.InputStream#available()} function to test whether at least one
	 * data byte may be extracted without blocking.
	 *
	 * @param block if true this method blocks until there is some data to read
	 *
	 * @return {@inheritDoc}
	 */
	public Integer doPoll(boolean block) throws IOException {
		if (spn == null) {
			return null;
		}

		if (block) {
			int i = tcpIs.read();

			if (i < 0)
				throw new ConnectionClosedException("Broken pipe");

			first = true;
			firstbyte = (byte)(i & 0xFF);

			return spn;
		} else if (tcpIs.available() > 0) {
			return spn;
		}

		return null;
	}

        protected void initReceive(Integer num) {
                //
        }

	/**
	 * {@inheritDoc}
	 *
	 * <BR><B>Note</B>: this function may block if the expected data is not there.
	 *
	 * @return {@inheritDoc}
	 */
	public byte readByte() throws IOException {
                byte b = 0;

                if (first) {
                        first = false;
                        b = firstbyte;
                } else {
			int i = tcpIs.read();
			if (i < 0) {
				throw new ConnectionClosedException("unexpected EOF");
			}
			b = (byte)(i & 0xFF);
                }

                return b;
	}

        public void doFinish() {
                //
        }

        public synchronized void doClose(Integer num) throws IOException {
                if (spn == num) {
			if (tcpOs != null) {
				tcpOs.close();
			}

			if (tcpIs != null) {
				tcpIs.close();
			}

			if (tcpSocket != null) {
				tcpSocket.close();
			}

			if (tcpServerSocket != null) {
				tcpServerSocket.close();
			}

                        spn = null;
                }
        }

        /**
	 * {@inheritDoc}
	 */
	public void doFree() throws IOException {
		if (tcpOs != null) {
			tcpOs.close();
		}

		if (tcpIs != null) {
			tcpIs.close();
		}

		if (tcpSocket != null) {
			tcpSocket.close();
		}

		if (tcpServerSocket != null) {
			tcpServerSocket.close();
		}

                spn = null;

		super.free();
	}

}
