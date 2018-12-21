public class Edge implements Comparable<Edge> {
    final Integer source;
    final Integer target;
    final Weight weight;

    public Edge(Integer source, Integer target, Weight weight) {
        if(source.equals(target)) {
            throw new RuntimeException("Source node and target node are equal");
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

    @Override
    public boolean equals(Object o){
        if(o == null) {
            return false;
        }
        if(o instanceof Edge) {
            return this.source.equals(((Edge) o).source) &&
                    this.target.equals(((Edge) o).target) &&
                    this.weight.equals(((Edge) o).weight);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.source.hashCode() + this.target.hashCode() + this.weight.hashCode();
    }

    public String toString() {
        return "[" + source + ", " + target + "]";
    }
}
