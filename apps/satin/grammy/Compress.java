// File: $Id$

import java.io.File;

class Compress extends ibis.satin.SatinObject implements Configuration
{
    static boolean doVerification = false;

    /**
     * Applies one step in the folding process.
     * @return True iff a useful compression step could be done.
     */
    public static boolean applyFolding( SuffixArray a, int top ) throws VerificationException
    {
        if( a.getLength() == 0 ){
            return false;
        }
        StepList steps = a.selectBestSteps( top );

        System.out.println( "Choices: " + steps.getLength() );

        // For now, just pick the best move.
        Step mv = steps.getBestStep();
        if( mv != null && mv.getGain()>0 ){
            // It is worthwile to do this compression.
            if( traceCompressionCosts ){
                System.out.println( "Best step: string [" + a.buildString( mv )  + "]: " + mv );
            }
            a.applyCompression( mv );
            if( doVerification ){
                a.test();
            }
            return true;
        }
        return false;
    }

    /** Returns a compressed version of the string represented by
     * this suffix array.
     */
    public ByteBuffer compress( SuffixArray a, int top ) throws VerificationException
    {
        boolean success;

        do {
            success = applyFolding( a, top );
            if( traceIntermediateGrammars && success ){
                a.printGrammar();
            }
        } while( success );
	return a.getByteBuffer();
    }

    public ByteBuffer compress( byte text[], int top ) throws VerificationException
    {
	SuffixArray a = new SuffixArray( text );
	ByteBuffer out = compress( a, top );
        a.printGrammar();
        return out;
    }

    static void usage()
    {
        System.err.println( "Usage: [-quiet] [-verify] [-top <n>] <text-file> <compressed-file>" );
	System.err.println( "   or: [-quiet] [-verify] [-top <n>] -string <text-string> <compressed-file>" );
    }

    /**
     * Allows execution of the class.
     * @param args The command-line arguments.
     */
    public static void main( String args[] ) throws java.io.IOException
    {
	File infile = null;
	File outfile = null;
        int top = DEFAULT_TOP;
        String intext = null;
        boolean quiet = false;

        for( int i=0; i<args.length; i++ ){
            if( args[i].equals( "-verify" ) ){
                doVerification = true;
            }
            else if( args[i].equals( "-quiet" ) ){
                quiet = true;
            }
            else if( args[i].equals( "-top" ) ){
                i++;
                top = Integer.parseInt( args[i] );
            }
            else if( args[i].equals( "-string" ) ){
                i++;
                if( intext != null || infile != null ){
                    System.err.println( "More than one text to compress given" );
                    usage();
                    System.exit( 1 );
                }
                intext = args[i];
            }
            else if( infile == null ){
                infile = new File( args[i] );
            }
            else if( outfile == null ){
                outfile = new File( args[i] );
            }
            else {
                System.err.println( "Superfluous parameter `" + args[i] + "'" );
                usage();
                System.exit( 1 );
            }
        }
        if( intext == null && (infile == null || outfile == null) ){
            usage();
            System.exit( 1 );
        }

        try {
            byte text[];
	    if( infile != null ){
		text = Helpers.readFile( infile );
	    }
	    else if( intext != null ){
		text = intext.getBytes();
	    }
	    else {
		System.err.println( "No text to compress" );
		usage();
		System.exit( 1 );
		text = null;
	    }
            long startTime = System.currentTimeMillis();

            Compress c = new Compress();
            ByteBuffer buf = c.compress( text, top );
            if( outfile != null ){
                Helpers.writeFile( outfile, buf );
            }

            long endTime = System.currentTimeMillis();
            double time = ((double) (endTime - startTime))/1000.0;

            if( !quiet ){
                System.out.println( "ExecutionTime: " + time );
                System.out.println( "In: " + text.length + " bytes, out: " + buf.getLength() + " bytes." );
            }
            if( doVerification ){
                ByteBuffer debuf = Decompress.decompress( buf );
                byte nt[] = debuf.getText();

                if( nt.length != text.length ){
                    System.out.println( "Error: decompressed text has different length from original. Original is " + text.length + " bytes, decompression is " + nt.length + " bytes" );
                    System.exit( 1 );
                }
                for( int i=0; i<nt.length; i++ ){
                    if( nt[i] != text[i] ){
                        System.out.println( "Error: decompressed text differs from original at position " + i );
                        System.exit( 1 );
                    }
                }
            }
        }
        catch( Exception x ){
            x.printStackTrace();
        }
    }
}
