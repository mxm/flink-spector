/*
 * Copyright 2015 Otto (GmbH & Co KG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flinkspector.datastream;

import org.flinkspector.core.input.InputBuilder;
import org.flinkspector.core.set.ExpectedOutput;
import org.flinkspector.core.set.MatcherBuilder;
import org.flinkspector.core.table.OutputMatcherFactory;
import org.flinkspector.datastream.input.EventTimeInput;
import org.flinkspector.datastream.input.EventTimeInputBuilder;
import org.flinkspector.datastream.input.SourceBuilder;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.flinkspector.core.input.Input;
import org.flinkspector.core.runtime.OutputVerifier;
import org.flinkspector.core.table.HamcrestVerifier;
import org.flinkspector.core.trigger.VerifyFinishedTrigger;
import org.flinkspector.datastream.functions.TestSink;
import org.flinkspector.datastream.input.EventTimeSourceBuilder;
import org.flinkspector.datastream.input.time.After;
import org.flinkspector.datastream.input.time.Before;
import org.hamcrest.Matcher;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Offers a base for testing Flink streaming applications
 * To use, extend your JUnitTest with this class.
 */
public class StreamTestBase {

	/**
	 * Test Environment
	 */
	private DataStreamTestEnvironment env;

	/**
	 * Creates a new {@link DataStreamTestEnvironment}
	 */
	@org.junit.Before
	public void initialize() throws Exception {
		env = DataStreamTestEnvironment.createTestEnvironment(2);
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
	}

	/**
	 * Creates a DataStreamSource from an EventTimeInput object.
	 * The DataStreamSource will emit the records with the specified EventTime.
	 *
	 * @param input to emit
	 * @param <OUT> type of the emitted records
	 * @return a DataStreamSource generating the input
	 */
	public <OUT> DataStreamSource<OUT> createTestStream(EventTimeInput<OUT> input) {
		return env.fromInput(input);
	}

	/**
	 * Provides an {@link SourceBuilder} to create an {@link DataStreamSource}
	 *
	 * @param record the first record to emit
	 * @param <OUT>  type of the stream.
	 * @return {@link SourceBuilder} to work with.
	 */
	public <OUT> SourceBuilder<OUT> createTestStreamWith(OUT record) {
		return SourceBuilder.createBuilder(record, env);
	}

	/**
	 * Provides an {@link EventTimeSourceBuilder} to create an {@link DataStreamSource}
	 *
	 * @param record the first record to emit
	 * @param <OUT>  type of the stream.
	 * @return {@link EventTimeSourceBuilder} to work with.
	 */
	public <OUT> EventTimeSourceBuilder<OUT> createTimedTestStreamWith(OUT record) {
		return EventTimeSourceBuilder.createBuilder(record, env);
	}


	/**
	 * Creates a DataStreamSource from an EventTimeInput object.
	 * The DataStreamSource will emit the records with the specified EventTime.
	 *
	 * @param input to emit
	 * @param <OUT> type of the emitted records
	 * @return a DataStreamSource generating the input
	 */
	public <OUT> DataStreamSource<OUT> createTestStream(Collection<OUT> input) {
		return env.fromInput(input);
	}

	/**
	 * Creates a DataStreamSource from an Input object.
	 *
	 * @param input to emit
	 * @param <OUT> type of the emitted records
	 * @return a DataStream generating the input
	 */
	public <OUT> DataStreamSource<OUT> createTestStream(Input<OUT> input) {
		return env.fromInput(input);
	}

	/**
	 * Creates a TestSink using {@link org.hamcrest.Matcher} to verify the output.
	 *
	 * @param matcher of type Iterable<IN>
	 * @param <IN>
	 * @return the created sink.
	 */
	public <IN> TestSink<IN> createTestSink(org.hamcrest.Matcher<Iterable<IN>> matcher) {
		OutputVerifier<IN> verifier = new HamcrestVerifier<IN>(matcher);
		return env.createTestSink(verifier);
	}

	/**
	 * Creates a TestSink using {@link org.hamcrest.Matcher} to verify the output.
	 *
	 * @param matcher of type Iterable<IN>
	 * @param <IN>
	 * @return the created sink.
	 */
	public <IN> TestSink<IN> createTestSink(org.hamcrest.Matcher<Iterable<IN>> matcher,
	                                        VerifyFinishedTrigger trigger) {
		OutputVerifier<IN> verifier = new HamcrestVerifier<>(matcher);
		return env.createTestSink(verifier, trigger);
	}

	/**
	 * Inspect a {@link DataStream} using a {@link OutputMatcherFactory}.
	 *
	 * @param stream  {@link DataStream} to test.
	 * @param matcher {@link OutputMatcherFactory} to use.
	 * @param <T>     type of the stream.
	 */
	public <T> void assertStream(DataStream<T> stream, Matcher<Iterable<T>> matcher) {
		stream.addSink(createTestSink(matcher));
	}

	/**
	 * Inspect a {@link DataStream} using a {@link OutputMatcherFactory}.
	 *
	 * @param stream  {@link DataStream} to test.
	 * @param matcher {@link OutputMatcherFactory} to use.
	 * @param trigger {@link VerifyFinishedTrigger}
	 *                to finish the assertion early.
	 * @param <T>     type of the stream.
	 */
	public <T> void assertStream(DataStream<T> stream,
	                             Matcher<Iterable<T>> matcher,
	                             VerifyFinishedTrigger trigger) {
		stream.addSink(createTestSink(matcher, trigger));
	}

	public void setParallelism(int n) {
		env.setParallelism(n);
	}

	/**
	 * Executes the test and verifies the output received by the sinks.
	 */
	@org.junit.After
	public void executeTest() throws Throwable {
		try {
			env.executeTest();
		} catch (AssertionError assertionError) {
			if (env.hasBeenStopped()) {
				//the execution has been forcefully stopped inform the user!
				throw new AssertionError("Test terminated due timeout!" +
						assertionError.getMessage());
			}
			throw assertionError;
		}
	}

	//================================================================================
	// Syntactic sugar stuff
	//================================================================================

	/**
	 * Creates an {@link After} object.
	 *
	 * @param span length of span
	 * @param unit of time
	 * @return {@link After}
	 */
	public static After after(long span, TimeUnit unit) {
		return After.period(span, unit);
	}

	/**
	 * Creates an {@link Before} object.
	 *
	 * @param span length of span
	 * @param unit of time
	 * @return {@link Before}
	 */
	public static Before before(long span, TimeUnit unit) {
		return Before.period(span, unit);
	}

	public static <T> EventTimeInputBuilder<T> startWith(T record) {
		return EventTimeInputBuilder.create(record);
	}

	public static <T> InputBuilder<T> emit(T elem) {
		return InputBuilder.create(elem);
	}

	public static <T> ExpectedOutput<T> expectOutput(T record) {
		return ExpectedOutput.create(record);
	}

	public static int times(int n) {
		return n;
	}

	public static final MatcherBuilder.Order strict = MatcherBuilder.Order.STRICT;
	public static final MatcherBuilder.Order notStrict = MatcherBuilder.Order.NONSTRICT;
	public static final TimeUnit seconds = TimeUnit.SECONDS;
	public static final TimeUnit minutes = TimeUnit.MINUTES;
	public static final TimeUnit hours = TimeUnit.HOURS;
	public static final String ignore = null;

}
