package com.reganbarua.jujulauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusMathTest {
    @Test fun battery() { assertEquals(87, StatusMath.batteryPercent(87, 100)); assertEquals(50, StatusMath.batteryPercent(2000, 4000)); assertEquals(100, StatusMath.batteryPercent(-1, 100)) }
    @Test fun net() { assertEquals("5G", StatusMath.netLabel(20)); assertEquals("4G", StatusMath.netLabel(13)); assertEquals("3G", StatusMath.netLabel(3)); assertEquals("", StatusMath.netLabel(0)) }
    @Test fun wifi() { assertEquals(4, StatusMath.wifiBars(-40)); assertEquals(2, StatusMath.wifiBars(-75)); assertEquals(0, StatusMath.wifiBars(-95)) }
    @Test fun clock() { assertEquals("12:05", StatusMath.hourMinute(0, 5)); assertEquals("12:30", StatusMath.hourMinute(12, 30)); assertEquals("4:48", StatusMath.hourMinute(16, 48)) }
}
