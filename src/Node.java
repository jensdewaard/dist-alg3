
import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;

public class Node implements Runnable, Serializable, INode {
    private final List<Edge> edges;
    private final Integer id;
    NodeState state = NodeState.SLEEPING;
    Integer fragmentLevel = 0; // the level of the fragment this node belongs to
    Weight fragmentName; // the name of the fragment this node belongs to
    Edge inBranch = null;  // the edge that leads to the fragment core
    Integer findCount; // the number of reports still expected
    Set<Edge> expectedReports; // the edges from which reports are expected
    Edge bestEdge; // the edge leading towards the best candidate for the moe
    Weight bestWeight = Weight.INFINITE; // the weight of the best candidate for the moe
    Edge testEdge; // the edge this node is currently testing for the moe
    private Map<Edge, EdgeState> edgeStates;
    private final Integer NETWORK_DELAY = 150;
    private final ArrayList<ReportMessage> reportQueue;
    private final ArrayList<ConnectMessage> connectQueue;
    final ArrayList<TestMessage> testQueue;
    private final Map<Edge, List<Message>> messageQueues;
    private final Map<Edge, Integer> sendCounter;
    private final Map<Edge, Integer> receiveCounter;

    Node(Integer id, List<Edge> edges) {
        this.id = id;
        this.reportQueue = new ArrayList<>();
        this.connectQueue = new ArrayList<>();
        this.testQueue = new ArrayList<>();
        this.expectedReports = new HashSet<>();
        // Create list of edges connected to this node
        Collections.sort(edges);
        this.edges = Collections.unmodifiableList(edges);
        this.messageQueues = new HashMap<>();
        this.sendCounter = new HashMap<>();
        this.receiveCounter = new HashMap<>();
        for(Edge e : this.edges) {
            this.messageQueues.put(e, new ArrayList<>());
            this.sendCounter.put(e, 0);
            this.receiveCounter.put(e, 0);
        }

        System.out.println(this.id + ": Created with edge list " + this.edges);
        this.fragmentName = this.edges.get(0).weight;
        // Keep a list of states corresponding to the above edges
        HashMap<Edge, EdgeState> edgeStates = new HashMap<>();
        this.edges.forEach(e -> edgeStates.put(e, EdgeState.UNKNOWN));
        this.edgeStates = Collections.unmodifiableMap(edgeStates);
        this.findCount = 0;
    }

    private void receiveInitiate(Integer from, Integer L, Weight F, NodeState S) {
        // Fragment IV
        Edge j = identifyEdge(from);
        if(!this.edgeStates.get(j).equals(EdgeState.IN_MST)) {
            throw new RuntimeException("Should only receive INITIATE from tree");
        }
        // We reset all variables because we are starting a new 'round' of looking
        // for the moe
        this.fragmentLevel = L;
        this.fragmentName = F;
        this.state = S;
        this.inBranch = j;
        this.bestEdge = null;
        this.bestWeight = Weight.INFINITE;
        for(Edge i : this.edges)  {
            // Send initiate messages to all branches in the subtree that
            // are not 'upstream'. So this floods the fragment with the
            // initiate message so that all "border nodes" start searching
            // for the moe
            if(!i.equals(j) && this.edgeStates.get(i) == EdgeState.IN_MST) {
                sendMessage(MessageType.INITIATE, i, F, L, S);
                if(S == NodeState.FIND) {
                    System.out.println(this.id + ": Now expecting a report from " + i);
                    this.findCount++;
                    this.expectedReports.add(i);
                }
            }
        }
        if(S == NodeState.FIND) {
            processDeferredConnects();
            test();
        }
        processDeferredTests();
    }

    private void processDeferredTests() {
        synchronized (this) {
            while(!testQueue.isEmpty()) {
                TestMessage tm = testQueue.get(0);
                if(tm.checkPreconditions(this.fragmentLevel)) {
                    testQueue.remove(tm);
                    this.receiveTest(tm.from, tm.level, tm.weight);
                } else {
                    return;
                }
            }
        }
    }

