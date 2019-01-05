import java.io.*;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws RemoteException, AlreadyBoundException, FileNotFoundException {
        String fileName = "1";
        String in = "input/" + fileName;
//        PrintStream out = new PrintStream(new FileOutputStream(new File("output/" + fileName)));
//        System.setOut(out);
        runOnFile(in);
//        out.flush();
//        out.close();
    }

    private static void runOnFile(String arg) throws FileNotFoundException, RemoteException, AlreadyBoundException {
        FileReader fileReader = new FileReader(arg);
        Scanner scanner = new Scanner(fileReader);

        List<Integer> allIds = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        while (scanner.hasNextLine()) {
            int id = scanner.nextInt();
            allIds.add(id);

            List<Integer> inputList = new ArrayList<>();
            while (scanner.hasNextInt()) {
                inputList.add(scanner.nextInt());
            }
            Node node = new Node(id, Collections.unmodifiableList(inputList));
            nodes.add(node);

            INode stub = (INode) UnicastRemoteObject.exportObject(node, 1098);

            registry.bind("p" + id, stub);

            new Thread(node).start();
            scanner.next();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Node node : nodes) {
                node.printStatus();
            }
            for (Integer id : allIds) {
                try {
                    registry.unbind("p" + id);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (NotBoundException e) {
                    e.printStackTrace();
                }
            }
        }));
    }
}
