package com.example.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalSyncPolicyTest {
    @Test fun `first upload does not require replacing cloud data`() {
        assertEquals(PersonalSyncDecision.UPLOAD, PersonalSyncPolicy.decide("local","empty",null,0,0))
    }
    @Test fun `a changed remote always requires a choice`() {
        assertEquals(PersonalSyncDecision.CHOOSE, PersonalSyncPolicy.decide("new-local","new-cloud","old",2,1))
        assertEquals(PersonalSyncDecision.CHOOSE, PersonalSyncPolicy.decide("old","new-cloud","old",2,1))
    }
    @Test fun `lost acknowledgement can be recognized without uploading again`() {
        assertEquals(PersonalSyncDecision.CURRENT, PersonalSyncPolicy.decide("same","same",null,1,0))
    }
    @Test fun `reset server cannot silently overwrite previous cloud history`() {
        assertEquals(PersonalSyncDecision.CHOOSE, PersonalSyncPolicy.decide("local","empty","old",0,5))
    }
    @Test fun `unchanged restored data does not upload again after local id remapping`() {
        assertEquals(PersonalSyncDecision.CURRENT, PersonalSyncPolicy.decide("remapped","original","remapped",3,3))
    }
}
