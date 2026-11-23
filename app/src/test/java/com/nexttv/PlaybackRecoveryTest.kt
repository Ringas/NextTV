package com.nexttv
import com.nexttv.core.data.RouteRecovery
import org.junit.Assert.*
import org.junit.Test
/** Active route round; global next-channel/stability behavior is covered by ProductionRecoveryInstrumentation. */
class PlaybackRecoveryTest {
 @Test fun alternateRoutesOnlyOnce(){val r=RouteRecovery();r.reset(0);assertEquals(1,r.next(3,0));assertEquals(2,r.next(3,1));assertNull(r.next(3,2))}
 @Test fun singleRouteRetriesOnce(){val r=RouteRecovery();r.reset(0);assertEquals(0,r.next(1,0));assertNull(r.next(1,0))}
 @Test fun explicitResetStartsNewRound(){val r=RouteRecovery();r.reset(0);r.next(1,0);assertNull(r.next(1,0));r.reset(0);assertEquals(0,r.next(1,0))}
 @Test fun initiallySelectedRouteIsNotRetriedInMultiRouteRound(){val r=RouteRecovery();r.reset(1);assertEquals(2,r.next(3,1));assertEquals(0,r.next(3,2));assertNull(r.next(3,0));assertNull(RouteRecovery().next(0,0))}
}
