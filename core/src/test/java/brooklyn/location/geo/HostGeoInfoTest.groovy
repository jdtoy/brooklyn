package brooklyn.location.geo;

import static org.testng.AssertJUnit.*

import org.testng.annotations.Test

import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.location.basic.SshMachineLocation

public class HostGeoInfoTest {
    private static final String IP = "192.168.0.1";
    
    private static final Location DOUBLE_LOCATION = new SimulatedLocation(name: "doubles", latitude: 50.0d, longitude: 0.0d);
    private static final Location BIGDECIMAL_LOCATION = new SimulatedLocation( name: "bigdecimals", latitude: 50.0, longitude: 0.0);
    private static final Location MIXED_LOCATION = new SimulatedLocation(name: "mixed", latitude: 50.0d, longitude: 0.0);
    
    private static final Location DOUBLE_CHILD = new SshMachineLocation(name: "double-child", address: IP, parentLocation: DOUBLE_LOCATION);
    private static final Location BIGDECIMAL_CHILD = new SshMachineLocation(name: "bigdecimal-child", address: IP, parentLocation: BIGDECIMAL_LOCATION);
    private static final Location MIXED_CHILD = new SshMachineLocation(name: "mixed-child", address: IP, parentLocation: MIXED_LOCATION);

        
    @Test
    public void testDoubleCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(DOUBLE_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testBigdecimalCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(BIGDECIMAL_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testMixedCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(MIXED_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testMissingCoordinates() {
    }
    
}
