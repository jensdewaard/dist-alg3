import java.io.Serializable;

public class Message implements Serializable, Comparable<Message> {
    final Integer clock;
    final MessageType type;
    final Integer sender;
    final Weight core;
    final Integer level;
    final NodeState state;

    public Message(MessageType type, Integer clock, Integer sender, Weight core, Integer level, NodeState state) {
        this.type = type;
        this.clock = clock;
        this.sender = sender;
        this.core = core;
        this.level = level;
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
       if(o == null) return false;
       if(o instanceof Message) {
           boolean stateEq = (state == null && ((Message) o).state == null) || state.equals(((Message) o).state);
           boolean levelEq = (level == null && ((Message) o).level == null) || level.equals(((Message) o).level);
           boolean coreEq = (core == null && ((Message) o).core == null) || core.equals(((Message) o).core);
           return this.type.equals(((Message) o).type) &&
                   this.clock.equals(((Message) o).clock) &&
                   this.sender.equals(((Message) o).sender) &&
                   stateEq && levelEq && coreEq;
       }
       return false;
    }

    @Override
    public int hashCode() {
        return type.hashCode() + clock.hashCode() + sender.hashCode() + core.hashCode() + level.hashCode() + state.hashCode();
    }

    @Override
    public int compareTo(Message o) {
        return this.clock.compareTo(o.clock);
    }
}
