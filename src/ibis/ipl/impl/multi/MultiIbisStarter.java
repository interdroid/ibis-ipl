/* $Id: StackingIbisStarter.java 6500 2007-10-02 18:28:50Z ceriel $ */

package ibis.ipl.impl.multi;

import ibis.ipl.CapabilitySet;
import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisStarter;
import ibis.ipl.PortType;
import ibis.ipl.RegistryEventHandler;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MultiIbisStarter extends IbisStarter {

    static final Logger logger = LoggerFactory
            .getLogger("ibis.ipl.impl.multi.MultiIbisStarter");

    static final IbisCapabilities ibisCapabilities = new IbisCapabilities(
            "nickname.multi");

    private final boolean matching;

    public MultiIbisStarter(IbisCapabilities caps, PortType[] types,
            IbisFactory.ImplementationInfo info) {
        super(caps, types, info);
        matching = ibisCapabilities.matchCapabilities(capabilities);
    }

    public boolean matches() {
        return matching;
    }

    public boolean isSelectable() {
        return true;
    }

    public boolean isStacking() {
        return false;
    }

    public CapabilitySet unmatchedIbisCapabilities() {
        return capabilities.unmatchedCapabilities(ibisCapabilities);
    }

    public PortType[] unmatchedPortTypes() {
        return portTypes.clone();
    }

    public Ibis startIbis(RegistryEventHandler registryEventHandler,
            Properties userProperties, String version,
            Object authenticationObject) {
        try {
            return new MultiIbis(registryEventHandler, userProperties,
                    capabilities, portTypes, authenticationObject);
        } catch (Throwable e) {
            throw new Error("Creation of MultiIbis Failed!", e);
        }
    }
}
