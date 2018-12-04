
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Node implements Runnable, Serializable, INode {
    private final List edges;
    private final Integer id;
    private NodeState state = NodeState.SLEEPING;
    private Integer fragmentLevel = 0; // the level of the fragment this node belongs to
    private Weight fragmentName = null; // the name of the fragment this node belongs to
    private Edge toCore = null;  // the edge that leads to the fragment core
    private Integer reportsIncoming; // the number of reports still expected
    private Edge moeCandidate; // the edge leading towards the best candidate for the moe
    private Weight weightCandidate; // the weight of the best candidate for the moe
    private Edge testing; // the edge this node is currently testing for the moe

    public Node(Integer id, List<Integer> neighbourIds) {
        this.id = id;
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
    }
}
