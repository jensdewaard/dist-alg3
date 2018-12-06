import java.rmi.Remote;
import java.rmi.RemoteException;

public interface INode extends Remote {
    void receiveInitiate(Integer id, Integer fragmentLevel, Weight FN, NodeState NS) throws RemoteException;

    void receiveTest(Integer id, Integer l, Weight FN) throws RemoteException;

    void receiveAccept(Integer from) throws RemoteException;

    void receiveReject(Integer from) throws RemoteException;

    void receiveReport(Integer from, Weight w) throws RemoteException;

    void receiveConnect(Integer from, Integer value) throws RemoteException;

    void receiveChangeRoot(Integer from) throws RemoteException;
}
