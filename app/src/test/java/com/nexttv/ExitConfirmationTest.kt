package com.nexttv
import com.nexttv.core.tv.ExitConfirmation
import org.junit.Assert.*
import org.junit.Test
class ExitConfirmationTest {
 @Test fun requiresTwoBacksWithinWindow(){val gate=ExitConfirmation();assertFalse(gate.press(100));assertTrue(gate.press(900));assertFalse(gate.press(1000))}
 @Test fun expiredConfirmationNeedsNewPair(){val gate=ExitConfirmation();assertFalse(gate.press(10));assertFalse(gate.press(2011));assertTrue(gate.press(2200))}
 @Test fun interactionCancelsPreviousConfirmation(){val gate=ExitConfirmation();assertFalse(gate.press(10));gate.reset();assertFalse(gate.press(100));assertTrue(gate.press(200))}
 @Test fun playerConfirmationCannotArmApplicationExit(){val player=ExitConfirmation();val home=ExitConfirmation();assertFalse(player.press(10));assertTrue(player.press(100));assertFalse(home.press(110));assertTrue(home.press(200))}
 @Test fun clockRollbackNeverExits(){val gate=ExitConfirmation();assertFalse(gate.press(100));assertFalse(gate.press(99))}
}
