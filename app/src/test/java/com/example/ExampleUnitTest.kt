package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.ui.RosterData
import com.example.ui.RosterEmployee

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGetDynamicPatrolCountsRobustness() {
    // Assert that the patrol counts method runs and returns a 31-day list
    val patrolCounts = RosterData.getDynamicPatrolCounts()
    assertEquals(31, patrolCounts.size)

    val dayCounts = RosterData.getDynamicDayPatrolCounts()
    assertEquals(31, dayCounts.size)

    val nightCounts = RosterData.getDynamicNightPatrolCounts()
    assertEquals(31, nightCounts.size)

    // Verify it doesn't crash regardless of month switches and remains consistent
    RosterData.switchMonth(1)
    val janCounts = RosterData.getDynamicDayPatrolCounts()
    assertEquals(31, janCounts.size)

    RosterData.switchMonth(0)
    val decCounts = RosterData.getDynamicNightPatrolCounts()
    assertEquals(31, decCounts.size)
  }
}

