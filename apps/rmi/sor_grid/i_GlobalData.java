import java.rmi.*;

interface i_GlobalData extends Remote {

	public i_SOR [] table(i_SOR me, int node) throws RemoteException;
	public double reduceDiff(double value) throws RemoteException;


	// Used for visualization, downsample/enlarge to the given size.
	public void setRawDataSize(int width, int height) throws RemoteException;

	// Used for visualization, downsample/enlarge to the given size.
	public float[][] getRawData() throws RemoteException;
}
