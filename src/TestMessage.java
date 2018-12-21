public class TestMessage {
    final Integer from;
    final Integer level;
    final Weight weight;

    public TestMessage(Integer from, Integer level, Weight weight) {
        this.from = from;
        this.level = level;
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o instanceof TestMessage) {
            return this.from.equals(((TestMessage) o).from) &&
                    this.level.equals(((TestMessage) o).level) &&
                    this.weight.equals(((TestMessage) o).weight);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return from.hashCode() + level.hashCode() + weight.hashCode();
    }
}
