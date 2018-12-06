public class Weight implements Comparable<Weight> {
    private final Integer weight;
    private final Integer lowerId;
    private final Integer higherId;

    public static final Weight INFINITE = new Weight(Integer.MAX_VALUE, -1, -1);

    public Weight(int w, int low, int high) {
        this.weight = w;
        this.lowerId = low;
        this.higherId = high;
    }

    public Weight(Weight weight) {
        this.weight = weight.weight;
        this.lowerId = weight.lowerId;
        this.higherId = weight.lowerId;
    }

    @Override
    public int compareTo(Weight w) {
        if(w == null) {
            throw new NullPointerException("Compared weight is null.");
        } else if (!weight.equals(w.weight)) {
            return weight.compareTo(w.weight);
        } else if (!lowerId.equals(w.lowerId)) {
            return lowerId.compareTo(w.lowerId);
        }
        return higherId.compareTo(w.higherId);

    }

    @Override
    public boolean equals(Object other) {
        if(other == null) {
            return false;
        } else if (other instanceof Weight) {
            return weight.equals(((Weight) other).weight) &&
                    lowerId.equals(((Weight) other).lowerId) &&
                    higherId.equals(((Weight) other).higherId);
        }
        return false;
    }

    public int hashCode() {
        return lowerId.hashCode() + higherId.hashCode() + weight.hashCode();
    }
}
