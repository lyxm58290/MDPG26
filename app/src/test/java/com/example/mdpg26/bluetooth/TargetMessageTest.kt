package com.example.mdpg26.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetMessageTest {

    @Test
    fun task2Arrows_routeToTask2Target() {
        assertEquals(Task2Target(1, Task2Direction.LEFT), TargetMessage.parse("TARGET,1,LEFT"))
        assertEquals(Task2Target(2, Task2Direction.RIGHT), TargetMessage.parse("TARGET,2,RIGHT"))
    }

    @Test
    fun task2Arrows_tolerateWhitespaceNewlinesAndCase() {
        assertEquals(Task2Target(1, Task2Direction.RIGHT), TargetMessage.parse("  TARGET, 1 , RIGHT \r\n"))
        assertEquals(Task2Target(2, Task2Direction.LEFT), TargetMessage.parse("TARGET,2,left\n"))
    }

    @Test
    fun task2Arrows_onOtherObstacleIds_areIgnored() {
        assertNull(TargetMessage.parse("TARGET,0,LEFT"))
        assertNull(TargetMessage.parse("TARGET,3,RIGHT"))
        assertNull(TargetMessage.parse("TARGET,-1,LEFT"))
    }

    @Test
    fun task1ImageIds_keepTask1Behaviour() {
        assertEquals(TargetDetection(5, "23"), TargetMessage.parse("TARGET,5,23"))
        assertEquals(TargetDetection(1, "11"), TargetMessage.parse("TARGET,1,11"))
        assertEquals(TargetDetection(3, "A"), TargetMessage.parse("TARGET,3,A"))
    }

    @Test
    fun malformedLines_returnNull() {
        listOf(
            "",
            "TARGET",
            "TARGET,1",
            "TARGET,1,",
            "TARGET,x,LEFT",
            "TARGET,1,LEFT,extra",
            "target,1,LEFT",
            "ROBOT,1,2,N",
            "TARGET,,LEFT"
        ).forEach { assertNull("expected null for [$it]", TargetMessage.parse(it)) }
    }
}
