package io.github.resilience4j.timeout.internal;

import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class HillClimbTest {

    @Test
    public void testHillClimbExecutor() {
        HillClimbExecutor.HillClimbingOptions options = new HillClimbExecutor.HillClimbingOptions(() -> 2, () -> 1000);

        HillClimbExecutor.HillClimbing hc = new HillClimbExecutor.HillClimbing(options);

        int newMax = 0;
        int threadAdjustmentInterval = 0;
        long totalCompletions = 0;
        long priorCompletionCount = 0;
        int timer = 0;
        int lastSampleTimer = 0;

        int currentThreadCount = options.getMinThreads().get();

        // Initialize
        hc.forceChange(currentThreadCount, HillClimbExecutor.HillClimbingStateTransition.Initializing);

        ThreadLocalRandom randomGenerator = ThreadLocalRandom.current();

        boolean randomWorkloadJumps = true;
        String filename = "results.csv";

        try {
            FileWriter fw = new FileWriter(filename);
            fw.write("Time,Throughput,Threads\n");

            for (int mode = 1; mode <= 5; mode++)
            {
                int currentWorkLoad = 0;
                switch (mode)
                {
                    case 1:
                    case 5:
                        currentWorkLoad = 3;
                        break;
                    case 2:
                    case 4:
                        currentWorkLoad = 7;
                        break;
                    case 3:
                        currentWorkLoad = 10;
                        break;
                    default:
                        currentWorkLoad = 1;
                        break;
                }

                boolean reportedMsgInWorkload = false;
                int workLoadForSection = currentWorkLoad * 10000500;
                while (workLoadForSection > 0)
                {
                    if (randomWorkloadJumps)
                    {
                        int randomValue = randomGenerator.nextInt(21); // 0 to 20
                        if (randomValue >= 19)
                        {
                            int randomChange = randomGenerator.nextInt(-2, 3); // i.e. -2, -1, 0, 1, 2 (not 3)
                            if (randomChange != 0)
                            {
                                System.out.println("Changing workload from "+currentWorkLoad + " -> " + currentWorkLoad + randomChange);
                                currentWorkLoad += randomChange;
                            }
                        }
                    }
                    timer += 1; //tick-tock, each iteration of the loop is 1 second
                    totalCompletions += currentThreadCount;
                    workLoadForSection -= currentThreadCount;
                    //fprintf(fp, "%d,%d\n", min(currentWorkLoad, currentThreadCount), currentThreadCount);
                    double randomNoise = randomGenerator.nextDouble() / 100.0 * 5; // [0..1) -> [0..0.01) -> [0..0.05)
                    fw.write(timer +","+ (Math.min(currentWorkLoad, currentThreadCount) * (0.95 + randomNoise)) +","+ currentThreadCount + "\n");
                    // Calling HillClimbingInstance.Update(..) should ONLY happen when we need more threads, not all the time!!
                    if (currentThreadCount != currentWorkLoad)
                    {
                        // We naively assume that each work items takes 1 second (which is also our loop/timer length)
                        // So in every loop we complete 'currentThreadCount' pieces of work
                        int numCompletions = currentThreadCount;

                        // In win32threadpool.cpp it does the following check before calling Update(..)
                        // if (elapsed*1000.0 >= (ThreadAdjustmentInterval/2)) //
                        // Also 'ThreadAdjustmentInterval' is initially set like so ('INTERNAL_HillClimbing_SampleIntervalLow' = 10):
                        // ThreadAdjustmentInterval = CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_SampleIntervalLow);
                        double sampleDuration = (double)(timer - lastSampleTimer);
                        if (sampleDuration * 1000.0 >= (threadAdjustmentInterval / 2))
                        {
                            newMax = hc.update(currentThreadCount, sampleDuration, numCompletions, threadAdjustmentInterval);
                            System.out.println("Mode = " +mode+
                                    ", Num Completions = "+numCompletions+
                                    " ("+totalCompletions+"), New Max = "+newMax+
                                    " (Old = "+currentThreadCount+
                                    "), threadAdjustmentInterval = "+threadAdjustmentInterval);

                            if (newMax > currentThreadCount)
                            {
                                // Never go beyound what we actually need (plus 1)
                                int newThreadCount = Math.min(newMax, currentWorkLoad + 1); // + 1
                                if (newThreadCount != 0 && newThreadCount > currentThreadCount)
                                {
                                    // We only ever increase by 1 at a time!
                                    System.out.println("*** INCREASING thread count, from "+currentThreadCount+" -> "+(currentThreadCount+1)+" (CurrentWorkLoad = "+currentWorkLoad+", Hill-Climbing New Max = "+newMax+")***");
                                    currentThreadCount += 1;
                                }
                                else
                                {
                                    System.out.println("*** SHOULD HAVE INCREASED thread count, but didn't, newMax = "+newMax+", currentThreadCount = "+currentThreadCount+", currentWorkLoad = "+currentWorkLoad);
                                }
                            }
                            else if (newMax < (currentThreadCount - 1) && newMax != 0)
                            {
                                System.out.println("*** DECREASING thread count, from "+currentThreadCount+" -> "+(currentThreadCount - 1)+" (CurrentWorkLoad = "+currentWorkLoad+", Hill-Climbing New Max = "+newMax+")***");
                                currentThreadCount -= 1;
                            }

                            priorCompletionCount = totalCompletions;
                            lastSampleTimer = timer;
                        }
                        else
                        {
                            System.out.println("Sample Duration is too small, current = "+sampleDuration+", needed = "+((threadAdjustmentInterval / 2) / 1000.0)+" (threadAdjustmentInterval = "+threadAdjustmentInterval+")");
                        }
                    }
                    else
                    {
                        if (reportedMsgInWorkload == false)
                        {
                            System.out.println("Enough threads to carry out current workload, currentThreadCount = "+currentThreadCount+", currentWorkLoad= "+currentWorkLoad);
                        }

                        reportedMsgInWorkload = true;
                    }
                }

                fw.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

//Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);


    }
}
