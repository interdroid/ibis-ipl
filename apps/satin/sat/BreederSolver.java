// File: $Id$

/**
 * A sequential SAT solver specifically used in evolution. Given a symbolic
 * boolean equation in CNF, find a set of assignments that make this
 * equation true.
 * 
 * This implementation tries to do all the things a professional SAT
 * solver would do, although we are limited by implementation time and
 * the fact that we need to parallelize the stuff.
 * 
 * @author Kees van Reeuwijk
 * @version $Revision$
 */

import java.io.File;
import ibis.satin.SatinTupleSpace;

final class CutoffUpdater implements ibis.satin.ActiveTuple {
    int limit;

    CutoffUpdater( int v ){
        limit = v;
    }

    public void handleTuple( String key ){
        if( limit<BreederSolver.cutoff ){
            BreederSolver.cutoff = limit;
        }
    }
}

public final class BreederSolver {
    private static final boolean traceSolver = false;
    private static final boolean printSatSolutions = false;
    private static final boolean traceNewCode = true;
    private static int label = 0;

    /** Total number of decisions in all solves. */
    private int decisions = 0;

    /** Maximal number decisions allowed before we give up. Can
     * be updated by the CutoffUpdater class above.
     */

    static int cutoff = Integer.MAX_VALUE;
    private static final int GENECOUNT = 8;

    /**
     * Solve the leaf part of a SAT problem.
     * The method throws a SATResultException if it finds a solution,
     * or terminates normally if it cannot find a solution.
     * @param level branching level
     * @param p the SAT problem to solve
     * @param ctx the changable context of the solver
     * @param var the next variable to assign
     * @param val the value to assign
     */
    public void leafSolve(
	int level,
	SATProblem p,
	SATContext ctx,
	int var,
	boolean val
    ) throws SATResultException, SATRestartException, SATCutoffException
    {
	if( traceSolver ){
	    System.err.println( "ls" + level + ": trying assignment var[" + var + "]=" + val );
	}
	ctx.assignment[var] = val?1:0;
	int res;
	if( val ){
	    res = ctx.propagatePosAssignment( p, var, level );
	}
	else {
	    res = ctx.propagateNegAssignment( p, var, level );
	}
	if( res == SATProblem.CONFLICTING ){
	    if( traceSolver ){
		System.err.println( "ls" + level + ": propagation found a conflict" );
	    }
	    return;
	}
	if( res == SATProblem.SATISFIED ){
	    // Propagation reveals problem is satisfied.
	    SATSolution s = new SATSolution( ctx.assignment );

	    if( traceSolver | printSatSolutions ){
		System.err.println( "ls" + level + ": propagation found a solution: " + s );
	    }
	    if( !p.isSatisfied( ctx.assignment ) ){
		System.err.println( "Error: " + level + ": solution does not satisfy problem." );
	    }
	    throw new SATResultException( s );
	}
	int nextvar = ctx.getDecisionVariable();
	if( nextvar<0 ){
	    // There are no variables left to assign, clearly there
	    // is no solution.
	    if( traceSolver ){
		System.err.println( "ls" + level + ": nothing to branch on" );
	    }
	    return;
	}
        decisions++;
	if( decisions>cutoff ){
	    throw new SATCutoffException();
	}

	boolean firstvar = ctx.posDominant( nextvar );
	SATContext subctx = (SATContext) ctx.clone();
        try {
            leafSolve( level+1, p, subctx, nextvar, firstvar );
        }
        catch( SATRestartException x ){
	    if( x.level<level ){
		//System.err.println( "RestartException passes level " + level + " heading for level " + x.level );
		throw x;
	    }
        }
	// Since we won't be using our context again, we may as well
	// give it to the recursion.
	// Also note that this call is a perfect candidate for tail
	// call elimination.
        // However, we must update the administration with any
        // new clauses that we've learned recently.
        ctx.update( p );
	leafSolve( level+1, p, ctx, nextvar, !firstvar );
    }

