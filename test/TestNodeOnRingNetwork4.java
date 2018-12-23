
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TestNodeOnRingNetwork4 {

    private Node spy1, spy2, spy3, spy4;
    private Edge e12, e23, e34, e14;

    @BeforeEach void setUp() {
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

    @Nested class Wakeup {
        @Test void SendsConnectMessageOnLowestEdge() {
            spy1.wakeup();
            verify(spy1).sendMessage(MessageType.CONNECT,
                    e12,
                    null,
                    0,
                    null
            );
        }

        @Test void LowestEdgeIsInTree() {
            spy1.wakeup();
            assertEquals(EdgeState.IN_MST, spy1.getEdgeState(e12));
        }

        @Test void SetsNodeStateToFound() {
            spy1.wakeup();
            assertEquals(NodeState.FOUND, spy1.state);
        }

        @Test void SetsFragmentsLevelToZero() {
            spy1.wakeup();
            assertEquals(Integer.valueOf(0), spy1.fragmentLevel);
        }

        @Test void SetsFindCountToZero() {
            spy1.wakeup();
            assertEquals(Integer.valueOf(0),  spy1.findCount);
        }
   }

    @Nested class ReceiveConnect {
        /*
            The ReceiveConnect function is dependant on the state of the node (4),
            the edge (3) and the comparison between current node level and the incoming
            level. These combinations require 36 tests. There are two versions of
            program flow when the node is awake ; if the incoming connect is from
            the node along its moe or from a higher node.

             In addition, a test to preclude
            connections from self (1) and to check if waking up works (3), for a total
            of 31 tests.
         */

        @Test void FromSelfIsAnError() {
            Executable sut = () -> spy1.receiveMessage(
                    MessageType.CONNECT,
                    1,
                    null,
                    0,
                    null
            );
            // Nodes should not be able to receive a connect message from themselves
            assertThrows(RuntimeException.class, sut);
        }

        @Test void WhileAsleepWakesUp() {
            assertEquals(NodeState.SLEEPING, spy1.state);
            spy1.receiveMessage(MessageType.CONNECT,
                    2,
                    null,
                    2,
                    null);
            verify(spy1).wakeup();
        }

        @Test void WhileSearchingDoesNotWake() {
            spy1.state = NodeState.FIND;
            spy1.receiveMessage(MessageType.CONNECT,
                    2, null, 2, null);
            verify(spy1,never()).wakeup();
        }

        @Test void WhileFoundDoesNotWake() {
            spy1.state = NodeState.FOUND;
            spy1.receiveMessage(MessageType.CONNECT, 2, null, 2, null);
            verify(spy1, never()).wakeup();
        }

        @Nested class FromHigherLevel {
            /* If a connect message is received from a fragment with a higher level,
               there are three possibilities: 1) the fragments merge, 2) the message
               is deferred until the receiving fragment has a higher level, or 3)
               an error occurs when the connect messages came in on a rejected branch.
             */
            @BeforeEach void setup() {
                assertEquals(Integer.valueOf(0), spy1.fragmentLevel);
            }

            @Nested class WhileAsleepAndFromLowerNode {
                @BeforeEach void setup() {
                    assertEquals(NodeState.SLEEPING, spy1.state);
                }

                @Test void FromUnknownEdge_Merges() {
                    assertEquals(EdgeState.UNKNOWN, spy1.getEdgeState(e12));
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 2, null);
                    verify(spy1).merge(e12);
                }

                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2,
                            null, 2, null);
                    verify(spy1).merge(e12);
                }

                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT, 2,
                            null, 2, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }

            @Nested class WhileAsleepAndFromHigherNode {
                @BeforeEach void setup() {
                    assertEquals(NodeState.SLEEPING, spy1.state);
                }
                @Test void FromUnknownEdge_Defers() {
                    assertEquals(EdgeState.UNKNOWN, spy1.getEdgeState(e14));
                    spy1.receiveMessage(MessageType.CONNECT, 4, null, 2,null);
                    verify(spy1).deferConnect(any(), any(), any());
                }
                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e14, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 4, null, 2, null);
                    verify(spy1).merge(e14);
                }
                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e14, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT, 4, null, 2, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }

            @Nested class WhileSearching {
                @BeforeEach void setup() {
                    spy1.state = NodeState.FIND;
                }

                @Test void FromUnknownEdge_Defers() {
                    assertEquals(EdgeState.UNKNOWN, spy1.getEdgeState(e12));
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 2, null);
                    verify(spy1).deferConnect(any(), any(), any());
                }

                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 2, null);
                    verify(spy1).merge(e12);
                }

                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT,
                            2, null, 2, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }

            @Nested class WhileFound {
                @BeforeEach void setup () {
                    spy1.state = NodeState.FOUND;
                }

                @Test void FromUnknownEdge_Merges() {
                    assertEquals(EdgeState.UNKNOWN, spy1.getEdgeState(e12));
                    spy1.receiveMessage(MessageType.CONNECT,
                            2, null, 2, null);
                    verify(spy1).deferConnect(any(), any(), any());
                }

                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 2, null);
                    verify(spy1).merge(e12);
                }

                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT, 2,
                            null, 2, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }
        }

        @Nested class FromSameLevel {
            // Fragments of the same level should be treated the same as fragments of a
            // higher level.
            @BeforeEach void setup () {
                spy1.fragmentLevel = 1;
            }

            @Nested class WhileAsleepAndFromLowerNode {
                @BeforeEach void setup() {
                    spy1.state = NodeState.SLEEPING;
                }
                @Test void FromUnknownEdge_Merges() {
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).merge(e12);
                }
                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).merge(e12);
                }
                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () ->
                            spy1.receiveMessage(MessageType.CONNECT,
                                    2,
                                    null,
                                    1,
                                    null);
                    assertThrows(RuntimeException.class, sut);
                }
            }

            @Nested class WhileAsleepAndFromHigherNode {
                @BeforeEach void setup() {
                    spy1.state = NodeState.SLEEPING;
                }
                @Test void FromUnknownEdge_Merges() {
                    spy1.receiveMessage(MessageType.CONNECT, 4, null, 1, null);
                    verify(spy1).deferConnect(any(), any(), any());
                }
                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e14, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 4, null, 1, null);
                    verify(spy1).merge(e14);
                }
                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e14, EdgeState.NOT_IN_MST);
                    Executable sut = () ->
                            spy1.receiveMessage(MessageType.CONNECT,
                                    4,
                                    null,
                                    1,
                                    null);
                    assertThrows(RuntimeException.class, sut);
                }
            }

            @Nested class WhileSearching {
                @BeforeEach void setup() {
                    spy1.state = NodeState.FIND;
                }
                @Test void FromUnknownEdge_Defers() {
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).deferConnect(any(), any(), any());
                }
                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).merge(e12);
                }
                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }

            @Nested class WhileFound {
                @BeforeEach void setup() {
                    spy1.state = NodeState.FOUND;
                }
                @Test void FromUnknownEdge_Defers() {
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).deferConnect(any(), any(), any());
                }
                @Test void FromTreeEdge_Merges() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                }
                @Test void FromRejectedEdge_IsError() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }
        }

        @Nested class FromLowerLevel {
            // Connection attempts from fragments of a lower level should always
            // results in the receiving fragment absorbing that fragment. Exceptions
            // are when receiving a connect on a rejected edge.
            @BeforeEach void setup() {
                spy1.fragmentLevel = 4;
            }
            @Nested class WhileSleeping {
                // Because the process of waking up resets the fragment level of a node
                // to zero, there is nothing to be tested here; it cannot happen.
            }

            @Nested class WhileSearching {
                @BeforeEach void setup() {
                    spy1.state = NodeState.FIND;
                }
                @Test void FromUnknownEdge() {
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).absorb(e12);
                }
                @Test void FromTreeEdge() {
                    spy1.updateEdgeState(e12, EdgeState.IN_MST);
                    spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    verify(spy1).absorb(e12);
                }
                @Test void FromRejectedEdge() {
                    spy1.updateEdgeState(e12, EdgeState.NOT_IN_MST);
                    Executable sut = () -> spy1.receiveMessage(MessageType.CONNECT, 2, null, 1, null);
                    assertThrows(RuntimeException.class, sut);
                }
            }
        }
    }

    @Nested class TestMethod {
        @Test void LowestEdgeInTree() {
            spy3.updateEdgeState(e23, EdgeState.IN_MST);
            spy3.test();
            verify(spy3).sendMessage(
                    MessageType.TEST,
                    e34,
                    spy3.fragmentName,
                    spy3.fragmentLevel,
                    null
            );
            assertEquals(e34, spy3.testEdge);
        }

        @Test void AllEdgesKnownSendsNoTests() {
            spy3.updateEdgeState(e23, EdgeState.IN_MST);
            spy3.updateEdgeState(e34, EdgeState.IN_MST);
            spy3.test();
            verify(spy3, never()).sendMessage(eq(MessageType.TEST), any(), any(), any(), any());
            assertNull(spy3.testEdge);
        }

    }

    @Nested class ReceiveInitiate {
        @BeforeEach void setUp() {
            doNothing().when(spy2).test();
        }

        @Test void MustComeFromTreeBranch() {
            Executable sut = () -> spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    e12.weight,
                    1,
                    NodeState.FIND);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void InStateFindStartsTest() {
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FIND);
            verify(spy2).test();
        }

        @Test void InStateFindDoesNotPropagateUpstream() {
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FIND);
            verify(spy2, never()).sendMessage(any(), eq(e12), any(), any(), any());
        }

        @Test void InStateFindDoesNotPropagateToRejected() {
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e23, EdgeState.NOT_IN_MST);
            spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    e12.weight,
                    1,
                    NodeState.FIND);
            verify(spy2, never()).sendMessage(any(), eq(e23), any(), any(), any());
        }

        @Test void InStateFindIncrementsFindCountForTreeBranches() {
            assertEquals(Integer.valueOf(0), spy2.findCount);
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e23, EdgeState.IN_MST);
            spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    e12.weight,
                    1,
                    NodeState.FIND);
            assertEquals(Integer.valueOf(1), spy2.findCount);
        }

        @Test void InStateFindDoesNotIncrementFindCountIfNoTreeBranches() {
            assertEquals(Integer.valueOf(0), spy2.findCount);
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e23, EdgeState.NOT_IN_MST);
            spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    e12.weight,
                    1,
                    NodeState.FIND);
            assertEquals(Integer.valueOf(0), spy2.findCount);
        }

        @Test void InStateFoundPropagatesDownstream() {
            // Initiate messages with the state found serve as "inform" messages
            // that propagate throughout the fragment to update everyone's information
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e23, EdgeState.IN_MST);
            spy2.receiveMessage(
                    MessageType.INITIATE,
                    1,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FOUND
            );
            // The message should be propagated downstream
            verify(spy2).sendMessage(
                    MessageType.INITIATE,
                    e23,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FOUND
            );
        }

        @Test void InStateFoundDoesNotPropagateUpstream() {
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e23, EdgeState.IN_MST);
            spy2.receiveMessage(
                    MessageType.INITIATE,
                    1,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FOUND
            );
            verify(spy2, never()).sendMessage(any(), eq(e12), any(), any(), any());
        }

        @Test void InStateFindPropagatesDownstream() {
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.updateEdgeState(e23, EdgeState.IN_MST);
            spy2.receiveMessage(MessageType.INITIATE,
                    1,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FIND);
            verify(spy2).sendMessage(MessageType.INITIATE,
                    e23,
                    new Weight(1, 1, 2),
                    1,
                    NodeState.FIND);
            // In addition to just updating the local state with the contents of the message
            // the receiving node is also instructed to started probing its neighbours
        }
    }

    @Nested class Report {
        @Test void OnlyReportsWhenNoOutstandingReportsInSubtree() {
            spy2.findCount = 1;
            spy2.report();
            verify(spy2, never()).sendMessage(eq(MessageType.REPORT), any(), any(), any(), any());
        }

        @Test void OnlyReportsWhenNotTesting() {
            spy3.testEdge = e34;
            spy3.report();
            verify(spy3, never()).sendMessage(eq(MessageType.REPORT), any(), any(), any(), any());
        }

        @Test void SendsAReportMessage() {
            spy3.inBranch = e23;
            spy3.report();
            verify(spy3).sendMessage(eq(MessageType.REPORT), eq(e23), any(), any(), any());
        }
    }

    @Nested class ReceiveReport {
        @BeforeEach void setUp() {
            spy2.findCount = 1;
        }

        @Test void testReceiveReportFromSubTreeAttemptsReport() {
            // If a node receives a report from the subtree, it incorporates
            // the knowledge of the subtree's moe and may report back to the
            // root
            spy2.updateEdgeState(e23, EdgeState.IN_MST);
            spy2.inBranch = e12;
            spy2.receiveMessage(MessageType.REPORT,
                    3,
                    Weight.INFINITE, // this would mean that node 3 did not find any possible moe
                    null,
                    null
            );
            verify(spy2).report();
        }

        @Test void WithNoOutstandingReportsIsAnError() {
            spy2.inBranch = e12;
            spy2.findCount = 0;
            Executable sut = () -> spy2.receiveMessage(MessageType.REPORT,
                    3,
                    Weight.INFINITE,
                    null,
                    null);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void ReportsFromTheCoreAreAllowed() {
            spy1.inBranch = e12;
            spy1.updateEdgeState(e12, EdgeState.IN_MST);
            spy1.fragmentName = e12.weight;
            spy1.receiveMessage(MessageType.REPORT,
                    2, Weight.INFINITE, null, null);
        }

        @Test void FromARejectedBranchIsAnError() {
            spy2.updateEdgeState(e23, EdgeState.NOT_IN_MST);
            Executable sut = () -> spy2.receiveMessage(MessageType.REPORT,
                    3,
                    Weight.INFINITE,
                    null,
                    null);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void FromABranchInUnknownStateIsAnError() {
            Executable sut = () -> spy2.receiveMessage(MessageType.REPORT,
                    4,
                    Weight.INFINITE,
                    null, null);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void FromInBranchWhileStillSearchingDefersReport() {
            // If a report comes in from the inbranch, that means it has been sent
            // from the other side of the core with the result of that subtree. If
            // this node is still waiting for its subtree, we defer the report message.
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.setState(NodeState.FIND);
            spy2.inBranch = e12;
            spy2.receiveMessage(MessageType.REPORT,
                    1,
                    Weight.INFINITE,
                    null,
                    null
            );
            verify(spy2).deferReport(any());
        }

        @Test void UpdatesKnowledgeOfMoe() {
            spy2.setState(NodeState.FIND);
            spy2.updateEdgeState(e23, EdgeState.IN_MST);
            spy2.inBranch = e12;
            spy2.receiveMessage(MessageType.REPORT,
                    3,
                    e34.weight,
                    null,
                    null);
            assertEquals(e34.weight, spy2.bestWeight);
            assertEquals(e23, spy2.bestEdge);
        }

        @Test void FromInBranchWhileDoneAndNoMoeWereFound() {
            doNothing().when(spy2).changeRoot();
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.setState(NodeState.FOUND);
            spy2.inBranch = e12;
            spy2.bestWeight = Weight.INFINITE;
            spy2.receiveMessage(MessageType.REPORT,
                    1,
                    Weight.INFINITE,
                    null,
                    null
            );
            verify(spy2).HALT(); // no new edges were found on the frontier of the
            // fragment, so we stop.
        }

        @Test void FromInBranchWhileDoneAndMoeIsInOtherSubtree() {
            doNothing().when(spy2).changeRoot();
            spy2.findCount = 1;
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.setState(NodeState.FOUND);
            spy2.inBranch = e12;
            spy2.bestWeight = Weight.INFINITE;
            spy2.receiveMessage(MessageType.REPORT,
                    1,
                    e14.weight,
                    null,
                    null);
            // In this case, it should do nothing at all, as the other side of the
            // core fragment will take care of it
            verify(spy2, never()).changeRoot();
            verify(spy2, never()).HALT();
            verify(spy2, never()).deferReport(any());
        }

        @Test void FromInBranchWhileDoneAndMoeIsInThisSubtree() {
            doNothing().when(spy2).changeRoot();
            spy2.setState(NodeState.FOUND);
            spy2.updateEdgeState(e12, EdgeState.IN_MST);
            spy2.inBranch = e12;
            spy2.bestWeight = e23.weight;
            spy2.receiveMessage(MessageType.REPORT,
                    1,
                    Weight.INFINITE,
                    null,
                    null);
            verify(spy2).changeRoot();
        }

    }

    @Nested class Absorb {
        @Test void includesTheEdge() {
            // When absorbing a fragment on edge j, it replies with an initiate message
            // and remembers that edge j is part of the MST.
            spy1.absorb(e12);
            verify(spy1).sendMessage(
                    eq(MessageType.INITIATE),
                    eq(e12),
                    any(), any(), any());
            verify(spy1).updateEdgeState(e12, EdgeState.IN_MST);
        }

        @Test void sendsAnInitiateMessage() {
            spy1.absorb(e12);
            verify(spy1).sendMessage(
                    eq(MessageType.INITIATE),
                    eq(e12),
                    any(), any(), any());
        }

        @Test void incrementsTheFindCountIfSearching() {
            assertEquals(Integer.valueOf(0), spy1.findCount);
            spy1.state = NodeState.FIND;
            spy1.absorb(e12);
            assertEquals(Integer.valueOf(1), spy1.findCount);
        }

        @Test void doesNotIncrementTheFoundCountIfFound() {
            assertEquals(Integer.valueOf(0), spy1.findCount);
            spy1.state = NodeState.FOUND;
            spy1.absorb(e12);
            assertEquals(Integer.valueOf(0), spy1.findCount);
        }
    }

    @Nested class ReceiveTest {
        @Test void rejectsTestsFromSameFragment() {
            spy3.setState(NodeState.FIND);
            spy3.setFragmentLevel(1);
            spy3.fragmentName = e12.weight;
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    1,
                    null
            );
            verify(spy3).sendMessage(eq(MessageType.REJECT), any(), any(), any(), any());
        }

        @Test void acceptsTestsFromSmallerFragments() {
            // If we receive a test from a smaller or equal fragment, we will accept
            // the test as we can absorb that fragment or merge with it
            spy3.setState(NodeState.FIND);
            spy3.fragmentLevel = 2;
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    1,
                    null);
            verify(spy3).sendMessage(eq(MessageType.ACCEPT), any(), any(), any(), any());
        }

        @Test void acceptsTestsFromEqualSizeFragments() {
            spy3.setState(NodeState.FIND);
            spy3.fragmentLevel = 1;
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    1,
                    null);
            verify(spy3).sendMessage(eq(MessageType.ACCEPT), any(), any(), any(), any());
        }

        @Test void cannotReceiveTestsFromLevelZeroFragments() {
            Executable sut = () -> spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    0,
                    null);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void wakesTheNodeIfSleeping() {
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    1,
                    null);
            verify(spy3).wakeup();
        }

        @Test void doesNotWakeTheNodeIfNodeStateIsSearching() {
            spy3.state = NodeState.FIND;
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    1,
                    null);
            verify(spy3, never()).wakeup();
        }

        @Test void doesNotWakeTheNodeIfNodeStateIsFound() {
            spy3.state = NodeState.FOUND;
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    1,
                    null);
            verify(spy3, never()).wakeup();
        }

        @Test void cannotReceiveTestsFromSelf() {
            Executable sut = () -> spy3.receiveMessage(MessageType.TEST,
                    3,
                    e23.weight,
                    1,
                    null);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void defersTestsFromLargerFragments() {
            // if we receive a test from a larger fragment, we will wait
            // with responding until we are a larger or equal fragment ourselves
            spy3.setState(NodeState.FIND);
            spy3.fragmentLevel = 1;
            spy3.receiveMessage(MessageType.TEST,
                    2,
                    e12.weight,
                    2,
                    null);
            verify(spy3).deferTest(2, 2, e12.weight);
        }
    }

    @Nested class Merge {
        @Test void includesTheEdgeInTree() {
            // When merging with a fragment on edge j, it replies with an initiate
            // message of a higher level. In addition, the edge j is part of the
            // tree.
            spy1.merge(e12);
            verify(spy1).updateEdgeState(e12, EdgeState.IN_MST);
        }

        @Test void startsANewRoundWithAHigherLevel() {
            assertEquals(Integer.valueOf(0), spy1.fragmentLevel);
            spy1.merge(e12);
            verify(spy1).sendMessage(eq(MessageType.INITIATE), eq(e12), any(), eq(1), any());
        }
    }

    @Nested class IdentifyEdge {
        @Test void selfLoop() {
            Executable sut = () -> spy1.identifyEdge(1);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void testIdentifyEdge() {
            assertEquals(e34, spy3.identifyEdge(4));
        }

        @Test void unknownEdge() {
            Executable sut = () -> spy2.identifyEdge(4);
            assertThrows(RuntimeException.class, sut);
        }
    }

    @Nested class UpdateEdgeState {
        @Test void WithNonexistentEdge() {
            Executable sut = () -> spy1.updateEdgeState(e34, EdgeState.IN_MST);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void WithUnknownState() {
            Executable sut = () -> spy1.updateEdgeState(e12, EdgeState.UNKNOWN);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void WithNullEdge() {
            Executable sut = () -> spy1.updateEdgeState(null, EdgeState.IN_MST);
            assertThrows(RuntimeException.class, sut);
        }

        @Test void WithStateNull() {
            Executable sut = () -> spy1.updateEdgeState(e12, null);
            assertThrows(RuntimeException.class, sut);
        }
    }

    @Nested class ReceiveReject {
        @Test void rejectsTheEdge() {
            // When receiving a reject message on an edge j, this means that
            // j is not in in the tree and we need to keep looking
            spy2.testEdge = e23;
            spy2.receiveMessage(
                    MessageType.REJECT,
                    3,
                    null,
                    null,
                    null
            );
            verify(spy2).updateEdgeState(e23, EdgeState.NOT_IN_MST);
        }

        @Test void testsANewEdge() {
            spy2.testEdge = e23;
            spy2.receiveMessage(
                    MessageType.REJECT,
                    3,
                    null,
                    null,
                    null
            );
            verify(spy2).test();
        }

        @Test void canOnlyBeReceivedFromTestEdge() {
            spy2.testEdge = null;
            Executable sut = () -> spy2.receiveMessage(
                        MessageType.REJECT,
                        3,
                        null,
                        null,
                        null
            );
            assertThrows(RuntimeException.class, sut);
        }
    }

    @Nested class ReceiveAccept {
        // Accept messages from a neighbouring node indicate that the common
        // edge is a candidate for the moe of the fragment. Accept messages
        // should only come from nodes currently being tested.
        @Test void sendsReport() {
            // When receiving an accept, we have found a candidate for the moe,
            // so we propagate this candidate up to the tree
            spy2.inBranch = e12;
            spy2.testEdge = e23;
            spy2.receiveMessage(MessageType.ACCEPT,
                    3,
                    null,
                    null,
                    null);
            verify(spy2).report();
        }

        @Test void updatesBestWeightIfEdgeWeightIsLower() {
            assertEquals(Weight.INFINITE, spy2.bestWeight);
            spy2.testEdge = e23;
            spy2.receiveMessage(MessageType.ACCEPT,
                    3,
                    null,
                    null,
                    null);
            assertEquals(e23.weight, spy2.bestWeight);
        }

        @Test void doesNotUpdateIfEdgeWeightIsHigher() {
            spy2.bestWeight = e12.weight;
            spy2.testEdge = e23;
            spy2.receiveMessage(MessageType.ACCEPT,
                    3,
                    null,
                    null,
                    null);
            assertEquals(e12.weight, spy2.bestWeight);
        }

        @Test void canOnlyBeReceivedFromTestEdge() {
            spy2.testEdge = null;
            Executable sut = () -> spy2.receiveMessage(
                        MessageType.ACCEPT,
                        3,
                        null,
                        null,
                        null
            );
            assertThrows(RuntimeException.class, sut);
        }
    }

    @Nested class SendMessage {
        @Test void testSendMessage() {
            Node n5 = new Node(5, List.of(1));
            Edge e15 = new Edge(1, 5, new Weight(1, 1, 5));
            doNothing().when(spy1).receiveMessage(
                    any(),
                    any(),
                    any(),
                    any(),
                    any());
            Node spy5 = spy(n5);
            doReturn(spy1).when(spy5).findNode(1);

            spy5.sendMessage(MessageType.REPORT,
                    e15,
                    new Weight(5, 3, 1),
                    4,
                    NodeState.SLEEPING);
            verify(spy1, timeout(3000)).receiveMessage(MessageType.REPORT,
                    5,
                    new Weight(5, 3, 1),
                    4,
                    NodeState.SLEEPING
                    );
        }
    }
}
