public class Fragment {
    private final Edge core;
    private final Weight name;
    private final Integer level;

    public Fragment() {
        this.core = null;
        this.name = null;
        this.level = 0;
    }

    public Fragment(Edge core, Integer level) {
        this.core = core;
        this.name = core.weight;
        this.level = level;
    }

    public Fragment connect(Fragment f) {
        if(this.level.equals(f.level)) {
            //compare MOE? and if equal do
            if(true) {
                Edge moe = new Edge(new Weight(1, 1, 1,), 1, 1); // TODO actually find the moe
                return merge(f, moe);
            }
        } else if(this.level < f.level) {
            return absorb(f);
        }
        return f; // todo this is incorrect, the checks above should probably be done elsewhere
    }

    private Fragment merge(Fragment f, Edge moe) {
        Integer newLevel = level + 1;
        return new Fragment(moe, newLevel);
    }

    private Fragment absorb(Fragment f) {
        return f;
    }
}
