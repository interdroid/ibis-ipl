package ibis.ipl.impl.net;

import ibis.io.ArrayInputStream;
import ibis.io.SerializationInputStream;

import ibis.ipl.impl.net.*;

import java.util.Hashtable;



//2     8      16      24      32      40      48      56      64      72      80      88      96     104     112
//......!.......!.......!.......!.......!.......!.......!.......!.......!.......!.......!.......!.......!.......!.......
//      |       |       |       |       |       |       |       |       |       |       |       |       |       |

/**
 * The ID input implementation.
 */
public abstract class NetSerializedInput extends NetInput {


	/**
	 * The driver used for the 'real' input.
	 */
	protected                       NetDriver                       subDriver               = null;
       
	/**       
	 * The 'real' input.       
	 */       
	protected                       NetInput                        subInput                = null;

        /**
         * The currently active {@linkplain SerializationInputStream serialization input stream}, or <code>null</code>.
         */
        private         volatile        SerializationInputStream        iss                     = null;

        /**
         * The table containing each {@linkplain SerializationInputStream serialization input stream}.
         *
         * The table is indexed by connection numbers.
         */
	private                         Hashtable                       streamTable             = null;

        /**
         * The most recently activated upcall thread if it is still alive, or <code>null</code>.
         */
        protected       volatile        Thread                          activeUpcallThread      = null;



	public NetSerializedInput(NetPortType pt, NetDriver driver, String context) throws NetIbisException {
		super(pt, driver, context);
                streamTable = new Hashtable();
	}

	/**
	 * {@inheritDoc}
	 */
	public synchronized void setupConnection(NetConnection cnx) throws NetIbisException {
                log.in();
		NetInput subInput = this.subInput;

		if (subInput == null) {
			if (subDriver == null) {
                                String subDriverName = getMandatoryProperty("Driver");
				subDriver = driver.getIbis().getDriver(subDriverName);
			}

			subInput = newSubInput(subDriver);
			this.subInput = subInput;
		}
		
                if (upcallFunc != null) {
                        subInput.setupConnection(cnx, this);
                } else {
                        subInput.setupConnection(cnx, null);
                }
                log.out();
	}

        public abstract SerializationInputStream newSerializationInputStream() throws NetIbisException;
        

	public void initReceive() throws NetIbisException {
                log.in();
                byte b = subInput.readByte();

                if (b != 0) {
                        iss = newSerializationInputStream();
                        if (activeNum == null) {
                                throw new Error("invalid state: activeNum is null");
                        }
                        
                        if (iss == null) {
                                throw new Error("invalid state: stream is null");
                        }
                        
                        streamTable.put(activeNum, iss);
                } else {
                        iss = (SerializationInputStream)streamTable.get(activeNum);
                        
                        if (iss == null) {
                                throw new Error("invalid state: stream not found");
                        }
                }
                log.out();
	}

        public void inputUpcall(NetInput input, Integer spn) throws NetIbisException {
                log.in();
                synchronized(this) {
                        while (activeNum != null) {
                                try {
                                        wait();
                                } catch (InterruptedException e) {
                                        throw new NetIbisInterruptedException(e);
                                }
                        }
                        
                        if (spn == null) {
                                throw new Error("invalid connection num");
                        }
                        
                        activeNum = spn;
                        activeUpcallThread = Thread.currentThread();
                        // System.err.println("NetSerializedInput["+this+"]: inputUpcall - activeNum = "+activeNum);
                }

                mtu          = subInput.getMaximumTransfertUnit();
                headerOffset = subInput.getHeadersLength();
                initReceive();
                upcallFunc.inputUpcall(this, spn);
                synchronized(this) {
                        if (activeNum == spn && activeUpcallThread == Thread.currentThread()) {
                                activeNum = null;
                                activeUpcallThread = null;
                                iss = null;
                                notifyAll();
                                // System.err.println("NetSerializedInput["+this+"]: inputUpcall - activeNum = "+activeNum);
                        }
                }
                        
                log.out();
        }

