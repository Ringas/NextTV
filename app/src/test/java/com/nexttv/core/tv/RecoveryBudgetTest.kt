package com.nexttv.core.tv
import org.junit.Assert.*
import org.junit.Test
class RecoveryBudgetTest {
 @Test fun retriesShareDeadline(){var now=0L;val b=RecoveryBudget{now};b.enter("one");now=12000;b.enter("one");assertEquals(13000,b.remaining(25000));now=26000;assertEquals(0,b.remaining(25000))}
 @Test fun channelChangeGetsNewBudget(){var now=5L;val b=RecoveryBudget{now};b.enter("one");now=10005;b.enter("two");assertEquals(25000,b.remaining(25000))}
 @Test fun stableOrExplicitSelectionResets(){var now=0L;val b=RecoveryBudget{now};b.enter("one");now=30000;b.reset();b.enter("one");assertEquals(25000,b.remaining(25000))}
 @Test fun normalPlaybackDoesNotConsumeRecovery(){var now=0L;val b=RecoveryBudget{now};b.enter("one");now=3000;b.pause();now=28000;assertEquals(22000,b.remaining(25000));b.enter("one");now=30000;assertEquals(20000,b.remaining(25000))}
 @Test fun explicitPauseDoesNotConsumeLoadingBudget(){var now=0L;val b=RecoveryBudget{now};b.enter("one");now=2000;b.pause();now=200000;b.enter("one");assertEquals(23000,b.remaining(25000))}
 @Test fun softwareAliasesOverrideIncompletePlatformFlags(){assertTrue(knownSoftwareDecoder("OMX.google.hevc.decoder",false));assertTrue(knownSoftwareDecoder("c2.android.avc.decoder",false));assertTrue(knownSoftwareDecoder("vendor.software",true));assertFalse(knownSoftwareDecoder("OMX.vendor.video.decoder",false))}
}
