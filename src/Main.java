import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class Main {

    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
//        if (System.getSecurityManager() == null) {
//            System.setSecurityManager(new SecurityManager());
//        }
        Registry registry = LocateRegistry.getRegistry("0.0.0.0",1099);
        Node one = new Node(1,  List.of(2, 4));
        Node two = new Node(2, List.of(1, 3));
        Node three = new Node(3, List.of(2, 4));
        Node four = new Node(4, List.of(3, 1));

        INode stub1 = (INode) UnicastRemoteObject.exportObject(one, 0);
        INode stub2 = (INode) UnicastRemoteObject.exportObject(two, 0);
        INode stub3 = (INode) UnicastRemoteObject.exportObject(three, 0);
        INode stub4 = (INode) UnicastRemoteObject.exportObject(four, 0);

        registry.bind("p1", stub1);
        registry.bind("p2", stub2);
        registry.bind("p3", stub3);
        registry.bind("p4", stub4);

        new Thread(one).start();
        new Thread(two).start();
        new Thread(three).start();
        new Thread(four).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                registry.unbind("p1");
                registry.unbind("p2");
                registry.unbind("p3");
                registry.unbind("p4");

                one.printStatus();
                two.printStatus();
                three.printStatus();
                four.printStatus();
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }));

    }
}
