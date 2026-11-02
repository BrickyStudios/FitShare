package com.fitshare.ui.screens

import com.fitshare.data.StatKey.AVG_HR
import com.fitshare.data.StatKey.DISTANCE
import com.fitshare.data.StatKey.SPEED
import com.fitshare.data.StatKey.TIME
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleStatTest {
    @Test fun addRemoveAndOverflow() {
        assertEquals(listOf(DISTANCE, TIME, SPEED), toggleStat(listOf(DISTANCE, TIME), SPEED, 3))
        assertEquals(listOf(TIME, SPEED, AVG_HR), toggleStat(listOf(DISTANCE, TIME, SPEED), AVG_HR, 3)) // oldest drops out
        assertEquals(listOf(DISTANCE, SPEED), toggleStat(listOf(DISTANCE, TIME, SPEED), TIME, 3))
        assertEquals(listOf(DISTANCE), toggleStat(listOf(DISTANCE), DISTANCE, 3)) // never empty
    }
}
