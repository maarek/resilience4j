package io.github.resilience4j.timeout.internal;

import io.vavr.control.Option;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

// Ported from https://github.com/dotnet/coreclr/blob/32f0f9721afb584b4a14d69135bea7ddc129f755/src/vm/hillclimbing.cpp.
// MIT Licensed.
public class HillClimbExecutor {

    public enum HillClimbingStateTransition {
        Warmup,
        Initializing,
        RandomMove,
        ClimbingMove,
        ChangePoint,
        Stabilizing,
        Starvation, //used by ThreadpoolMgr
        ThreadTimedOut, //used by ThreadpoolMgr
        Undefined,
    }

    public static class HillClimbingOptions {

        public int samplesToMeasure;// { get; private set; }

        public HillClimbingOptions(Supplier<Integer> minThreads, Supplier<Integer> maxThreads) {
            reset();
            this.minThreads = minThreads;
            this.maxThreads = maxThreads;
        }

        public void reset() {
            // Defaults from : https://raw.githubusercontent.com/dotnet/coreclr/549c9960a8edcbe3930639e316616d35b22bca25/src/inc/clrconfigvalues.h
            wavePeriod = 4; //CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_WavePeriod);
            maxThreadWaveMagnitude = 20; //CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_MaxWaveMagnitude);
            threadMagnitudeMultiplier = 100 / 100.0; //(double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_WaveMagnitudeMultiplier) / 100.0;
            samplesToMeasure = wavePeriod * 8; //(int)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_WaveHistorySize);
            targetThroughputRatio = 15 / 100.0; //(double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_Bias) / 100.0;
            targetSignalToNoiseRatio = 300 / 100.0; // (double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_TargetSignalToNoiseRatio) / 100.0;
            maxChangePerSecond = 4; // (double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_MaxChangePerSecond);
            maxChangePerSample = 20; //(double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_MaxChangePerSample);
            sampleIntervalLow = 10; // CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_SampleIntervalLow);
            sampleIntervalHigh = 200; // CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_SampleIntervalHigh);
            throughputErrorSmoothingFactor = 1 / 100.0; //(double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_ErrorSmoothingFactor) / 100.0;
            gainExponent = 200 / 100.0; // (double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_GainExponent) / 100.0;
            maxSampleError = 15 / 100.0; // (double)CLRConfig::GetConfigValue(CLRConfig::INTERNAL_HillClimbing_MaxSampleErrorPercent) / 100.0;
        }

        /// <summary>
        /// Returns the number of currently required minimum threads.
        /// </summary>
        private Supplier<Integer> minThreads; //{ get; set; }
        public Supplier<Integer> getMinThreads() {
            return minThreads;
        }
        public void setMinThreads(Supplier<Integer> minThreadSupplier) {
            this.minThreads = minThreadSupplier;
        }

        /// <summary>
        /// Returns the number of currently allowed maximum threads.
        /// </summary>
        private Supplier<Integer> maxThreads; //{ get; set; }
        public Supplier<Integer> getMaxThreads() {
            return maxThreads;
        }
        public void setMaxThreads(Supplier<Integer> maxThreadSupplier) {
            this.maxThreads = maxThreadSupplier;
        }

        /// <summary>
        /// Returns the current CPU utilization in percent (e.g. 95 for 95%).
        /// </summary>
        private Supplier<Integer> cpuUtilization() {
            return () -> {
                try {
                    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                    ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
                    AttributeList list = mbs.getAttributes(name, new String[]{"ProcessCpuLoad"});

                    if (list.isEmpty()) return -1;

                    Attribute att = (Attribute) list.get(0);
                    Double value = (Double) att.getValue();

                    // usually takes a couple of seconds before we get real values
                    if (value == -1.0) return -1;
                    // returns a percentage value with 1 decimal point precision
                    return (int)((int)(value * 1000) / 10.0);
                }
                catch (Exception e) {

                }
                return -1;
            };
        }

        private int wavePeriod; //{ get; set; }

        private double targetThroughputRatio; //{ get; set; }

        private double targetSignalToNoiseRatio; //{ get; set; }

