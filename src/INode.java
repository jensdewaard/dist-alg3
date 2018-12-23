import java.rmi.Remote;
import java.rmi.RemoteException;

public interface INode extends Remote {
    void receiveMessage(Message message) throws RemoteException;

    void receiveMessage(MessageType type, Integer from, Weight w, Integer level, NodeState state) throws RemoteException;
}
