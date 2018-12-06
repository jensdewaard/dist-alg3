public class Edge implements Comparable<Edge> {
    final Integer source;
    final Integer target;
    final Weight weight;

    public Edge(Integer source, Integer target, Weight weight) {
        if(source.equals(target)) {
            throw new EdgeException("Source node and target node are equal");
        } else if(source > target) {
            
        }
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    @Override
    public int compareTo(Edge edge) {
        if(edge == null) {
            throw new NullPointerException("Compared Edge is null");
        }
        return weight.compareTo(edge.weight);
    }

    public String toString() {
        return "[" + source + ", " + target + "]";
    }
}
