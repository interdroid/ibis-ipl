// File: $Id$

import java.io.PrintStream;

public class SuffixArray {
    short text[];
    int indices[];
    int commonality[];
    static final short END = 0;

    static String buildString( short text[], int start, int length )
    {
        String s = "";
	int i = start;

        while( length>0  ){
            short c = text[i];
            if( c>0 && c<255 ){
                s += (char) c;
            }
            else if( c == END ){
		break;
            }
            else {
                s += "<" + c + ">";
            }
	    i++;
	    length--;
        }
        return s;
    }

    static String buildString( short text[], int start )
    {
	return buildString( text, start, text.length );
    }

    static String buildString( short text[] )
    {
        return buildString( text, 0 );
    }


    private short[] buildShortArray( byte text[] )
    {
        short arr[] = new short[text.length+1];

        for( int i=0; i<text.length; i++ ){
            arr[i] = (short) text[i];
        }
        arr[text.length] = END;
        return arr;
    }

    // Return the number of common characters in the two given spans.
    private int commonLength( short text[], int i0, int i1 )
    {
	int n = 0;
	while( i0<text.length && i1<text.length && text[i0] == text[i1] ){
	    i0++;
	    i1++;
	    n++;
	}
	return n;
    }

    // Return true iff i0 refers to a smaller text than i1.
    private boolean areCorrectlyOrdered( short text[], int i0, int i1 )
    {
	while( i0<text.length && i1<text.length && text[i0] == text[i1] ){
	    i0++;
	    i1++;
	}
	if( text[i0] == END ){
	    // The sortest string is first, this is as it should be.
	    return true;
	}
	if( text[i1] == END ){
	    // The sortest string is last, this is not good.
	    return false;
	}
	return( text[i0]<text[i1] );
    }

    private void buildArray( short text[] ) throws VerificationException
    {
	indices = new int[text.length-1];
	commonality = new int[text.length-1];

	commonality[0] = -1;
	for( int i=0; i<indices.length; i++ ){
	    indices[i] = i;
	}
	int i = 0;

	// Now sort the indices. Uses `gnome sort' for the moment.
	while( i<indices.length ){
	    if( i == 0 ){
		i++;
	    }
	    else {
		int i0 = indices[i-1];
		int i1 = indices[i];
		int l = commonLength( text, i0, i1 );
		
		commonality[i] = l;
		if( text[i0+l]<text[i1+l] ){
		    // Things are sorted, or we're at the start of the array,
		    // take a step forward.
		    i++;
		}
		else {
		    // Things are in the wrong order, swap them and step back.
		    int tmp = indices[i];
		    indices[i] = indices[i-1];
		    indices[--i] = tmp;
		}
	    }
	}
    }

    private SuffixArray( short text[] ) throws VerificationException
    {
        this.text = text;

        buildArray( text );
    }

    SuffixArray( byte t[] ) throws VerificationException
    {
        this.text = buildShortArray( t );

        buildArray( text );
    }

    SuffixArray( String text ) throws VerificationException
    {
        this( text.getBytes() );
    }

    private void print( PrintStream s )
    {
	for( int i=0; i<indices.length; i++ ){
	    s.println( "" + indices[i] + " " + commonality[i] + " " + buildString( text, indices[i] ) );
	}
    }

    private void printMaximum( PrintStream s )
    {
	int max = 0;

	for( int i=1; i<indices.length; i++ ){
	    if( commonality[i]>commonality[max] ){
		max = i;
	    }
	}
	s.println( "maximum: " + indices[max] + " " + commonality[max] + " " + buildString( text, indices[max], commonality[max] ) );

    }

    public void test() throws VerificationException
    {
    }

    public static void main( String args[] )
    {
        try {
            SuffixArray t = new SuffixArray( args[0] );

            t.test();
            t.print( System.out );
            t.printMaximum( System.out );
        }
        catch( Exception x )
        {
            System.err.println( "Caught " + x );
            x.printStackTrace();
            System.exit( 1 );
        }
    }
}
