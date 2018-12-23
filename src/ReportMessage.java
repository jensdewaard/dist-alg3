public class ReportMessage {
    final Integer from;
    final Weight weight;

    public ReportMessage(Integer f, Weight w) {
        this.from = f;
        this.weight = w;
    }

    public boolean equals(Object o) {
        if(o == null) return false;
        if(o instanceof ReportMessage) {
            return this.from.equals(((ReportMessage) o).from) &&
                    this.weight.equals(((ReportMessage) o).weight);
        }
        return false;
    }

    public int hashCode() {
        return this.from.hashCode() + this.weight.hashCode();
    }

    public boolean checkPreconditions(Edge j, Edge inBranch, NodeState state) {
        // Report messages are deferred if w is equal to the inbranch
        // and the state of FIND
        return !j.equals(inBranch) || state != NodeState.FIND;
    }
}
