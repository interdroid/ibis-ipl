import ibis.ipl.*;

import java.util.Properties;
import java.util.Random;

import java.io.IOException;

interface Config {
    static final boolean tracePortCreation = true;
    static final boolean traceCommunication = true;
    static final boolean traceClusterResizing = true;
}

class RszHandler implements Config, ResizeHandler {
    int members = 0;

    public void join( IbisIdentifier id )
    {
        if( traceClusterResizing ){
            System.err.println( "Join of " + id.name() );
        }
        members++;
    }

    public void leave( IbisIdentifier id )
    {
        if( traceClusterResizing ){
            System.err.println( "Leave of " + id.name() );
        }
        members--;
    }

    public void delete( IbisIdentifier id )
    {
        if( traceClusterResizing ){
            System.err.println( "Delete of " + id );
        }
        members--;
    }

    public void reconfigure()
    {
        if( traceClusterResizing ){
            System.err.println( "Reconfigure" );
        }
    }
}

class Cell1D implements Config {
    static Ibis ibis;
    static Registry registry;
    static ibis.util.PoolInfo info;

    public static void connect( SendPort s, ReceivePortIdentifier ident ) {
        boolean success = false;
        do {
            try {
                s.connect( ident );
                success = true;
            }
            catch( Exception e ) {
                try {
                    Thread.sleep( 500 );
                }
                catch( Exception e2 ) {
                    // ignore
                }
            }
        } while( !success );
    }

    private static void usage()
    {
        System.out.println( "Usage: Cell1D [count]" );
        System.exit( 0 );
    }

    /**
     * Creates an update send port that connected to the specified neighbour.
     * @param t The type of the port to construct.
     * @param me My own processor number.
     * @param procno The processor number to connect to.
     */
    private static SendPort createUpdateSendPort( PortType t, int me, int procno )
        throws java.io.IOException
    {
        String portclass;

        if( me<procno ){
            portclass = "Upstream";
        }
        else {
            portclass = "Downstream";
        }
        String sendportname = "send" + portclass + me;
        String receiveportname = "receive" + portclass + procno;

        SendPort res = t.createSendPort( sendportname );
        if( tracePortCreation ){
            System.err.println( "P" + me + ": created send port " + sendportname  );
        }
        ReceivePortIdentifier id = registry.lookup( receiveportname );
        res.connect( id );
        if( tracePortCreation ){
            System.err.println( "P" + me + ": connected " + sendportname + " to " + receiveportname );
        }
        return res;
    }

    /**
     * Creates an update receive port.
     * @param t The type of the port to construct.
     * @param me My own processor number.
     * @param procno The processor to receive from.
     */
    private static ReceivePort createUpdateReceivePort( PortType t, int me, int procno )
        throws java.io.IOException
    {
        String portclass;

        if( me<procno ){
            portclass = "receiveDownstream";
        }
        else {
            portclass = "receiveUpstream";
        }
        String receiveportname = portclass + me;

        ReceivePort res = t.createReceivePort( receiveportname );
        if( tracePortCreation ){
            System.err.println( "P" + me + ": created receive port " + receiveportname  );
        }
        res.enableConnections();
        return res;
    }

    private static void send( int me, SendPort p, int data[] )
        throws java.io.IOException
    {
        if( traceCommunication ){
            System.err.println( "P" + me + ": sending from port " + p );
        }
        WriteMessage m = p.newMessage();
        m.writeArray( data );
        m.send();
        m.finish();
    }

    private static void receive( int me, ReceivePort p, int data[] )
        throws java.io.IOException
    {
        if( traceCommunication ){
            System.err.println( "P" + me + ": receiving on port " + p );
        }
        ReadMessage m = p.receive();
        m.readArray( data );
        m.finish();
    }

    public static void main( String [] args )
    {
        int count = -1;
        int repeat = 10;
        int rank = 0;
        int remoteRank = 1;
        boolean noneSer = false;
        RszHandler rszHandler = new RszHandler();

        /* Parse commandline parameters. */
        for( int i=0; i<args.length; i++ ){
            if( args[i].equals( "-repeat" ) ){
                i++;
                repeat = Integer.parseInt( args[i] );
            }
            else {
                if( count == -1 ){
                    count = Integer.parseInt( args[i] );
                }
                else {
                    usage();
                }
            }
        }

        if( count == -1 ) {
            count = 10000;
        }

        try {
            info = new ibis.util.PoolInfo();
            StaticProperties s = new StaticProperties();
            s.add( "serialization", "data" );
            s.add( "communication", "OneToOne, Reliable, ExplicitReceipt" );
            s.add( "worldmodel", "closed" );
            ibis = Ibis.createIbis( s, rszHandler );

            // ibis.openWorld();

            registry = ibis.registry();

            // This only works for a closed world...
            final int me = info.rank();         // My processor number.
            final int nProcs = info.size();     // Total number of procs.

            System.err.println( "me=" + me + ", nProcs=" + nProcs );

            PortType t = ibis.createPortType( "neighbour update", s );

            SendPort leftSendPort = null;
            SendPort rightSendPort = null;
            ReceivePort leftReceivePort = null;
            ReceivePort rightReceivePort = null;

            if( me != 0 ){
                leftReceivePort = createUpdateReceivePort( t, me, me-1 );
            }
            if( me != nProcs-1 ){
                rightReceivePort = createUpdateReceivePort( t, me, me+1 );
            }
            if( me != 0 ){
                leftSendPort = createUpdateSendPort( t, me, me-1 );
            }
            if( me != nProcs-1 ){
                rightSendPort = createUpdateSendPort( t, me, me+1 );
            }

            int data[] = new int[] { 0, 1, 2 };

            for( int iter=0; iter<3; iter++ ){
                if( (me % 2) == 0 ){
                    if( leftSendPort != null ){
                        send( me, leftSendPort, data );
                    }
                    if( rightSendPort != null ){
                        send( me, rightSendPort, data );
                    }
                    if( leftReceivePort != null ){
                        receive( me, leftReceivePort, data );
                    }
                    if( rightReceivePort != null ){
                        receive( me, rightReceivePort, data );
                    }
                }
                else {
                    if( leftReceivePort != null ){
                        receive( me, leftReceivePort, data );
                    }
                    if( rightReceivePort != null ){
                        receive( me, rightReceivePort, data );
                    }
                    if( leftSendPort != null ){
                        send( me, leftSendPort, data );
                    }
                    if( rightSendPort != null ){
                        send( me, rightSendPort, data );
                    }
                }
            }
            ibis.end();
        }
        catch( Exception e ) {
            System.err.println( "Got exception " + e );
            System.err.println( "StackTrace:" );
            e.printStackTrace();
        }
    }
}
