import ibis.ipl.*;
import ibis.util.PoolInfo;

import java.util.Properties;

class Latency {

	static Ibis ibis;
	static Registry registry;

	public static ReceivePortIdentifier lookup(String name) throws IbisIOException {

		ReceivePortIdentifier temp = null;

		do {
			temp = registry.lookup(name);

			if (temp == null) {
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					// ignore
				}
			}

		} while (temp == null);

		return temp;
	}

	public static void main(String [] args) {
		/* Parse commandline. */

		boolean upcall = false;

		int count = Integer.parseInt(args[0]);

		if (args.length > 1) {
			upcall = args[1].equals("-u");
		}

		PoolInfo info = new PoolInfo();

		int rank = info.rank();
		int size = info.size();
		int remoteRank = (rank == 0 ? 1 : 0);

		try {
			ibis     = Ibis.createIbis("ibis:" + rank, "ibis.ipl.impl.tcp.TcpIbis", null);
			registry = ibis.registry();

			StaticProperties s = new StaticProperties();
			PortType t = ibis.createPortType("test type", s);

			ReceivePort rport = t.createReceivePort("receive port " + rank);
			SendPort sport = t.createSendPort("send port " + rank);

			Latency lat = null;

			if (rank == 0) {

				sport.connect(rport.identifier());

				System.err.println(rank + "*******  connect to myself");

				for (int i=1;i<size;i++) {

					System.err.println(rank + "******* receive");

					ReadMessage r = rport.receive();
					ReceivePortIdentifier id = (ReceivePortIdentifier) r.readObject();
					r.finish();

					System.err.println(rank + "*******  connect to " + id);

					sport.connect(id);
				}

				System.err.println(rank + "*******  connect done ");

				WriteMessage w = sport.newMessage();
				w.writeInt(42);
				w.send();
				w.finish();

				sport.free();

			} else {
				ReceivePortIdentifier id = lookup("receive port 0");


				System.err.println(rank + "*******  connect to 0");
				sport.connect(id);


				System.err.println(rank + "*******  connect done ");

				WriteMessage w = sport.newMessage();
				w.writeObject(rport.identifier());
				w.send();
				w.finish();

				sport.free();
			}

			ReadMessage r = rport.receive();
			int result = r.readInt();
			r.finish();

			System.out.println(rank + " got " + result);

			rport.free();
			ibis.end();

		} catch (IbisIOException e) {
			System.out.println("Got exception " + e);
			System.out.println("StackTrace:");
			e.printStackTrace();
		} catch (IbisException e) {
			System.out.println("Got exception " + e);
			System.out.println("StackTrace:");
			e.printStackTrace();
		}
	}
}