    private void processDeferredReports() {
        synchronized (this) {
            while(!reportQueue.isEmpty()) {
                ReportMessage rm = reportQueue.get(0);
                Edge j = identifyEdge(rm.from);
                if(rm.checkPreconditions(j, this.inBranch, this.state)) {
                    reportQueue.remove(rm);
                    System.out.println(this.id + ": Popping a message from the report queue");
                    this.receiveReport(rm.from, rm.weight);
                } else {
                    return;
                }
            }
        }
    }

    private void receiveTest(Integer from, Integer l, Weight FN) {
        if(this.state == NodeState.SLEEPING) {
            wakeup();
        }
        if(l.compareTo(this.fragmentLevel) > 0) {
            deferTest(from, l, FN);
        } else {
            Edge j = identifyEdge(from);
            if(!FN.equals(this.fragmentName)) {
                this.sendMessage(MessageType.ACCEPT, j, null, null, null);
            } else {
                if(this.getEdgeState(j).equals(EdgeState.UNKNOWN)) {
                    this.updateEdgeState(j, EdgeState.NOT_IN_MST);
                }
                if(!j.equals(this.testEdge)) {
                    this.sendMessage(MessageType.REJECT, j, null, null, null);
                } else {
                    test();
                }
            }
        }
    }

    void deferTest(Integer from, Integer l, Weight FN) {
        System.out.println(this.id + ": Appending to TEST Queue");
        this.testQueue.add(new TestMessage(from, l, FN));
    }

    private void receiveAccept(Integer from) {
        // Fragment VIII
        Edge j = identifyEdge(from);
        if(!j.equals(testEdge)) {
            throw new RuntimeException(
                    "Receiving ACCEPT from an edge not being tested. (" +
                            j + " != " + testEdge + ")");
        }
        this.testEdge = null;
        if(j.weight.compareTo(bestWeight) < 0) {
            this.bestEdge = j;
            this.bestWeight = j.weight;
        }
        report();
    }

    void report() {
        // Fragment IX
        if(this.expectedReports.isEmpty() && this.testEdge == null) {
//        if(this.findCount == 0 && this.testEdge == null) {
            this.state = NodeState.FOUND;
            sendMessage(MessageType.REPORT, inBranch, this.bestWeight, null, null);
        }
    }

    private void receiveReject(Integer from) {
        // Fragment VII
        Edge j = identifyEdge(from);
        if(!j.equals(testEdge)) {
            throw new RuntimeException(
                    "Receiving REJECT from an edge not being tested. (" +
                            j + " != " + testEdge + ")");
        }
        if(this.getEdgeState(j) == EdgeState.UNKNOWN) {
            this.updateEdgeState(j, EdgeState.NOT_IN_MST);
        }
        test();
    }

    private void receiveReport(Integer from, Weight w) {
        // Fragment X
        Edge j = identifyEdge(from);
        if(!this.edgeStates.get(j).equals(EdgeState.IN_MST)) {
            throw new RuntimeException("Should only receive reports from the tree branches");
        }
        if(!j.equals(inBranch)) { // report is coming from lower in the tree
//            if(this.findCount.equals(0)) {
//                throw new RuntimeException("Node " + this.id +" was not expecting reports and its inbranch is " + this.inBranch);
//            }
            this.findCount -= 1;
            this.expectedReports.remove(j);
            System.out.println(this.id + ": " + findCount + " reports remaining");
            if(w.compareTo(bestWeight) < 0) {
                bestWeight = w;
                bestEdge = j;
            }
            report();
            if(!reportQueue.isEmpty()) {
                processDeferredReports();
            }
        } else { // report is coming from the co-owner of the core
            if(this.state == NodeState.FIND) {
                deferReport(new ReportMessage(from, w));
            } else {
                if(w.compareTo(bestWeight) > 0) {
                    System.out.println(this.id + ": Changing root, my subtree has the lower MOE");
                    changeRoot();
                } else {
                    System.out.println(this.id + ": Not changing root, might halt");
                    if(w.equals(bestWeight) && bestWeight.equals(Weight.INFINITE)) {
                        HALT();
                    }
                }
            }
        }
    }

    void deferReport(ReportMessage reportMessage) {
        System.out.println(this.id + ": Appending to REPORT queue");
        this.reportQueue.add(reportMessage);
    }

