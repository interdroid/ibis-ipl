package ibis.impl.nio;

import ibis.ipl.IbisError;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

final class SendBuffer implements Config {

    //primitives are send in order of size, largest first
    private final int HEADER = 0;
    private final int LONGS = 1;
    private final int DOUBLES = 2;
    private final int INTS = 3;
    private final int FLOATS = 4;
    private final int SHORTS = 5;
    private final int CHARS = 6;
    private final int BYTES = 7;
    private final int PADDING = 8;
    private final int NR_OF_BUFFERS = 9;

    /**
     * The header contains 1 byte for the byte order,
     * one byte indicating the length of the padding at the end of the 
     * packet (in bytes), and 7 shorts (14 bytes) for the number of each
     * primitive send (in bytes!)
     */
    private final int SIZEOF_HEADER = 16;

    private final int SIZEOF_PADDING = 8;

    public static final int SIZEOF_BYTE = 1;
    public static final int SIZEOF_CHAR = 2;
    public static final int SIZEOF_SHORT = 2;
    public static final int SIZEOF_INT = 4;
    public static final int SIZEOF_LONG = 8;
    public static final int SIZEOF_FLOAT = 4;
    public static final int SIZEOF_DOUBLE = 8;


    static final int BUFFER_CACHE_SIZE = 128;

    static SendBuffer[] cache = new SendBuffer[BUFFER_CACHE_SIZE];
    static int cacheSize = 0;

    /**
     * Static method to get a sendbuffer out of the cache
     */
    synchronized static SendBuffer get() {
	if(cacheSize > 0) {
	    if (DEBUG) {
		Debug.message("buffers", null, 
			"SendBuffer: got empty buffer from cache");
	    }
	    cacheSize--;
	    cache[cacheSize].clear();
	    return cache[cacheSize];
	} else {
	    if (DEBUG) {
		Debug.message("buffers", null, 
			"SendBuffer: got new empty buffer");
	    }
	    return new SendBuffer();
	}
    }

    /**
     * static method to put a buffer in the cache
     */
    synchronized static void recycle(SendBuffer buffer) {
	if(buffer.parent == null) {
	    if(buffer.copies != 0) {
		throw new IbisError("tried to recycly buffer with children!");
	    }
	    if(cacheSize >= BUFFER_CACHE_SIZE) {
		if (DEBUG) {
		    Debug.message("buffers", null, "SendBuffer: cache full"
			    + " apon recycling buffer, throwing away");
		}
		return;
	    }
	    cache[cacheSize] = buffer;
	    cacheSize++;
	    if (DEBUG) {
		Debug.message("buffers", null, "SendBuffer: recycled buffer");
	    }
	} else {
	    if (DEBUG) {
		Debug.message("buffers", null, 
					"SendBuffer: recycling child buffer");
	    }

	    buffer.parent.copies--;
	    if(buffer.parent.copies == 0) {
		if(cacheSize >= BUFFER_CACHE_SIZE) {
		    if (DEBUG) {
			Debug.message("buffers", null, "SendBuffer: cache full"
			    + " apon recycling parent of child buffer,"
			    + " throwing away");
		    }
		    return;
		}
		cache[cacheSize] = buffer.parent;
		cacheSize++;
		if (DEBUG) {
		    Debug.message("buffers", null, 
					"SendBuffer: recycled parent buffer");
		}
	    }
	}
    }

    /**
     * copies a buffer, records how may copies are made so far
     */
    synchronized static SendBuffer duplicate(SendBuffer original) {
	SendBuffer result = new SendBuffer(original);
	original.copies += 1;
	return result;
    }

    //number of copies that exist of this buffer
    private int copies = 0;

    //original buffer this buffer is a copy of (if applicable)
    SendBuffer parent = null;

    private static long nextSequenceNr = 0;

    ShortBuffer header;
    LongBuffer longs;
    DoubleBuffer doubles;
    IntBuffer ints;
    FloatBuffer floats;
    ShortBuffer shorts;
    CharBuffer chars;
    ByteBuffer bytes;

    ByteBuffer[] byteBuffers;

    /**
     * Used to keep track of a buffer. Recycling a buffer will reset its
     * sequence number.
     */
    long sequenceNr;

    SendBuffer() {
	ByteOrder order = ByteOrder.nativeOrder();

	byteBuffers = new ByteBuffer[NR_OF_BUFFERS];
	byteBuffers[HEADER] = ByteBuffer.allocateDirect(
		SIZEOF_HEADER).order(order);
	byteBuffers[PADDING] = ByteBuffer.allocateDirect(
		SIZEOF_PADDING).order(order);

	//put the byte order in the first byte of the header
	if(order == ByteOrder.BIG_ENDIAN) {
	    byteBuffers[HEADER].put(0, (byte) 1);
	} else {
	    byteBuffers[HEADER].put(0, (byte) 0);
	}

	for(int i = 1; i < (NR_OF_BUFFERS - 1); i++) {
	    byteBuffers[i] = ByteBuffer
		.allocateDirect(PRIMITIVE_BUFFER_SIZE).order(order);
	}

	header = byteBuffers[HEADER].asShortBuffer();
	longs = byteBuffers[LONGS].asLongBuffer();
	doubles = byteBuffers[DOUBLES].asDoubleBuffer();
	ints = byteBuffers[INTS].asIntBuffer();
	floats = byteBuffers[FLOATS].asFloatBuffer();
	shorts = byteBuffers[SHORTS].asShortBuffer();
	chars = byteBuffers[CHARS].asCharBuffer();
	bytes = byteBuffers[BYTES];

	clear();
    }

