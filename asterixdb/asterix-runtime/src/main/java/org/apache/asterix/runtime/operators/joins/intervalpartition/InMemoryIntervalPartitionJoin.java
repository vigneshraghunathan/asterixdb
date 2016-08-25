/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.runtime.operators.joins.intervalpartition;

import java.util.logging.Logger;

import org.apache.asterix.runtime.operators.joins.IIntervalMergeJoinChecker;
import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.comm.IFrameWriter;
import org.apache.hyracks.api.comm.VSizeFrame;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import org.apache.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import org.apache.hyracks.dataflow.common.comm.util.FrameUtils;
import org.apache.hyracks.dataflow.std.buffermanager.BufferInfo;
import org.apache.hyracks.dataflow.std.buffermanager.IFrameBufferManager;

public class InMemoryIntervalPartitionJoin {

    private static final Logger LOGGER = Logger.getLogger(InMemoryIntervalPartitionJoin.class.getName());

    private final FrameTupleAccessor accessorBuild;
    private final FrameTupleAppender appender;
    private final IFrameBufferManager fbm;
    private BufferInfo bufferInfo;
    private final IIntervalMergeJoinChecker imjc;

    private long joinComparisonCount = 0;
    private long joinResultCount = 0;

    public InMemoryIntervalPartitionJoin(IHyracksTaskContext ctx, IFrameBufferManager fbm,
            IIntervalMergeJoinChecker imjc, RecordDescriptor buildRd, RecordDescriptor probeRd)
            throws HyracksDataException {
        bufferInfo = new BufferInfo(null, -1, -1);
        this.accessorBuild = new FrameTupleAccessor(buildRd);
        appender = new FrameTupleAppender(new VSizeFrame(ctx));
        this.imjc = imjc;
        this.fbm = fbm;
        LOGGER.fine(
                "InMemoryIntervalPartitionJoin has been created for Thread ID " + Thread.currentThread().getId() + ".");
    }

    public long getComparisonCount() {
        return joinComparisonCount;
    }

    public long getResultCount() {
        return joinResultCount;
    }

    public void join(IFrameTupleAccessor accessorProbe, int probeTupleIndex, IFrameWriter writer)
            throws HyracksDataException {
        if (fbm.getNumFrames() != 0) {
            for (int frameIndex = 0; frameIndex < fbm.getNumFrames(); ++frameIndex) {
                fbm.getFrame(frameIndex, bufferInfo);
                accessorBuild.reset(bufferInfo.getBuffer());
                for (int buildTupleIndex = 0; buildTupleIndex < accessorBuild.getTupleCount(); ++buildTupleIndex) {
                    if (imjc.checkToSaveInResult(accessorBuild, buildTupleIndex, accessorProbe, probeTupleIndex,
                            false)) {
                        appendToResult(accessorBuild, buildTupleIndex, accessorProbe, probeTupleIndex, writer);
                    }
                    joinComparisonCount++;
                }
            }
        }
    }

    public void closeJoin(IFrameWriter writer) throws HyracksDataException {
        appender.write(writer, true);
    }

    private void appendToResult(IFrameTupleAccessor accessorBuild, int buildSidetIx, IFrameTupleAccessor accessorProbe,
            int probeSidetIx, IFrameWriter writer) throws HyracksDataException {
        FrameUtils.appendConcatToWriter(writer, appender, accessorBuild, buildSidetIx, accessorProbe, probeSidetIx);
        joinResultCount++;
    }
}