	public synchronized Integer poll(boolean block) throws NetIbisException {
                log.in();
                if (activeNum != null) {
                        throw new Error("invalid call");
                }

                if (subInput == null) {
                        log.out();
                        return null;
                }
                
                Integer result = subInput.poll(block);
                if (result != null) {
                        activeNum = result;
                        mtu          = subInput.getMaximumTransfertUnit();
                        headerOffset = subInput.getHeadersLength();
                        initReceive();
                }

                log.out();
		return result;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void finish() throws NetIbisException {
                log.in();
                //iss.close();
		super.finish();
		subInput.finish();
                synchronized(this) {
                        iss = null;
                        activeNum = null;
                        activeUpcallThread = null;
                        notifyAll();
                        // System.err.println("NetSerializedInput: finish - activeNum = "+activeNum);
                }

                log.out();
	}

        public synchronized void close(Integer num) throws NetIbisException {
                log.in();
                if (subInput != null) {
                        subInput.close(num);
                }
                log.out();
        }
        

	/**
	 * {@inheritDoc}
	 */
	public void free() throws NetIbisException {
                log.in();
		if (subInput != null) {
			subInput.free();
		}

		super.free();
                log.out();
	}
	

        public NetReceiveBuffer readByteBuffer(int expectedLength) throws NetIbisException {
                log.in();
                NetReceiveBuffer b = subInput.readByteBuffer(expectedLength);
                log.out();
                return b;
        }       

        public void readByteBuffer(NetReceiveBuffer buffer) throws NetIbisException {
                log.in();
                subInput.readByteBuffer(buffer);
                log.out();
        }

	public boolean readBoolean() throws NetIbisException {
                boolean b = false;

                log.in();
		try {
                        b = iss.readBoolean();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}

                log.out();
                return b;
        }
        

	public byte readByte() throws NetIbisException {
                byte b = 0;

                log.in();
		try {
                        b = iss.readByte();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();

                return b;
        }
        

	public char readChar() throws NetIbisException {
                char c = 0;

                log.in();
		try {
                        c = iss.readChar();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
                
                return c;
        }


	public short readShort() throws NetIbisException {
                short s = 0;

                log.in();
		try {
                        s = iss.readShort();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();

                return s;
        }


	public int readInt() throws NetIbisException {
                int i = 0;

                log.in();
		try {
                        i = iss.readInt();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();

                return i;
        }


	public long readLong() throws NetIbisException {
                long l = 0;

                log.in();
		try {
                        l = iss.readLong();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();

                return l;
        }

	
	public float readFloat() throws NetIbisException {
                float f = 0.0f;

                log.in();
		try {
                        f = iss.readFloat();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();

                return f;
        }


	public double readDouble() throws NetIbisException {
                double d = 0.0;
                
                log.in();
		try {
                        d = iss.readDouble();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();

                return d;
        }


	public String readString() throws NetIbisException {
                String s = null;

                log.in();
		try {
                        s = (String)iss.readObject();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		} catch(ClassNotFoundException e2) {
                        throw new NetIbisException("got exception", e2);
		}
                log.out();

                return s;
        }


	public Object readObject() throws NetIbisException {
                Object o = null;

                log.in();
		try {
                        o = iss.readObject();
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		} catch(ClassNotFoundException e2) {
                        throw new NetIbisException("got exception", e2);
		}
                log.out();

                return o;
        }

	public void readArraySliceBoolean(boolean [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceBoolean(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceByte(byte [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceByte(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceChar(char [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceChar(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceShort(short [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceShort(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceInt(int [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceInt(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceLong(long [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceLong(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceFloat(float [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceFloat(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }


	public void readArraySliceDouble(double [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceDouble(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		}
                log.out();
        }

	public void readArraySliceObject(Object [] b, int o, int l) throws NetIbisException {
                log.in();
		try {
                        iss.readArraySliceObject(b, o, l);
		} catch(java.io.IOException e) {
                        throw new NetIbisException("got exception", e);
		} catch(ClassNotFoundException e2) {
                        throw new NetIbisException("got exception", e2);
		}
                log.out();
        }
}
