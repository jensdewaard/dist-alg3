import java.rmi.Remote;
import java.rmi.RemoteException;

public interface INode extends Remote {
    void receiveMessage(MessageType type, Integer id, Weight core, Integer level, NodeState state) throws RemoteException;
}