        private double maxChangePerSecond; //{ get; set; }

        private double maxChangePerSample; //{ get; set; }

        private int maxThreadWaveMagnitude; //{ get; set; }

        private int sampleIntervalLow; //{ get; set; }

        private double threadMagnitudeMultiplier; //{ get; set; }

        private int sampleIntervalHigh; //{ get; set; }

        private double throughputErrorSmoothingFactor; //{ get; set; }

        private double gainExponent; //{ get; set; }

        private double maxSampleError; //{ get; set; }

        private int tpMaxWorkerThreads() {
            return 1 << 16;
        }

        private int tpMinWorkerThreads() {
            return Runtime.getRuntime().availableProcessors();
        }

        public int minLimitThreads() {
            return Option.of(minThreads.get()).getOrElse(this::tpMinWorkerThreads);
        }

        public int maxLimitThreads() {
            return Option.of(maxThreads.get()).getOrElse(this::tpMaxWorkerThreads);
        }

        public int currentCpuUtilization() {
            return Option.of(cpuUtilization().get()).getOrElse(0);
        }

        private void fireEtwThreadPoolWorkerThreadAdjustmentSample(double throughput) {}

        private void fireEtwThreadPoolWorkerThreadAdjustmentAdjustment(int newThreadCount,
                                                                        double throughput, HillClimbingStateTransition transition) { }

        private void fireEtwThreadPoolWorkerThreadAdjustmentStats(double sampleDuration, double throughput,
                                                                   double threadWaveComponent, double throughputWaveComponent, double throughputErrorEstimate,
                                                                   double averageThroughputNoise, double ratio, double confidence, double currentControlSetting,
                                                                   short newThreadWaveMagnitude) { }
    }

    public static class HillClimbing
    {
        private final HillClimbingOptions options;

        private double      currentControlSetting;
        private long        totalSamples;
        private int         lastThreadCount;
        private double      elapsedSinceLastChange; //elapsed seconds since last thread count change
        private double      completionsSinceLastChange; //number of completions since last thread count change

        private double      averageThroughputNoise;

        private final double[]   samples;   //Circular buffer of the last m_samplesToMeasure samples
        private final double[]   threadCounts; //Thread counts effective at each of m_samples

        private int              currentSampleInterval;
        private final ThreadLocalRandom randomIntervalGenerator;

        private int         accumulatedCompletionCount;
        private double      accumulatedSampleDuration;

        // From win32threadpool.h
        private static final int  cpuUtilizationHigh = 95;

        public HillClimbing(HillClimbingOptions options) {
            if (options == null)
                throw new IllegalArgumentException();

            this.options = options;
            this.samples = new double[options.samplesToMeasure];
            this.threadCounts = new double[options.samplesToMeasure];
            this.randomIntervalGenerator = ThreadLocalRandom.current(); //((AppDomain.CurrentDomain.Id << 16) ^ Process.GetCurrentProcess().Id);
            this.currentSampleInterval = randomIntervalGenerator.nextInt(options.sampleIntervalLow, options.sampleIntervalHigh + 1);
        }

