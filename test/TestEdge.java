import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestEdge {

    Edge e1, e2, e3;

    @BeforeEach
    void setUp() {
        e1 = new Edge(1, 2, new Weight(1, 1, 2));
        e2 = new Edge(2, 3, new Weight(1, 2, 3));
        e3 = new Edge(1, 2, new Weight(1, 1, 2));
    }

    @Test
    void testEquals() {
        assertEquals(e1, e3);
    }

    @Test
    void testUnequal() {
        assertNotEquals(e1, e2);
    }

    @Test
    void testEqualHashes() {
        assertEquals(e1.hashCode(), e3.hashCode());
    }
}
