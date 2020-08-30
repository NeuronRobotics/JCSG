package eu.mihosoft.vrl.v3d;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class SubTransformableTest {
    @Test
    public void vectorsMovedTest() {
        CSG cube = new Cube(4.0).toCSG();
        Map<String, Transformable> st = cube.getSubTransformable();

        st.put("pointToConnect", new Vector3d(-2.0, -1.0, -2.0));
        st.put("top", Vector3d.z(4));

        cube.movex(3);
        cube.movez(2);

        assertEquals(st.get("pointToConnect"), new Vector3d(1.0, -1.0, 0.0));
        assertEquals(st.get("top"), new Vector3d(3.0, 0.0, 6.0));
    }

    @Test
    public void csgUnionTest() {
        CSG hat = getHatWithSubTransformable();
        Map<String, Transformable> st = hat.getSubTransformable();

        CSG union = hat.union(new Cube(0.5).toCSG());
        assertTrue(union.getSubTransformable().containsKey("s"));
        assertTrue(union.getSubTransformable().containsKey("v"));
        assertNotSame(union.getSubTransformable(), st);

        // reverse
        union = new Cube(0.5).toCSG().union(hat);
        assertTrue(union.getSubTransformable().containsKey("s"));
        assertTrue(union.getSubTransformable().containsKey("v"));
        assertNotSame(union.getSubTransformable(), st);
    }

    private CSG getHatWithSubTransformable() {
        CSG hat = new Cylinder(3.0, 2.2).toCSG();
        CSG ornament = new Dodecahedron(1.1).toCSG();
        hat.getSubTransformable().put("s", ornament);
        hat.getSubTransformable().put("v", Vector3d.y(-3.24));
        return hat;
    }

    @Test
    public void cloneTest() {
        CSG hat = getHatWithSubTransformable();
        Map<String, Transformable> st = hat.getSubTransformable();

        Map<String, Transformable> stOfClone = hat.clone().getSubTransformable();

        assertNotSame(st.get("s"), stOfClone.get("s"));
        assertEquals(st.get("v"), stOfClone.get("v"));
        assertNotSame(st.get("v"), stOfClone.get("v"));
    }

    @Test
    public void disabledInPlaceTransformationsTest() {
        CSG hat = getHatWithSubTransformable();
        hat.setSubTransformableMutationEnabled(false);
        Map<String, Transformable> st = hat.getSubTransformable();
        Map<String, Transformable> stOfTransformed = hat.movey(9).getSubTransformable();

        assertNotSame(st.get("v"), stOfTransformed.get("v"));
    }
}