        public int update(int currentThreadCount, double sampleDuration, int numCompletions, int pNewSampleInterval) {
            //
            // If someone changed the thread count without telling us, update our records accordingly.
            //
            if (currentThreadCount != lastThreadCount)
                forceChange(currentThreadCount, HillClimbingStateTransition.Initializing);

            //
            // Update the cumulative stats for this thread count
            //
            elapsedSinceLastChange += sampleDuration;
            completionsSinceLastChange += numCompletions;

            //
            // Add in any data we've already collected about this sample
            //
            sampleDuration += accumulatedSampleDuration;
            numCompletions += accumulatedCompletionCount;

            //
            // We need to make sure we're collecting reasonably accurate data.  Since we're just counting the end
            // of each work item, we are goinng to be missing some data about what really happened during the
            // sample interval.  The count produced by each thread includes an initial work item that may have
            // started well before the start of the interval, and each thread may have been running some new
            // work item for some time before the end of the interval, which did not yet get counted.  So
            // our count is going to be off by +/- threadCount workitems.
            //
            // The exception is that the thread that reported to us last time definitely wasn't running any work
            // at that time, and the thread that's reporting now definitely isn't running a work item now.  So
            // we really only need to consider threadCount-1 threads.
            //
            // Thus the percent error in our count is +/- (threadCount-1)/numCompletions.
            //
            // We cannot rely on the frequency-domain analysis we'll be doing later to filter out this error, because
            // of the way it accumulates over time.  If this sample is off by, say, 33% in the negative direction,
            // then the next one likely will be too.  The one after that will include the sum of the completions
            // we missed in the previous samples, and so will be 33% positive.  So every three samples we'll have
            // two "low" samples and one "high" sample.  This will appear as periodic variation right in the frequency
            // range we're targeting, which will not be filtered by the frequency-domain translation.
            //
            if (totalSamples > 0 && ((currentThreadCount - 1.0) / numCompletions) >= options.maxSampleError)
            {
                // not accurate enough yet.  Let's accumulate the data so far, and tell the ThreadPool
                // to collect a little more.
                accumulatedSampleDuration = sampleDuration;
                accumulatedCompletionCount = numCompletions;
                pNewSampleInterval = 10;
                return currentThreadCount;
            }

            //
            // We've got enouugh data for our sample; reset our accumulators for next time.
            //
            accumulatedSampleDuration = 0;
            accumulatedCompletionCount = 0;

            //
            // Add the current thread count and throughput sample to our history
            //
            double throughput = (double)numCompletions / sampleDuration;
            options.fireEtwThreadPoolWorkerThreadAdjustmentSample(throughput);

            int sampleIndex = (int)(totalSamples % options.samplesToMeasure);
            samples[sampleIndex] = throughput;
            threadCounts[sampleIndex] = currentThreadCount;
            totalSamples++;

            //
            // Set up defaults for our metrics
            //
            Complex threadWaveComponent = new Complex();
            Complex throughputWaveComponent = new Complex();
            double throughputErrorEstimate = 0;
            Complex ratio = new Complex();
            double confidence = 0;

            HillClimbingStateTransition transition = HillClimbingStateTransition.Warmup;

            //
            // How many samples will we use?  It must be at least the three wave periods we're looking for, and it must also be a whole
            // multiple of the primary wave's period; otherwise the frequency we're looking for will fall between two  frequency bands
            // in the Fourier analysis, and we won't be able to measure it accurately.
            //
            int sampleCount = ((int)Math.min(totalSamples - 1, options.samplesToMeasure) / options.wavePeriod) * options.wavePeriod;

            if (sampleCount > options.wavePeriod)
            {
                //
                // Average the throughput and thread count samples, so we can scale the wave magnitudes later.
                //
                double sampleSum = 0;
                double threadSum = 0;
                for (int i = 0; i < sampleCount; i++)
                {
                    sampleSum += samples[(int) ((totalSamples - sampleCount + i) % options.samplesToMeasure)];
                    threadSum += threadCounts[(int) ((totalSamples - sampleCount + i) % options.samplesToMeasure)];
                }
                double averageThroughput = sampleSum / sampleCount;
                double averageThreadCount = threadSum / sampleCount;

                if (averageThroughput > 0 && averageThreadCount > 0)
                {
                    //
                    // Calculate the periods of the adjacent frequency bands we'll be using to measure noise levels.
                    // We want the two adjacent Fourier frequency bands.
                    //
                    double adjacentPeriod1 = sampleCount / (((double)sampleCount / (double)options.wavePeriod) + 1);
                    double adjacentPeriod2 = sampleCount / (((double)sampleCount / (double)options.wavePeriod) - 1);

                    //
                    // Get the the three different frequency components of the throughput (scaled by average
                    // throughput).  Our "error" estimate (the amount of noise that might be present in the
                    // frequency band we're really interested in) is the average of the adjacent bands.
                    //
                    throughputWaveComponent = getWaveComponent(samples, sampleCount, options.wavePeriod).divides(averageThroughput);
                    throughputErrorEstimate = getWaveComponent(samples, sampleCount, adjacentPeriod1).divides(averageThroughput).abs();
                    if (adjacentPeriod2 <= sampleCount)
                        throughputErrorEstimate = Math.max(throughputErrorEstimate, getWaveComponent(samples, sampleCount, adjacentPeriod2).divides(averageThroughput).abs());

                    //
                    // Do the same for the thread counts, so we have something to compare to.  We don't measure thread count
                    // noise, because there is none; these are exact measurements.
                    //
                    threadWaveComponent = getWaveComponent(threadCounts, sampleCount, options.wavePeriod).divides(averageThreadCount);

                    //
                    // Update our moving average of the throughput noise.  We'll use this later as feedback to
                    // determine the new size of the thread wave.
                    //
                    if (averageThroughputNoise == 0)
                        averageThroughputNoise = throughputErrorEstimate;
                    else
                        averageThroughputNoise = (options.throughputErrorSmoothingFactor * throughputErrorEstimate) + ((1.0 - options.throughputErrorSmoothingFactor) * averageThroughputNoise);

                    if (threadWaveComponent.abs() > 0)
                    {
                        //
                        // Adjust the throughput wave so it's centered around the target wave, and then calculate the adjusted throughput/thread ratio.
                        //
                        ratio = throughputWaveComponent.minus(threadWaveComponent.times(options.targetThroughputRatio)).divides(threadWaveComponent);
                        transition = HillClimbingStateTransition.ClimbingMove;
                    }
                    else
                    {
                        ratio = new Complex();
                        transition = HillClimbingStateTransition.Stabilizing;
                    }

                    //
                    // Calculate how confident we are in the ratio.  More noise == less confident.  This has
                    // the effect of slowing down movements that might be affected by random noise.
                    //
                    double noiseForConfidence = Math.max(averageThroughputNoise, throughputErrorEstimate);
                    if (noiseForConfidence > 0)
                        confidence = (threadWaveComponent.abs() / noiseForConfidence) / options.targetSignalToNoiseRatio;
                    else
                        confidence = 1.0; //there is no noise!

                }
            }

            //
            // We use just the real part of the complex ratio we just calculated.  If the throughput signal
            // is exactly in phase with the thread signal, this will be the same as taking the magnitude of
            // the complex move and moving that far up.  If they're 180 degrees out of phase, we'll move
            // backward (because this indicates that our changes are having the opposite of the intended effect).
            // If they're 90 degrees out of phase, we won't move at all, because we can't tell wether we're
            // having a negative or positive effect on throughput.
            //
            double move = Math.min(1.0, Math.max(-1.0, ratio.re()));

            //
            // Apply our confidence multiplier.
            //
            move *= Math.min(1.0, Math.max(0.0, confidence));

            //
            // Now apply non-linear gain, such that values around zero are attenuated, while higher values
            // are enhanced.  This allows us to move quickly if we're far away from the target, but more slowly
            // if we're getting close, giving us rapid ramp-up without wild oscillations around the target.
            //
            double gain = options.maxChangePerSecond * sampleDuration;
            move = Math.pow(Math.abs(move), options.gainExponent) * (move >= 0.0 ? 1 : -1) * gain;
            move = Math.min(move, options.maxChangePerSample);

            //
            // If the result was positive, and CPU is > 95%, refuse the move.
            //
            if (move > 0.0 && options.currentCpuUtilization() > cpuUtilizationHigh)
                move = 0.0;

            //
            // Apply the move to our control setting
            //
            currentControlSetting += move;

            //
            // Calculate the new thread wave magnitude, which is based on the moving average we've been keeping of
            // the throughput error.  This average starts at zero, so we'll start with a nice safe little wave at first.
            //
            int newThreadWaveMagnitude = (int)(0.5 + (currentControlSetting * averageThroughputNoise * options.targetSignalToNoiseRatio * options.threadMagnitudeMultiplier * 2.0));
            newThreadWaveMagnitude = Math.min(newThreadWaveMagnitude, options.maxThreadWaveMagnitude);
            newThreadWaveMagnitude = Math.max(newThreadWaveMagnitude, 1);

            //
            // Make sure our control setting is within the ThreadPool's limits
            //
            currentControlSetting = Math.min(options.maxLimitThreads() - newThreadWaveMagnitude, currentControlSetting);
            currentControlSetting = Math.max(options.minLimitThreads(), currentControlSetting);

            //
            // Calculate the new thread count (control setting + square wave)
            //
            int newThreadCount = (int)(currentControlSetting + newThreadWaveMagnitude * ((totalSamples / (options.wavePeriod / 2)) % 2));

            //
            // Make sure the new thread count doesn't exceed the ThreadPool's limits
            //
            newThreadCount = Math.min(options.maxLimitThreads(), newThreadCount);
            newThreadCount = Math.max(options.minLimitThreads(), newThreadCount);

            //
            // Record these numbers for posterity
            //
            options.fireEtwThreadPoolWorkerThreadAdjustmentStats(
                    sampleDuration,
                    throughput,
                    threadWaveComponent.re(),
                    throughputWaveComponent.re(),
                    throughputErrorEstimate,
                    averageThroughputNoise,
                    ratio.re(),
                    confidence,
                    currentControlSetting,
                    (short)newThreadWaveMagnitude);

            //
            // If all of this caused an actual change in thread count, log that as well.
            //
            if (newThreadCount != currentThreadCount)
                changeThreadCount(newThreadCount, transition);

            //
            // Return the new thread count and sample interval.  This is randomized to prevent correlations with other periodic
            // changes in throughput.  Among other things, this prevents us from getting confused by Hill Climbing instances
            // running in other processes.
            //
            // If we're at minThreads, and we seem to be hurting performance by going higher, we can't go any lower to fix this.  So
            // we'll simply stay at minThreads much longer, and only occasionally try a higher value.
            //
            if (ratio.re() < 0.0 && newThreadCount == options.minLimitThreads())
                pNewSampleInterval = (int)(0.5 + currentSampleInterval * (10.0 * Math.max(-ratio.re(), 1.0)));
            else
                pNewSampleInterval = (int)currentSampleInterval;

            return newThreadCount;
        }

