
import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;

public class Node implements Runnable, Serializable, INode {
    private final List<Edge> edges;
    private final Integer id;
    private NodeState state = NodeState.SLEEPING;
    private Integer fragmentLevel = 0; // the level of the fragment this node belongs to
    private Weight fragmentName = null; // the name of the fragment this node belongs to
    private Edge inBranch = null;  // the edge that leads to the fragment core
    private Integer findCount; // the number of reports still expected
    private Edge bestEdge; // the edge leading towards the best candidate for the moe
    private Weight bestWeight = Weight.INFINITE; // the weight of the best candidate for the moe
    private Edge testEdge; // the edge this node is currently testing for the moe
    private Map<Edge, EdgeState> edgeStates;
    private final Integer NETWORK_DELAY = 150;
    private final ArrayList<ReportMessage> reportQueue;
    private final ArrayList<ConnectMessage> connectQueue;
    private final ArrayList<TestMessage> testQueue;

    public Node(Integer id, List<Integer> neighbourIds) {
        this.id = id;
        this.reportQueue = new ArrayList<>();
        this.connectQueue = new ArrayList<>();
        this.testQueue = new ArrayList<>();
        // Create list of edges connected to this node
        ArrayList<Edge> edges = new ArrayList<>();
        neighbourIds.forEach(n -> {
            if(id < n) { // current node is the lower id
               edges.add(new Edge(id, n, new Weight(1, id, n)));
            } else {
                edges.add(new Edge(n, id, new Weight(1, n, id)));
            }
        });
        Collections.sort(edges);
        this.edges = Collections.unmodifiableList(edges);
        System.out.println(this.id + ": Created with edge list " + this.edges);
        this.fragmentName = this.edges.get(0).weight;
        // Keep a list of states corresponding to the above edges
        HashMap<Edge, EdgeState> edgeStates = new HashMap<>();
        this.edges.forEach(e -> edgeStates.put(e, EdgeState.UNKNOWN));
        this.edgeStates = Collections.unmodifiableMap(edgeStates);
    }

    @Override
    public void receiveInitiate(Integer from, Integer L, Weight F, NodeState S) throws RemoteException {
        // Fragment IV
        System.out.println(from + " -> " + this.id + " INITIATE");
        Edge j = identifyEdge(from);
        this.fragmentLevel = L;
        this.fragmentName = F;
        this.state = S;
        this.inBranch = j;
        this.bestEdge = null;
        this.bestWeight = Weight.INFINITE;
        for(Edge i : this.edges)  {
            if(!i.equals(j) && this.edgeStates.get(i) == EdgeState.IN_MST) {
                sendInitiate(i, L, F, S);
                if(this.state == NodeState.FIND) {
                    this.findCount++;
                }
            }
        }
        if(this.state == NodeState.FIND) {
            test();
        }
        if(this.state == NodeState.FOUND) {
            if(!reportQueue.isEmpty()) {
                System.out.println(this.id + ": Popping a message from the report queue");
                ReportMessage m = reportQueue.get(0);
                reportQueue.remove(0);
                this.receiveReport(m.from, m.weight);
            }
        }
        if(!testQueue.isEmpty()) {
            TestMessage tm = testQueue.get(0);
            if(tm.level <= this.fragmentLevel) {
                System.out.println(this.id + ": Popping a message from the test queue");
                testQueue.remove(tm);
                this.receiveTest(tm.from, tm.level, tm.weight);
            }
        }

    }

    @Override
    public void receiveTest(Integer from, Integer l, Weight FN) {
        // Fragment VI
        System.out.println(from + " -> " + this.id + " TEST");
        if(this.state == NodeState.SLEEPING) {
            wakeup();
        }
        Edge j = identifyEdge(from);
        if(this.fragmentName.equals(FN)) {
            sendReject(j);
        } else if (this.fragmentLevel <= l) {
            sendAccept(j);
        } else {
            System.out.println(this.id + ": Appending to TEST Queue");
            this.testQueue.add(new TestMessage(from, l, FN));
        }
    }

