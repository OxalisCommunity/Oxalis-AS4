package network.oxalis.as4.common;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DefaultMessageIdGenerator.class)
public class DefaultMessageIdGeneratorTest {

    private DefaultMessageIdGenerator generator = new DefaultMessageIdGenerator("seller.eu");

    @Before
    public void before() {
        // Build UUID before UUID.class is mocked
        UUID uuid = UUID.fromString("8196c8e2-820f-4aec-a1ca-288a4d1d4020");
        mockStatic(UUID.class);
        when(UUID.randomUUID()).thenReturn(uuid);
    }

    @Test
    public void testGenerateByTransmissionRequest() {
        Assert.assertEquals("8196c8e2-820f-4aec-a1ca-288a4d1d4020@seller.eu", generator.generate());
    }
}