        public void forceChange(int newThreadCount, HillClimbingStateTransition transition) {
            if (newThreadCount != lastThreadCount)
            {
                currentControlSetting += (newThreadCount - lastThreadCount);
                changeThreadCount(newThreadCount, transition);
            }
        }

        private void changeThreadCount(int newThreadCount, HillClimbingStateTransition transition)
        {
            lastThreadCount = newThreadCount;
            currentSampleInterval = randomIntervalGenerator.nextInt(options.sampleIntervalLow, options.sampleIntervalHigh + 1);
            double throughput = (elapsedSinceLastChange > 0) ? (completionsSinceLastChange / elapsedSinceLastChange) : 0;
            options.fireEtwThreadPoolWorkerThreadAdjustmentAdjustment(newThreadCount, throughput, transition);
            elapsedSinceLastChange = 0;
            completionsSinceLastChange = 0;
        }

        private Complex getWaveComponent(double[] samples, int sampleCount, double period)
        {
            assert(sampleCount >= period) : "sampleCount >= period"; //can't measure a wave that doesn't fit
            assert(period >= 2) : "period >= 2"; //can't measure above the Nyquist frequency

            final double pi = 3.141592653589793;

            //
            // Calculate the sinusoid with the given period.
            // We're using the Goertzel algorithm for this.  See http://en.wikipedia.org/wiki/Goertzel_algorithm.
            //
            double w = 2.0 * pi / period;
            double cosine = Math.cos(w);
            double sine = Math.sin(w);
            double coeff = 2.0 * cosine;
            double q0 = 0, q1 = 0, q2 = 0;

            for (int i = 0; i < sampleCount; i++)
            {
                double sample = samples[(int) ((totalSamples - sampleCount + i) % options.samplesToMeasure)];

                q0 = coeff * q1 - q2 + sample;
                q2 = q1;
                q1 = q0;
            }

            return new Complex(q1 - q2 * cosine, q2 * sine).divides((double)sampleCount);
        }
    }

}





