
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
    private Weight bestWeight; // the weight of the best candidate for the moe
    private Edge testEdge; // the edge this node is currently testing for the moe
    private Map<Edge, EdgeState> edgeStates;
    private final Integer NETWORK_DELAY = 150;
    private final ArrayList<ReportMessage> reportQueue;
    private final ArrayList<ConnectMessage> connectQueue;
    private final ArrayList<TestMessage> testQueue;

    Node(Integer id, List<Integer> neighbourIds) {
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

        // Keep a list of states corresponding to the above edges
        HashMap<Edge, EdgeState> edgeStates = new HashMap<>();
        this.edges.forEach(e -> edgeStates.put(e, EdgeState.UNKNOWN));
        this.edgeStates = Collections.unmodifiableMap(edgeStates);
    }

    @Override
    public synchronized void receiveInitiate(Integer id, Integer L, Weight F, NodeState S) {
        // Fragment IV
        Edge j = identifyEdge(id);
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
                    findCount = findCount + 1;
                }
            }
        }
        if(this.state == NodeState.FIND) {
            test();
        }
        checkReportQueue();
        checkTestQueue();
    }

    @Override
    public synchronized void receiveTest(Integer from, Integer l, Weight FN) {
        // Fragment VI
        if(this.state == NodeState.SLEEPING) {
            wakeup();
        }
        if(l > this.fragmentLevel) {
            this.testQueue.add(new TestMessage(from, l, FN));
        }
        else {
            Edge j = identifyEdge(from);
            if(!FN.equals(this.fragmentName)) {
                sendAccept(j);
            } else {
                if(this.edgeStates.get(j) == EdgeState.UNKNOWN) {
                    this.updateEdgeState(j, EdgeState.NOT_IN_MST);
                } else if (!j.equals(this.testEdge)) {
                    sendReject(j);
                } else {
                    test();
                }
            }
        }
    }

    private void sendReject(Edge j) {
        Integer receiveID = getReceiver(j);
        INode receiver = findNode(receiveID);
        if(receiver == null){
            return;
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
           return;
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
    public synchronized void receiveAccept(Integer from) {
        // Fragment VIII
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
            return;
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
    public synchronized void receiveReject(Integer from) {
        // Fragment VII
        Edge j = identifyEdge(from);
        if(this.edgeStates.get(j) == EdgeState.UNKNOWN) {
            this.updateEdgeState(j, EdgeState.NOT_IN_MST);
        }
        test();
    }

    @Override
    public synchronized void receiveReport(Integer from, Weight w) {
        // Fragment X
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
        Integer receiverID = getReceiver(j);
        INode receiver = findNode(receiverID);
        if(receiver == null) {
            return;
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
    public synchronized void receiveConnect(Integer from, Integer value) {
        // Fragment III
        if(this.state == NodeState.SLEEPING) {
            wakeup();
        }
        Edge j = identifyEdge(from);
        if(value < this.fragmentLevel) {
            updateEdgeState(j, EdgeState.IN_MST);
            sendInitiate(j, fragmentLevel, fragmentName, state);
        } else {
            if(this.edgeStates.get(j) == EdgeState.UNKNOWN) {
                this.connectQueue.add(new ConnectMessage(from, value, j));
            } else {
                sendInitiate(j, fragmentLevel + 1, j.weight, NodeState.FIND);
            }
        }

    }

    private void sendInitiate(Edge j, Integer fragmentLevel, Weight fragmentName, NodeState state) {
        Integer receiverID = getReceiver(j);
        INode receiver = findNode(receiverID);
        if (receiver == null) {
            return;
        }
        Weight FN = new Weight(fragmentName);

        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveInitiate(this.id, fragmentLevel, FN, state);
            } catch (InterruptedException ignored) {
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        };
        new Thread(send).start();
    }

    @Override
    public synchronized void receiveChangeRoot(Integer from) {
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
        synchronized (this) {
            if(this.state == NodeState.SLEEPING) {
                wakeup();
            }
        }
    }

    private void wakeup() {
        // Code Fragment II : Waking up
        System.out.println(this.id + ": Waking up");
        Edge j = this.edges.get(0); // Edge list should be sorted on increasing weight
        updateEdgeState(j, EdgeState.IN_MST);
        this.findCount = 0;
        sendConnect(j, 0);
    }

    private void sendConnect(Edge e, Integer value) {
        Integer receiverId = getReceiver(e);
        INode receiver = findNode(receiverId);
        if(receiver == null) {
            return; // is just nothing doing anything reasonable? what is a reasonable fallback?
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
            return;
        }
        Runnable send = () -> {
            try {
                Random random = new Random();
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                receiver.receiveTest(this.id, fragmentLevel, fragmentName);
            } catch (InterruptedException ignored) {
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        };
        new Thread(send).start();
    }

    private void updateEdgeState(Edge edge, EdgeState state) {
        Map<Edge, EdgeState> tempMap = new HashMap<>(this.edgeStates);
        tempMap.put(edge, state);
        this.edgeStates = Collections.unmodifiableMap(tempMap);
        checkConnectQueue();

    }

    private Integer getReceiver(Edge e) {
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
        for (Edge e : this.edges) {
            if (e.source.equals(from) || e.target.equals(from)) {
                return e;
            }
        }
        throw new RuntimeException("Node " + this.id + " is not familiar with an edge to " + from);
    }

    void printStatus() {
        String queueStatus = "";
        queueStatus += this.reportQueue.isEmpty() ? "R0" : "R1";
        queueStatus += this.testQueue.isEmpty() ? "T0" : "T1";
        queueStatus += this.connectQueue.isEmpty() ? "C0" : "C1";
        System.out.println("[Node: " + this.id + ", Level: " + this.fragmentLevel + ", Core: " +
                this.fragmentName + ", Queue: " + queueStatus + "]");
    }

    private void checkReportQueue() {
        if(this.state == NodeState.FOUND) {
            if(!this.reportQueue.isEmpty()) {
                System.out.println(this.id + ": Popping a message from the report queue");
                ReportMessage m = reportQueue.get(0);
                reportQueue.remove(0);
                this.receiveReport(m.from, m.weight);
            }
        }
    }

    private void checkTestQueue() {
        if(!testQueue.isEmpty()) {
            TestMessage tm = testQueue.get(0);
            if(tm.level <= this.fragmentLevel) {
                System.out.println(this.id + ": Popping a message from the test queue");
                testQueue.remove(tm);
                this.receiveTest(tm.from, tm.level, tm.weight);
                checkTestQueue();
            }
        }
    }

    private void checkConnectQueue() {
        List<ConnectMessage> toProcess = new ArrayList<>();
        for(ConnectMessage m : connectQueue) {
            if(this.edgeStates.get(m.edge) != EdgeState.UNKNOWN) {
                toProcess.add(m);
            }
        }
        for(ConnectMessage m : toProcess) {
            System.out.println(this.id + ": Popping a message from the request queue");
            connectQueue.remove(m);
            this.receiveConnect(m.from, m.value);
        }
    }

}