    /**
     * Given a SAT problem, returns a solution, or <code>null</code> if
     * there is no solution.
     * @param p The problem to solve.
     * @param cutoff The maximum number of decisions to try.
     * @return a solution of the problem, or <code>null</code> if there is no solution
     */
    protected SATSolution solveSystem( final SATProblem p )
	throws SATCutoffException
    {
	SATSolution res = null;

	if( p.isConflicting() ){
	    return null;
	}
	if( p.isSatisfied() ){
	    return new SATSolution( p.buildInitialAssignments() );
	}
	int oldClauseCount = p.getClauseCount();

        // Now recursively try to find a solution.
	try {
	    SATContext ctx = SATContext.buildSATContext( p );

	    ctx.assignment = p.buildInitialAssignments();

	    int r = ctx.optimize( p );
	    if( r == SATProblem.SATISFIED ){
		if( !p.isSatisfied( ctx.assignment ) ){
		    System.err.println( "Error: solution does not satisfy problem." );
		}
		return new SATSolution( ctx.assignment );
	    }
	    if( r == SATProblem.CONFLICTING ){
		return null;
	    }

	    int nextvar = ctx.getDecisionVariable();
	    if( nextvar<0 ){
		// There are no variables left to assign, clearly there
		// is no solution.
		if( traceSolver | traceNewCode ){
		    System.err.println( "top: nothing to branch on" );
		}
		return null;
	    }
	    if( traceSolver ){
		System.err.println( "Top level: branching on variable " + nextvar );
	    }
            decisions++;

	    SATContext negctx = (SATContext) ctx.clone();
	    boolean firstvar = ctx.posDominant( nextvar );
            try {
                leafSolve( 0, p, negctx, nextvar, firstvar );
            }
            catch( SATRestartException x ){
                // Restart the search here, since we have an untried
                // value.
            }
            ctx.update( p );
            leafSolve( 0, p, ctx, nextvar, !firstvar );
	}
	catch( SATResultException r ){
	    if( r.s == null ){
		System.err.println( "A null solution thrown???" );
	    }
	    res = r.s;
	}
        catch( SATRestartException x ){
	    // No solution found.
            res = null;
        }

	int newClauseCount = p.getClauseCount();
	return res;
    }

    static Genes getInitialGenes()
    {
	float g[] = new float[GENECOUNT];

	for( int i=0; i<g.length; i++ ){
	    g[i] = 1.0f;
	}
	return new Genes( g, null, null );
    }

    static Genes getMaxGenes()
    {
	float g[] = new float[GENECOUNT];

	for( int i=0; i<g.length; i++ ){
	    g[i] = 100.0f;
	}
	return new Genes( g, null, null );
    }

    static Genes getMinGenes()
    {
	float g[] = new float[GENECOUNT];

	for( int i=0; i<g.length; i++ ){
	    g[i] = 1e-6f;
	}
	return new Genes( g, null, null );
    }

    static int run( final SATProblem p_in, Genes genes, int cutoff )
    {
        BreederSolver s = new BreederSolver();

	//System.err.println( "Using genes: " + genes );
	SATProblem p = (SATProblem) p_in.clone();
	p.reviewer = new GeneticClauseReviewer( genes.floats );
	try {
	    s.solveSystem( p );
	}
	catch( SATCutoffException x ){
	    return -1;
	}
	return s.decisions;
    }

    static int run( final SATProblem pl[], Genes genes, int cutoff )
    {
	int total = 0;

	for( int i=0; i<pl.length; i++ ){
	    int d = run( pl[i], genes, cutoff );

	    if( d<0 ){
		// Over the budget, we're done.
		return d;
	    }
	    total += d;
	}
        int newCutoff = (3*total)/2;
        if( cutoff>newCutoff ){
            // We may have a new cutoff value, broadcast it.
            SatinTupleSpace.add( "cutoff", new CutoffUpdater( newCutoff ) );
        }
	return total;
    }
}
