/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecchronos.core.repair;

import com.ericsson.bss.cassandra.ecchronos.core.JmxProxy;
import com.ericsson.bss.cassandra.ecchronos.core.exceptions.ScheduledJobException;
import com.ericsson.bss.cassandra.ecchronos.core.repair.state.RepairStateSnapshot;
import com.ericsson.bss.cassandra.ecchronos.core.repair.state.VnodeRepairState;
import com.ericsson.bss.cassandra.ecchronos.core.repair.state.VnodeRepairStates;
import com.ericsson.bss.cassandra.ecchronos.core.repair.state.VnodeRepairStatesImpl;
import com.ericsson.bss.cassandra.ecchronos.core.utils.LongTokenRange;
import com.ericsson.bss.cassandra.ecchronos.core.utils.DriverNode;
import com.ericsson.bss.cassandra.ecchronos.core.utils.TableReference;
import com.google.common.collect.ImmutableSet;
import org.assertj.core.util.Preconditions;
import org.mockito.internal.util.collections.Sets;

import javax.management.Notification;
import javax.management.NotificationListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.ericsson.bss.cassandra.ecchronos.core.MockTableReferenceFactory.tableReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class TestUtils
{
    public static RepairStateSnapshot generateRepairStateSnapshot(long lastRepairedAt, VnodeRepairStates vnodeRepairStates)
    {
        return RepairStateSnapshot.newBuilder()
                .withLastCompletedAt(lastRepairedAt)
                .withVnodeRepairStates(vnodeRepairStates)
                .withReplicaRepairGroups(Collections.emptyList())
                .build();
    }

    public static RepairConfiguration generateRepairConfiguration(long repairIntervalInMs)
    {
        return RepairConfiguration.newBuilder().withRepairInterval(repairIntervalInMs, TimeUnit.MILLISECONDS).build();
    }

    public static RepairConfiguration createRepairConfiguration(long interval, double unwindRatio, int warningTime, int errorTime)
    {
        return RepairConfiguration.newBuilder()
                .withRepairInterval(interval, TimeUnit.MILLISECONDS)
                .withParallelism(RepairOptions.RepairParallelism.PARALLEL)
                .withRepairUnwindRatio(unwindRatio)
                .withRepairWarningTime(warningTime, TimeUnit.MILLISECONDS)
                .withRepairErrorTime(errorTime, TimeUnit.MILLISECONDS)
                .build();
    }

    public static class ScheduledRepairJobBuilder
    {
        private UUID id = UUID.randomUUID();
        private String keyspace;
        private String table;
        private long lastRepairedAt = 0;
        private long repairInterval = 0;
        private ImmutableSet<DriverNode> replicas = ImmutableSet.of();
        private LongTokenRange longTokenRange = new LongTokenRange(1, 2);
        private Collection<VnodeRepairState> vnodeRepairStateSet;
        private RepairConfiguration repairConfiguration;
        private double progress = 0;
        private ScheduledRepairJobView.Status status = ScheduledRepairJobView.Status.ON_TIME;
        private RepairOptions.RepairType repairType = RepairOptions.RepairType.VNODE;

        public ScheduledRepairJobBuilder withId(UUID id)
        {
            this.id = id;
            return this;
        }

        public ScheduledRepairJobBuilder withKeyspace(String keyspace)
        {
            this.keyspace = keyspace;
            return this;
        }

        public ScheduledRepairJobBuilder withTable(String table)
        {
            this.table = table;
            return this;
        }

        public ScheduledRepairJobBuilder withLastRepairedAt(long lastRepairedAt)
        {
            this.lastRepairedAt = lastRepairedAt;
            return this;
        }

        public ScheduledRepairJobBuilder withRepairInterval(long repairInterval)
        {
            this.repairInterval = repairInterval;
            return this;
        }

        public ScheduledRepairJobBuilder withVnodeRepairStateSet(Collection<VnodeRepairState> vnodeRepairStateSet)
        {
            this.vnodeRepairStateSet = vnodeRepairStateSet;
            return this;
        }

        public ScheduledRepairJobBuilder withStatus(ScheduledRepairJobView.Status status)
        {
            this.status = status;
            return this;
        }

        public ScheduledRepairJobBuilder withProgress(double progress)
        {
            this.progress = progress;
            return this;
        }

        public ScheduledRepairJobBuilder withRepairConfiguration(RepairConfiguration repairConfiguration)
        {
            this.repairConfiguration = repairConfiguration;
            return this;
        }

        public ScheduledRepairJobBuilder withRepairType(RepairOptions.RepairType repairType)
        {
            this.repairType = repairType;
            return this;
        }


        public ScheduledRepairJobView build()
        {
            Preconditions.checkNotNull(keyspace, "Keyspace cannot be null");
            Preconditions.checkNotNull(table, "Table cannot be null");
            Preconditions.checkArgument(lastRepairedAt > 0, "Last repaired not set");
            Preconditions.checkArgument(repairInterval > 0, "Repair interval not set");
            VnodeRepairStates vnodeRepairStates;
            if ( vnodeRepairStateSet != null)
            {
                vnodeRepairStates = VnodeRepairStatesImpl.newBuilder(vnodeRepairStateSet).build();
            }
            else
            {
                VnodeRepairState vnodeRepairState = createVnodeRepairState(longTokenRange, replicas, lastRepairedAt);
                vnodeRepairStates = VnodeRepairStatesImpl.newBuilder(Sets.newSet(vnodeRepairState)).build();
            }

            if (repairConfiguration == null)
            {
                this.repairConfiguration = generateRepairConfiguration(repairInterval);
            }
            return new ScheduledRepairJobView(id, tableReference(keyspace, table), repairConfiguration,
                    generateRepairStateSnapshot(lastRepairedAt, vnodeRepairStates), status,progress, lastRepairedAt + repairInterval, repairType);
        }
    }

    public static class OnDemandRepairJobBuilder
    {
        private UUID id = UUID.randomUUID();
        private UUID hostId = UUID.randomUUID();
        private String keyspace;
        private String table;
        private long completedAt = 0;

        private double progress = 0;
        private OnDemandRepairJobView.Status status = OnDemandRepairJobView.Status.IN_QUEUE;
		private RepairConfiguration repairConfiguration = RepairConfiguration.DEFAULT;
        private RepairOptions.RepairType repairType = RepairOptions.RepairType.VNODE;

        public OnDemandRepairJobBuilder withId(UUID id)
        {
            this.id = id;
            return this;
        }

        public OnDemandRepairJobBuilder withHostId(UUID hostId)
        {
            this.hostId = hostId;
            return this;
        }

        public OnDemandRepairJobBuilder withKeyspace(String keyspace)
        {
            this.keyspace = keyspace;
            return this;
        }

        public OnDemandRepairJobBuilder withTable(String table)
        {
            this.table = table;
            return this;
        }

        public OnDemandRepairJobBuilder withCompletedAt(long completedAt)
        {
            this.completedAt = completedAt;
            return this;
        }

        public OnDemandRepairJobBuilder withRepairConfiguration(RepairConfiguration repairConfiguration)
        {
            this.repairConfiguration = repairConfiguration;
            return this;
        }

        public OnDemandRepairJobBuilder withRepairType(RepairOptions.RepairType repairType)
        {
            this.repairType = repairType;
            return this;
        }

        public OnDemandRepairJobBuilder withStatus(OnDemandRepairJobView.Status status)
        {
            this.status = status;
            return this;
        }

        public OnDemandRepairJobBuilder withProgress(double progress)
        {
            this.progress = progress;
            return this;
        }

        public OnDemandRepairJobView build()
        {
            Preconditions.checkNotNull(keyspace, "Keyspace cannot be null");
            Preconditions.checkNotNull(table, "Table cannot be null");
            Preconditions.checkArgument(completedAt > 0 || completedAt == -1, "Last repaired not set");

            return new OnDemandRepairJobView(id, hostId, tableReference(keyspace, table), status, progress, completedAt,
                    repairType);
        }
    }

    public static VnodeRepairState createVnodeRepairState(long startToken, long endToken, ImmutableSet<DriverNode> replicas,
            long lastRepairedAt)
    {
        return createVnodeRepairState(new LongTokenRange(startToken, endToken), replicas, lastRepairedAt);
    }

    public static VnodeRepairState createVnodeRepairState(LongTokenRange longTokenRange, ImmutableSet<DriverNode> replicas,
            long lastRepairedAt)
    {
        return new VnodeRepairState(longTokenRange, replicas, lastRepairedAt);
    }

    public static class MockedJmxProxy implements JmxProxy
    {
        public final String myKeyspace;
        public final String myTable;

        public volatile NotificationListener myListener;

        public volatile Map<String, String> myOptions;

        public MockedJmxProxy(String keyspace, String table)
        {
            myKeyspace = keyspace;
            myTable = table;
        }

        @Override
        public void close() throws IOException
        {
            // Intentionally left empty
        }

        @Override
        public void addStorageServiceListener(NotificationListener listener)
        {
            myListener = listener;
        }

        @Override
        public int repairAsync(String keyspace, Map<String, String> options)
        {
            myOptions = options;
            return 1;
        }

        @Override
        public void forceTerminateAllRepairSessions()
        {
            // NOOP
        }

        @Override
        public void removeStorageServiceListener(NotificationListener listener)
        {
            myListener = null;
        }

        @Override
        public long liveDiskSpaceUsed(TableReference tableReference)
        {
            return 0;
        }

        @Override
        public long getMaxRepairedAt(TableReference tableReference)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getPercentRepaired(TableReference tableReference)
        {
            throw new UnsupportedOperationException();
        }

        public void notify(Notification notification)
        {
            myListener.handleNotification(notification, null);
        }

        @Override
        public List<String> getLiveNodes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getUnreachableNodes()
        {
            return Collections.emptyList();
        }

    }

    public static CountDownLatch startRepair(final RepairTask repairTask, final boolean assertFailed, final MockedJmxProxy proxy)
    {
        final CountDownLatch cdl = new CountDownLatch(1);

        new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    repairTask.execute();
                    assertThat(assertFailed).isFalse();
                }
                catch (ScheduledJobException e)
                {
                    // Intentionally left empty
                }
                finally
                {
                    cdl.countDown();
                }
            }
        }.start();

        await().pollInterval(10, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS).until(() -> proxy.myListener != null);

        return cdl;
    }

    public static String getFailedRepairMessage(LongTokenRange... ranges)
    {
        Collection<LongTokenRange> rangeCollection = Arrays.asList(ranges);
        return String.format("Repair session RepairSession for range %s failed with error ...", rangeCollection);
    }

    public static String getRepairMessage(LongTokenRange... ranges)
    {
        Collection<LongTokenRange> rangeCollection = Arrays.asList(ranges);
        return String.format("Repair session RepairSession for range %s finished", rangeCollection);
    }

    public static Map<String, Integer> getNotificationData(int type, int progressCount, int total)
    {
        Map<String, Integer> data = new HashMap<>();
        data.put("type", type);
        data.put("progressCount", progressCount);
        data.put("total", total);
        return data;
    }
}
