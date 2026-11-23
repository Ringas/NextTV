package com.nexttv.baseline

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Same binary, cold process, interpreted vs packaged Baseline Profile. Device results are separate. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
 @get:Rule val rule=MacrobenchmarkRule()
 private fun run(mode:CompilationMode)=rule.measureRepeated(packageName=UserJourneys.PACKAGE,
  metrics=listOf(StartupTimingMetric()),compilationMode=mode,startupMode=StartupMode.COLD,
  iterations=5,setupBlock={pressHome()}){startActivityAndWait();with(UserJourneys){awaitHome()}}
 @Test fun coldWithoutProfile()=run(CompilationMode.None())
 @Test fun coldWithProfile()=run(CompilationMode.Partial(BaselineProfileMode.Require,warmupIterations=0))
}