    /**
     * Copy constructor. Acutally only copies byteBuffers;
     */
    SendBuffer(SendBuffer parent) {
	this.parent = parent;

	byteBuffers = new ByteBuffer[NR_OF_BUFFERS];
	for(int i = 0; i < NR_OF_BUFFERS; i++) {
	    byteBuffers[i] = parent.byteBuffers[i].duplicate();
	}
    }


    /**
     *

    /**
     * Resets a buffer as though it's a newly created buffer. Sets the
     * sequencenr to a new value
     */
    void clear() {
	header.clear();
	longs.clear();
	doubles.clear();
	ints.clear();
	floats.clear();
	shorts.clear();
	chars.clear();
	bytes.clear();

	sequenceNr = nextSequenceNr;
	nextSequenceNr++;
    }

    /**
     * Make a (partially) filled buffer ready for sending
     */
    void flip() {
	int paddingLength;

	//fill header with the size of the primitive arrays (in bytes)
	header.clear();
	header.put(LONGS, ((short) (longs.position() * SIZEOF_LONG)));
	header.put(DOUBLES, ((short) (doubles.position() * SIZEOF_DOUBLE)));
	header.put(INTS, ((short) (ints.position() * SIZEOF_INT)));
	header.put(FLOATS, ((short) (floats.position() * SIZEOF_FLOAT)));
	header.put(SHORTS, ((short) (shorts.position() * SIZEOF_SHORT)));
	header.put(CHARS, ((short) (chars.position() * SIZEOF_CHAR)));
	header.put(BYTES, ((short) (bytes.position() * SIZEOF_BYTE)));

	//set up primitive buffers so they can be send
	byteBuffers[HEADER].position(0).limit(SIZEOF_HEADER);
	byteBuffers[LONGS].limit(longs.position() * SIZEOF_LONG)
	    .position(0);
	byteBuffers[DOUBLES].limit(doubles.position() * SIZEOF_DOUBLE)
	    .position(0);
	byteBuffers[INTS].limit(ints.position() * SIZEOF_INT)
	    .position(0);
	byteBuffers[FLOATS].limit(floats.position() * SIZEOF_FLOAT)
	    .position(0);
	byteBuffers[SHORTS].limit(shorts.position() * SIZEOF_SHORT)
	    .position(0);
	byteBuffers[CHARS].limit(chars.position() * SIZEOF_CHAR)
	    .position(0);
	byteBuffers[BYTES].flip();

	//add padding to make the total nr of bytes send a multiple of eight

	//find out length of padding we need
	byteBuffers[PADDING].limit(0);
	paddingLength = (int) (8 - (remaining() % 8));
	byteBuffers[PADDING].position(0).limit(paddingLength);

	//put a byte in the header indicating the length of the paddding
	byteBuffers[HEADER].put(1, (byte) paddingLength);

	if (DEBUG) {
	    Debug.message("buffers", this, "flipping buffer, sending: l[" 
		    + longs.position()
		    + "] d[" + doubles.position()
		    + "] i[" + ints.position()
		    + "] f[" + floats.position()
		    + "] s[" + shorts.position()
		    + "] c[" + chars.position()
		    + "] b[" + bytes.remaining()
		    + "] total size: " + remaining() + " padding size: "
		    + paddingLength);
	}

    }

    /**
     * set a mark on all Byte Buffers 
     */
    void mark() {
	for (int i = 0; i < byteBuffers.length; i++) {
	    byteBuffers[i].mark();
	}
    }

    /**
     * reset all Byte Buffers
     */
    void reset() {
	for (int i = 0; i < byteBuffers.length; i++) {
	    byteBuffers[i].reset();
	}
    }

    /**
     * returns the number of remaining bytes in the bytebuffers
     */
    long remaining() {
	long result = 0;
	for (int i = 0; i < byteBuffers.length; i++) {
	    result += byteBuffers[i].remaining();
	}
	return result;
    }

    /**
     * returns if this buffer is empty (before flipping)
     */
    boolean isEmpty() {
	return ( (longs.position() == 0)
		&& (doubles.position() == 0)
		&& (ints.position() == 0)
		&& (floats.position() == 0)
		&& (shorts.position() == 0)
		&& (chars.position() == 0)
		&& (bytes.position() == 0));
    }


    /**
     * Returns if this buffer has any data remaining in it.
     * Only works _after_ it has been flipped!
     */
    boolean hasRemaining() {
	return byteBuffers[PADDING].hasRemaining();
    }

}