    private void sendReject(Edge j) {
        Integer receiverId = getReceiver(j);
        INode receiver = findNode(receiverId);
        if(receiver == null){
            throw new RuntimeException("Unable to find Node with id " + receiverId);
        }
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveReject(this.id);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (InterruptedException ignored) {
            }
        };
        new Thread(send).start();
    }

    private void sendAccept(Edge j) {
       Integer receiverId = getReceiver(j);
       INode receiver = findNode(receiverId);
       if (receiver == null) {
           throw new RuntimeException("Unable to find Node with id " + receiverId);
       }
       Runnable send = () -> {
           try {
               Random random = new Random();
               Thread.sleep(random.nextInt(NETWORK_DELAY));
               receiver.receiveAccept(this.id);
           } catch (RemoteException e) {
               e.printStackTrace();
           } catch (InterruptedException ignored) {}
       };
       new Thread(send).start();
    }

    @Override
    public void receiveAccept(Integer from) {
        // Fragment VIII
        System.out.println(from + " -> " + this.id + " ACCEPT");
        Edge j = identifyEdge(from);
        this.testEdge = null;
        if(j.weight.compareTo(bestWeight) < 0) {
            this.bestEdge = j;
            this.bestWeight = j.weight;
        }
        report();

    }

    private void report() {
        // Fragment IX
        if(this.findCount == 0 && this.testEdge == null) {
            this.state = NodeState.FOUND;
            Integer receiver = getReceiver(inBranch);
            sendReport(receiver, this.bestWeight);
        }
    }

    private void sendReport(Integer receiverId, Weight bestWeight) {
        INode receiver = findNode(receiverId);
        if(receiver == null) {
            throw new RuntimeException("Unable to find node with id " + receiverId);
        }
        Weight W = new Weight(bestWeight);
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveReport(this.id, W);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (InterruptedException ignored) {}
        };
        new Thread(send).start();
    }

    @Override
    public void receiveReject(Integer from) {
        // Fragment VII
        System.out.println(from + " -> " + this.id + " REJECT");
        Edge j = identifyEdge(from);
        this.updateEdgeState(j, EdgeState.NOT_IN_MST);
        test();
    }

    @Override
    public void receiveReport(Integer from, Weight w) {
        // Fragment X
        System.out.println(from + " -> " + this.id + " REPORT");
        Edge j = identifyEdge(from);
        if(!j.equals(inBranch)) {
            this.findCount -= 1;
            if(w.compareTo(bestWeight) < 0) {
                bestWeight = w;
                bestEdge = j;
            }
            report();
        } else {
            if(this.state == NodeState.FIND) {
                System.out.println(this.id + ": Appending to REPORT queue");
                this.reportQueue.add(new ReportMessage(from, w));
            } else {
                if(w.compareTo(bestWeight) > 0) {
                    changeRoot();
                } else {
                    if(w.equals(bestWeight) && bestWeight.equals(Weight.INFINITE)) {
                        HALT();
                    }
                }
            }
        }
    }

    private void changeRoot() {
        // Fragment XI
        if(this.edgeStates.get(bestEdge) == EdgeState.IN_MST) {
            sendChangeRoot(bestEdge);
        } else {
            this.sendConnect(bestEdge, this.fragmentLevel);
            updateEdgeState(bestEdge, EdgeState.IN_MST);
        }
    }

    private void sendChangeRoot(Edge j) {
        Integer receiverId = getReceiver(j);
        INode receiver = findNode(receiverId);
        if(receiver == null) {
            throw new RuntimeException("Unable to find node with id " + receiverId);
        }
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveChangeRoot(this.id);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (InterruptedException ignored) {
            }
        };
        new Thread(send).start();
    }

    private void HALT() {
        // TODO
        // misschien iets van een flag zodat er niet meer iets gedaan wordt met berichten?
        System.out.println("HALT!");
    }

    @Override
    public void receiveConnect(Integer from, Integer value) {
        // Fragment III
        System.out.println(from + " -> " + this.id + " CONNECT");
        if(this.state == NodeState.SLEEPING) {
            wakeup();
        }
        Edge j = identifyEdge(from);
        if(value < this.fragmentLevel) {
            System.out.println(this.id + ": ABSORB");
            updateEdgeState(j, EdgeState.IN_MST);
            sendInitiate(j, fragmentLevel, fragmentName, state);
            if(this.state == NodeState.FIND) {
                this.findCount++;
            }
        } else {
            if(this.edgeStates.get(j) == EdgeState.UNKNOWN) {
                System.out.println(this.id + ": Appending to CONNECT queue");
                this.connectQueue.add(new ConnectMessage(from, value, j));
            } else {
                System.out.println(this.id + ": MERGE");
                sendInitiate(j, fragmentLevel + 1, j.weight, NodeState.FIND);
            }
        }

    }

    private void sendInitiate(Edge j, Integer fragmentLevel, Weight fragmentName, NodeState state) {
        Integer receiverId = getReceiver(j);
        INode receiver = findNode(receiverId);
        if (receiver == null) {
            throw new RuntimeException("Unable to find node with id " + receiverId);
        }
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveInitiate(this.id, fragmentLevel, fragmentName, state);
            } catch (InterruptedException ignored) {
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        };
        new Thread(send).start();
    }

    @Override
    public void receiveChangeRoot(Integer from) {
        System.out.println(from + " -> " + id + " CHANGE-ROOT");
        changeRoot();
    }

    @Override
    public void run() {
        // Code Fragment I : spontaneously starting
        Random random = new Random();
        try {
            Thread.sleep(random.nextInt(1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(this.state == NodeState.SLEEPING && this.id.equals(1)) {
            wakeup();
        }
    }

    private void wakeup() {
        // Code Fragment II : Waking up
        Edge j = this.edges.get(0); // Edge list should be sorted on increasing weight
        System.out.println(this.id + ": Waking up");
        if(this.edges.size() > 1) {
            if(j.compareTo(this.edges.get(1)) >= 0) {
                throw new RuntimeException("Edges are not sorted by weight");
            }
        }
        updateEdgeState(j, EdgeState.IN_MST);
        this.fragmentLevel = 0;
        this.state = NodeState.FOUND;
        this.findCount = 0;
        sendConnect(j, 0);
    }

    private void sendConnect(Edge e, Integer value) {
        Integer receiverId = getReceiver(e);
        INode receiver = findNode(receiverId);
        if(receiver == null) {
            throw new RuntimeException("Unable to find node with id " + receiverId);
        }
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveConnect(this.id, value);
            } catch (InterruptedException ignored) {
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        };
        new Thread(send).start();
    }

    private void test() {
        // Fragment V
        for(Edge e : this.edges) {
            if(this.edgeStates.get(e) == EdgeState.UNKNOWN) {
                this.testEdge = e;
                sendTest(e, fragmentLevel, fragmentName);
                return;
            }
        }
        this.testEdge = null;
        report();
    }

    private void sendTest(Edge e, Integer fragmentLevel, Weight fragmentName) {
        Integer receiverId = getReceiver(e);
        INode receiver = findNode(receiverId);
        if(receiver == null) {
            throw new RuntimeException("Unable to find node with id " + receiverId);
        }
        Weight FN = new Weight(fragmentName);
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveTest(this.id, fragmentLevel, FN);
            } catch (InterruptedException ignored) {
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        };
        new Thread(send).start();
    }

    private void updateEdgeState(Edge edge, EdgeState state) {
        if(this.edgeStates.get(edge) != EdgeState.UNKNOWN && this.edgeStates.get(edge) != state) {
            throw new RuntimeException("Changing already set edge state. States should be stable");
        }
        Map<Edge, EdgeState> tempMap = new HashMap<>(this.edgeStates);
        tempMap.put(edge, state);
        this.edgeStates = Collections.unmodifiableMap(tempMap);


        ConnectMessage cm = null;
        for(ConnectMessage m : connectQueue) {
            if(this.edgeStates.get(m) != EdgeState.UNKNOWN) {
               cm = m;
               break;
            }
        }
        if(cm != null) {
            System.out.println("Popping a message from the request queue");
            connectQueue.remove(cm);
            this.receiveConnect(cm.from, cm.value);
        }
    }

    private Integer getReceiver(Edge e) {
        if (e == null) {
            throw new RuntimeException("Edge e cannot be null");
        }
        if(e.source.equals(this.id)) {
            return e.target;
        } else if (e.target.equals(this.id)) {
            return e.source;
        } else {
            // this should never happen
            throw new RuntimeException("Current node " + this.id + " is not connected to edge");
        }
    }

    private INode findNode(Integer nodeId) {
        INode node = null;
        try {
            node = (INode) Naming.lookup("//localhost:1099/p" + nodeId);
        } catch (NotBoundException e) {
            try {
                node = (INode) Naming.lookup("//ip:1099/p" + nodeId);
            } catch (NotBoundException | MalformedURLException | RemoteException ignored) {}
        } catch (MalformedURLException | RemoteException ignored) {}
        return node;
    }

    private Edge identifyEdge(Integer from) {
//        System.out.println(this.id + ": Trying to find an edge to or from " + from);
        for (Edge e : this.edges) {
            if (e.source.equals(from) || e.target.equals(from)) {
                return e;
            }
        }
        throw new RuntimeException("Node " + this.id + " is not familiar with an edge to " + from);
    }

    public void printStatus() {
        System.out.println("[Node: " + this.id + ", Level: " + this.fragmentLevel + ", Core: " + this.fragmentName + "]");
    }

}
