final class TranspositionTable {
	static final int SIZE = 1 << 22;
	static int lookups = 0, hits = 0, sorts = 0, stores = 0, 
		overwrites = 0, visited = 0, aborts = 0, scoreImprovements = 0;

	TranspositionTableEntry[] entries = new TranspositionTableEntry[SIZE];

	TranspositionTableEntry lookup(long signature) {
		lookups++;
		return entries[(int)signature & (SIZE-1)];
	}

	void store(TranspositionTableEntry e) {
		stores++;
		TranspositionTableEntry old = entries[(int)e.tag & (SIZE-1)];
		
		if(old == null || e.depth >= old.depth) {
			entries[(int)e.tag & (SIZE-1)] = e;
			if(old != null) overwrites++;
		}
 	}

	protected void finalize() throws Throwable {
		System.out.println("tt: lookups: " + lookups + ", hits: " + hits + ", sorts: " + sorts +
				   ", stores: " + stores + ", overwrites: " + overwrites + 
				   ", aborts: " + aborts + ", score incs: " + scoreImprovements + ", visited: " + visited);
	}
}
