/* $Id$ */

package ibis.rmi.impl;

import ibis.rmi.Remote;
import ibis.rmi.server.RemoteStub;
import ibis.io.Replacer;

public final class RMIReplacer implements Replacer {

    public Object replace(Object o) {

        if (o instanceof RemoteStub) {
            return o;
        }
        if (o instanceof Remote) {
            Object r = RTS.getStub(o);
            if (r != null) {
                return r;
            }
        }
        return o;
    }
}
