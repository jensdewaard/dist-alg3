import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class TestWeight {

    @Test
    void testEquals() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(1, 1, 1);
        assertEquals(w1, w2);
    }

    @Test
    void testEqualsDifferingWeight() {
        Weight w2 = new Weight(1, 1, 1);
        Weight w1 = new Weight(3, 1, 1);
        assertNotEquals(w1, w2);
    }

    @Test
    void testEqualsDifferingSource() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(1, 2, 1);
        assertNotEquals(w1, w2);
    }

    @Test
    void testEqualsDifferingTarget() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(1, 1, 2);
        assertNotEquals(w1, w2);
    }

    @Test
    void testCompareEquals() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(1, 1, 1);
        assertEquals(w1.compareTo(w2), 0);
    }

    @Test
    void testCompareLessWeight() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(2 , 2, 2);
        assertTrue(w1.compareTo(w2) < 0);
    }

    @Test
    void testCompareLessSource() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(1 , 2, 2);
        assertTrue(w1.compareTo(w2) < 0);
    }

    @Test
    void testCompareLessTarget() {
        Weight w1 = new Weight(1, 1, 1);
        Weight w2 = new Weight(1 , 1, 2);
        assertTrue(w1.compareTo(w2) < 0);
    }

    @Test
    void testCompareMoreWeight() {
        Weight w1 = new Weight(2, 1, 1);
        Weight w2 = new Weight(1, 1, 1);
        assertTrue(w1.compareTo(w2) > 0);
    }

    @Test
    void testCompareMoreSource() {
        Weight w1 = new Weight(1, 2, 1);
        Weight w2 = new Weight(1, 1, 1);
        assertTrue(w1.compareTo(w2) > 0);
    }

    @Test
    void testCompareMoreTarget() {
        Weight w1 = new Weight(1, 1, 2);
        Weight w2 = new Weight(1, 1, 1);
        assertTrue(w1.compareTo(w2) > 0);
    }
}
