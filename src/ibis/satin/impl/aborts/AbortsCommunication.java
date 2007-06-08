/*
 * Created on Apr 26, 2006 by rob
 */
package ibis.satin.impl.aborts;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.communication.Protocol;
import ibis.satin.impl.loadBalancing.Victim;
import ibis.satin.impl.spawnSync.InvocationRecord;
import ibis.satin.impl.spawnSync.Stamp;

import java.io.IOException;

final class AbortsCommunication implements Config {
    private Satin s;

    AbortsCommunication(Satin s) {
        this.s = s;
    }

    /*
     * message combining for abort messages does not work (I tried). It is very
     * unlikely that one node stole more than one job from me
     */
    protected void sendAbortMessage(InvocationRecord r) {

        if (s.deadIbises.contains(r.getStealer())) {
            /* don't send abort and store messages to crashed ibises */
            return;
        }

        abortLogger.debug("SATIN '" + s.ident + ": sending abort message to: "
            + r.getStealer() + " for job " + r.getStamp() + ", parent = " + r.getParentStamp());

        WriteMessage writeMessage = null;
        try {
            Victim v = s.victims.getVictim(r.getStealer());
            if (v == null) return; // node might have crashed

            writeMessage = v.newMessage();
            writeMessage.writeByte(Protocol.ABORT);
            writeMessage.writeObject(r.getParentStamp());
            long cnt = v.finish(writeMessage);
            if (s.comm.inDifferentCluster(r.getStealer())) {
                s.stats.interClusterMessages++;
                s.stats.interClusterBytes += cnt;
            } else {
                s.stats.intraClusterMessages++;
                s.stats.intraClusterBytes += cnt;
            }
        } catch (IOException e) {
            if (writeMessage != null) {
                writeMessage.finish(e);
            }
            abortLogger.info("SATIN '" + s.ident
                + "': Got Exception while sending abort message: " + e, e);
            // This should not be a real problem, it is just inefficient.
            // Let's continue...
        }
    }

    protected void handleAbort(ReadMessage m) {
        try {
            Stamp stamp = (Stamp) m.readObject();
            synchronized (s) {
                s.aborts.addToAbortList(stamp);
            }
            // m.finish();
        } catch (IOException e) {
            abortLogger.error("SATIN '" + s.ident
                + "': got exception while reading job result: " + e, e);
        } catch (ClassNotFoundException e1) {
            abortLogger.error("SATIN '" + s.ident
                + "': got exception while reading job result: " + e1, e1);
        }
    }
}