    void changeRoot() {
        // Fragment XI
        if(this.edgeStates.get(bestEdge) == EdgeState.IN_MST) {
            sendMessage(MessageType.CHANGEROOT, bestEdge, null, null, null);
        } else {
            sendMessage(MessageType.CONNECT, bestEdge, null, this.fragmentLevel, null);
            updateEdgeState(bestEdge, EdgeState.IN_MST);
        }
    }

    void HALT() {
        // TODO
        // misschien iets van een flag zodat er niet meer iets gedaan wordt met berichten?
        System.out.println("HALT!");
    }

    /**
     * Has dependencies on the current node state, the edge state of the edge
     * and the current fragment level
     * @param from
     * @param level
     */
    private void receiveConnect(Integer from, Integer level) {
        // Fragment III
        if(from.equals(this.id)) {
            throw new RuntimeException("Should not receive connect from self!");
        }
        if(this.state == NodeState.SLEEPING) {
            wakeup(); // this will change the state of the lowest outging edge..
        }
        Edge j = identifyEdge(from);
        if(level < this.fragmentLevel) {
            absorb(j);
        } else {
            if(this.edgeStates.get(j) == EdgeState.UNKNOWN) { // ..and that ruins my testing here
                deferConnect(from, level, j);
            } else {
                merge(j);
            }
        }
    }

    void deferConnect(Integer from, Integer value, Edge j) {
        System.out.println(this.id + ": Appending to CONNECT queue");
        this.connectQueue.add(new ConnectMessage(from, value, j));
    }

    void merge(Edge j) {
        System.out.println(this.id + ": MERGE along edge " + j);
        sendMessage(MessageType.INITIATE, j, j.weight, fragmentLevel +1, NodeState.FIND);
        updateEdgeState(j, EdgeState.IN_MST);
    }

    void absorb(Edge j) {
        System.out.println(this.id + ": ABSORB along edge " + j);
        updateEdgeState(j, EdgeState.IN_MST);
        this.sendMessage(MessageType.INITIATE, j, fragmentName, fragmentLevel, state);
        if(this.state == NodeState.FIND && !j.equals(inBranch)) {
            System.out.println(this.id + ": Now expecting a report from " + j);
            this.findCount++;
            this.expectedReports.add(j);
        }
    }

    @Override
    public void receiveMessage(Message m) {
        MessageType type = m.type;
        Integer from = m.sender;
        Edge e = identifyEdge(from);
        if(!m.clock.equals(this.receiveCounter.get(e))) {
            // this is not the expected message, it has overtaken another on the same edge
            System.out.println(this.id + ": Message received with clock " + m.clock + ", expecting " + this.receiveCounter.get(e));
            List<Message> edgeQueue = this.messageQueues.get(e);
            edgeQueue.add(m);
            Collections.sort(edgeQueue);
        } else {
            System.out.println(this.id + ": Receiving " + type + " from " + from);
            Integer recCount = this.receiveCounter.get(e);
            this.receiveCounter.put(e, recCount + 1);
            switch(m.type) {
                case ACCEPT:
                    this.receiveAccept(from);
                    break;
                case REJECT:
                    this.receiveReject(from);
                    break;
                case TEST:
                    this.receiveTest(from, m.level, m.core);
                    break;
                case REPORT:
                    this.receiveReport(from, m.core);
                    break;
                case CHANGEROOT:
                    changeRoot();
                    break;
                case CONNECT:
                    this.receiveConnect(from, m.level);
                    break;
                case INITIATE:
                    this.receiveInitiate(from, m.level, m.core, m.state);
                    break;
                default:
                    throw new RuntimeException("Unknown message type received");
            }
            synchronized (this) {
                List<Message> edgeQueue = this.messageQueues.get(e);
                if(!edgeQueue.isEmpty()) {
                    Message top = edgeQueue.get(0);
                    if (this.receiveCounter.get(e).equals(top.clock)) {
                        edgeQueue.remove(top);
                        this.receiveMessage(top);
                    }
                }
            }
            processDeferredConnects();
            processDeferredReports();
            processDeferredTests();
        }

    }

    @Override
    public void receiveMessage(MessageType type, Integer from, Weight w, Integer level, NodeState state) {
        throw new RuntimeException("This is a dummy function, do not call it");
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
        if(this.state == NodeState.SLEEPING) {
            wakeup();
        }
    }

