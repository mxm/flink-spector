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

package org.flinkspector.dataset;

import org.flinkspector.core.runtime.OutputPublisher;
import org.flinkspector.core.util.SerializeUtil;
import org.apache.flink.api.common.io.RichOutputFormat;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestOutputFormat<IN> extends RichOutputFormat<IN> {

	private static final long serialVersionUID = 1L;


	private OutputPublisher handler;
	private TypeSerializer<IN> serializer;
	private final int port;
	private String jobManagerAddress;

	private int taskNumber;
	private int numTasks;

	private Logger LOG = LoggerFactory.getLogger(RichOutputFormat.class);

	public TestOutputFormat(int port) {
		this.port = port;
	}

	@Override
	public void configure(Configuration configuration) {
		jobManagerAddress = configuration
				.getString("jobmanager.rpc.address", "localhost");
	}

	@Override
	public void open(int taskNumber, int numTasks) throws IOException {
		this.taskNumber = taskNumber;
		this.numTasks = numTasks;
		//open a socket to push data
		handler = new OutputPublisher(jobManagerAddress, port);
	}

	@Override
	public void writeRecord(IN next) throws IOException {
		byte[] msg;
		if (serializer == null) {
			//create serializer
			TypeInformation<IN> typeInfo = TypeExtractor.getForObject(next);
			serializer = typeInfo.createSerializer(getRuntimeContext().getExecutionConfig());
			//push serializer to output receiver
			try {
				handler.sendOpen(taskNumber,
						numTasks,
						SerializeUtil.serialize(serializer));
			} catch (IOException e) {
				LOG.error("Could not serialize TypeSerializer", e);
				return;
			}
		}

		//serialize input and push to output
		byte[] bytes;
		try {
			bytes = SerializeUtil.serialize(next, serializer);
		} catch (IOException e) {
			LOG.error("Could not serialize input", e);
			return;
		}
		handler.sendRecord(bytes);
	}

	@Override
	public void close() throws IOException {
		//signal close to output receiver
		handler.sendClose(taskNumber);
	}
}
