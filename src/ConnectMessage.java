public class ConnectMessage {
    final Integer value;
    final Integer from;
    final Edge edge;

    public ConnectMessage(Integer from, Integer value, Edge e) {
        this.from = from;
        this.value = value;
        this.edge = e;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o instanceof ConnectMessage) {
            return ((ConnectMessage) o).from.equals(this.from) &&
                    ((ConnectMessage) o).value.equals(this.value) &&
                    ((ConnectMessage) o).edge.equals(this.edge);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return from.hashCode() + value.hashCode() + edge.hashCode();
    }


    public boolean checkPreconditions(Integer fragmentLevel, EdgeState edgeState) {
        // A connect message is deferred is value is equal to or higher than the fragment level
        // and the edge state is unknown
        return this.value < fragmentLevel || !edgeState.equals(EdgeState.UNKNOWN);
    }
}