    void wakeup() {
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
        this.expectedReports.clear();
        sendMessage(MessageType.CONNECT, j, null, fragmentLevel, null);
    }

    void test() {
        // Fragment V
        for(Edge e : this.edges) {
            if(this.edgeStates.get(e) == EdgeState.UNKNOWN) {
                //Prevent double test messages?
                if(e.equals(this.testEdge)) return;
                this.testEdge = e;
                sendMessage(MessageType.TEST, e, fragmentName, fragmentLevel, null);
                return;
            }
        }
        this.testEdge = null;
        report();
    }

    void updateEdgeState(Edge edge, EdgeState state) {
        EdgeState oldState = this.edgeStates.get(edge);
        if(edge == null) {
            throw new RuntimeException("Cannot update state of null");
        }
        if(state == null) {
            throw new RuntimeException("Cannot set edge state to null");
        }
        if(state == EdgeState.UNKNOWN) {
            throw new RuntimeException("Cannot set edge state to unknown");
        }
        if(oldState == null) {
            throw new RuntimeException("Edge " + edge + " is not in this node");
        }
        if(oldState != EdgeState.UNKNOWN && oldState != state) {
//            return;
            throw new RuntimeException(
                    "Changing already set edge state " + oldState
                            + ". States should be stable");
        }
        Map<Edge, EdgeState> tempMap = new HashMap<>(this.edgeStates);
        tempMap.put(edge, state);
        this.edgeStates = Collections.unmodifiableMap(tempMap);
        processDeferredConnects();
    }

    private void processDeferredConnects() {
        synchronized (this) {
            while(!connectQueue.isEmpty()) {
                ConnectMessage cm = connectQueue.get(0);
                Edge j = identifyEdge(cm.from);
                EdgeState es = getEdgeState(j);
                if(cm.checkPreconditions(this.fragmentLevel, es)) {
                    System.out.println("Popping a message from the request queue");
                    connectQueue.remove(cm);
                    this.receiveConnect(cm.from, cm.value);
                } else {
                    return;
                }
            }
        }
    }

    EdgeState getEdgeState(Edge edge) {
        return this.edgeStates.get(edge);
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

    INode findNode(Integer nodeId) {
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

    Edge identifyEdge(Integer from) {
//        System.out.println(this.id + ": Trying to find an edge to or from " + from);
        for (Edge e : this.edges) {
            if (e.source.equals(from) && e.target.equals(this.id)) {
                return e;
            }
            if (e.source.equals(this.id) && e.target.equals(from)) {
                return e;
            }
        }
        throw new RuntimeException("Node " + this.id + " is not familiar with an edge to " + from);
    }

    void printStatus() {
        String connectStatus = connectQueue.isEmpty() ? "0" : "1";
        String testStatus = testQueue.isEmpty() ? "0" : "1";
        String reportStatus = reportQueue.isEmpty() ? "0" : "1";
        System.out.println("[Node: " + this.id + ", Level: " + this.fragmentLevel + ", Core: " + this.fragmentName
                + " [ " + connectStatus + testStatus + reportStatus + "]]");
    }

    /**
     *
     * @param type
     * @param e The edge on which the message should be send
     * @param core
     * @param level
     * @param state
     */
    void sendMessage(MessageType type, Edge e, Weight core, Integer level, NodeState state) {
        Integer receiverId = getReceiver(e);
        INode receiver = findNode(receiverId);
        if(receiver == null) {
            throw new RuntimeException("Unable to find node with id " + receiverId);
        }
        Runnable send = () -> {
            try {
                System.out.println(this.id + ": Sending (" + type + "," + core + "," + level + ") to " + receiverId);
                Random random = new Random();
                Integer timestamp = this.sendCounter.get(e);
                this.sendCounter.put(e, timestamp + 1);
                Thread.sleep(random.nextInt(NETWORK_DELAY));
                Message m = new Message(type, timestamp, this.id, core, level, state);
                receiver.receiveMessage(m);
            } catch (InterruptedException | RemoteException e1) {
                e1.printStackTrace();
            }
        };
        new Thread(send).start();
    }

    void setState(NodeState state) {
        this.state = state;
    }

    void setFragmentLevel(Integer level) {
        this.fragmentLevel = level;
    }
}
