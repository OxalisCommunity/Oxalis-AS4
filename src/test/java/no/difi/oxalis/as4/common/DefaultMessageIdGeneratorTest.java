package no.difi.oxalis.as4.common;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@PrepareForTest(DefaultMessageIdGenerator.class)
@RunWith(PowerMockRunner.class)
public class DefaultMessageIdGeneratorTest {

    private DefaultMessageIdGenerator generator;

    @Before
    public void before() {
        UUID uuid = UUID.fromString("8196c8e2-820f-4aec-a1ca-288a4d1d4020");
        mockStatic(UUID.class);
        given(UUID.randomUUID()).willReturn(uuid);
        generator = new DefaultMessageIdGenerator("seller.eu");
    }

    @Test
    public void testGenerateByTransmissionRequest() {
        assertThat(generator.generate()).isEqualTo("8196c8e2-820f-4aec-a1ca-288a4d1d4020@seller.eu");
    }
}
