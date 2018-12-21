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
                    ((ConnectMessage) o).edge.equals(this.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return from.hashCode() + value.hashCode() + edge.hashCode();
    }
}
