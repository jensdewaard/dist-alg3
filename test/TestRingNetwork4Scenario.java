import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestRingNetwork4Scenario {
    private Node spy1, spy2, spy3, spy4;
    private Edge e12, e23, e34, e14;

    @BeforeEach
    void setUp() {
        Node p1 = new Node(1, List.of(2, 4));
        Node p2 = new Node(2, List.of(1, 3));
        Node p3 = new Node(3, List.of(2, 4));
        Node p4 = new Node(4, List.of(1, 3));

        spy1 = spy(p1);
        spy2 = spy(p2);
        spy3 = spy(p3);
        spy4 = spy(p4);
        doNothing().when(spy1).sendMessage(any(), any(), any(), any(), any());
        doNothing().when(spy2).sendMessage(any(), any(), any(), any(), any());
        doNothing().when(spy3).sendMessage(any(), any(), any(), any(), any());
        doNothing().when(spy4).sendMessage(any(), any(), any(), any(), any());

        e12 = new Edge(1, 2, new Weight(1, 1, 2));
        e23 = new Edge(2, 3, new Weight(1, 2, 3));
        e34 = new Edge(3, 4, new Weight(1, 3, 4));
        e14 = new Edge(1, 4, new Weight(1, 1, 4));
    }

    @Test void testRound0() {
        assertEquals(NodeState.SLEEPING, spy1.state);
        spy1.wakeup();
        verify(spy1, times(1))
                .sendMessage(MessageType.CONNECT, e12, null, 0, null);
        assertEquals(NodeState.FOUND, spy1.state);
        assertEquals(EdgeState.IN_MST, spy1.getEdgeState(e12));
    }

    @Test void testRound1() {
        testRound0(); // we do it like this because junit5 has no way of explicitly ordering tests, and we need to
                      // do the rounds in proper order
        assertEquals(NodeState.SLEEPING, spy2.state);
        spy2.receiveMessage(MessageType.CONNECT, 1, null, 0, null);
        assertEquals(NodeState.FOUND, spy2.state);
        verify(spy2).sendMessage(MessageType.CONNECT, e12, null, 0, null);
        verify(spy2).sendMessage(MessageType.INITIATE, e12, e12.weight, 1, NodeState.FIND);
        assertEquals(EdgeState.IN_MST, spy2.getEdgeState(e12));
    }

    @Test void testRound2a() {
        testRound1();
        spy1.receiveMessage(MessageType.CONNECT, 2, null, 0, null);
        verify(spy1, times(1))
                .sendMessage(MessageType.INITIATE, e12, e12.weight, 1, NodeState.FIND);
    }

    @Test void testRound2b() {
        testRound2a();
        spy1.receiveMessage(MessageType.INITIATE, 2, e12.weight, 1, NodeState.FIND);
        assertEquals(NodeState.FIND, spy1.state);
        verify(spy1).sendMessage(MessageType.TEST, e14, e12.weight, 1, null);
    }

    @Test void testRound2c() {
        testRound2b();
        assertEquals(spy2.getEdgeState(e12), EdgeState.IN_MST);
        spy2.receiveMessage(MessageType.INITIATE, 1, e12.weight, 1, NodeState.FIND);
        verify(spy2, times(1)) // Not another Initiate to 1
                .sendMessage(MessageType.INITIATE, e12, e12.weight, 1, NodeState.FIND);
        verify(spy2, times(1)) // but a Test to 3
                .sendMessage(MessageType.TEST, e23, e12.weight, 1, null);
    }

    @Test void testRound2d() {
        testRound2c();
        spy4.receiveMessage(MessageType.TEST, 1, e12.weight, 1, null);
        verify(spy4).sendMessage(MessageType.ACCEPT, e14, null, null, null);
        verify(spy4, never()).sendMessage(MessageType.REJECT, e14, null, null, null);
        verify(spy4).sendMessage(MessageType.CONNECT, e14, null, 0, null);
    }

    @Test void testRound2da() {
        spy1.receiveMessage(MessageType.CONNECT, 4, null, 0, null);
        verify(spy1).deferConnect(any(), any(), any());
    }

    @Test void testRound2e() {
        testRound2d();
        spy1.receiveMessage(MessageType.ACCEPT, 4, null, null, null);
        verify(spy1).sendMessage(MessageType.REPORT, e12, e14.weight, null, null);
    }

    @Test void testRound2f() {
        testRound2e();
        spy3.receiveMessage(MessageType.TEST, 2, e12.weight, 1, null);
        verify(spy3).sendMessage(MessageType.ACCEPT, e23, null, null, null);
    }

    @Test void testRound2g() {
        testRound2f();
        spy2.receiveMessage(MessageType.ACCEPT, 3, null, null, null);
        verify(spy2).sendMessage(MessageType.REPORT, e12, e23.weight, null, null);
    }

    @Test void testRound2h() {
        testRound2g();
        spy2.receiveMessage(MessageType.REPORT, 1, e14.weight, null, null);
        verify(spy2, never()).changeRoot();
        verify(spy2, never()).HALT();
    }

    @Test void testRound2i() {
        testRound2h();
        spy1.receiveMessage(MessageType.REPORT, 2, e34.weight, null, null);
        verify(spy4, never()).sendMessage(MessageType.CONNECT, e14, null, 0, null);
        verify(spy1).changeRoot();
        verify(spy1).sendMessage(MessageType.CONNECT, e14, null, 1, null);
        assertEquals(EdgeState.IN_MST, spy1.getEdgeState(e14));
    }

    @Test void testRound2j() {
        testRound2i();
        assertEquals(Integer.valueOf(0), spy4.fragmentLevel);
        assertEquals(EdgeState.IN_MST, spy4.getEdgeState(e14));
        verify(spy4, never()).sendMessage(MessageType.CONNECT, e14, null, 0, null);
        spy4.receiveMessage(MessageType.CONNECT, 1, null, 1, null);
        verify(spy4).merge(e14);
        assertEquals(EdgeState.IN_MST, spy4.getEdgeState(e14));
        verify(spy4, times(1)).
                sendMessage(MessageType.INITIATE, e14, e14.weight, 2, null);
    }

}
