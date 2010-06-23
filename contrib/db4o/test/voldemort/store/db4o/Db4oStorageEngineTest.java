/*
 * Copyright 2008-2009 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.store.db4o;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileDeleteStrategy;

import voldemort.TestUtils;
import voldemort.store.AbstractStorageEngineTest;
import voldemort.store.StorageEngine;
import voldemort.utils.ByteArray;
import voldemort.utils.ClosableIterator;
import voldemort.utils.Pair;
import voldemort.versioning.VectorClock;
import voldemort.versioning.Versioned;

import com.db4o.Db4oEmbedded;
import com.db4o.config.EmbeddedConfiguration;

public class Db4oStorageEngineTest extends AbstractStorageEngineTest {

    private File tempDir;
    private Db4oByteArrayStorageEngine store;
    private EmbeddedConfiguration dbConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // this.envConfig.setTxnNoSync(true);
        // this.envConfig.setAllowCreate(true);
        // this.envConfig.setTransactional(true);
        this.tempDir = TestUtils.createTempDir();
        // databaseConfig.setAllowCreate(true);
        // databaseConfig.setTransactional(true);
        // databaseConfig.setSortedDuplicates(true);

        this.dbConfig = Db4oEmbedded.newConfiguration();
        this.store = new Db4oByteArrayStorageEngine(this.tempDir + "/" + "test", dbConfig);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            store.close();
        } finally {
            FileDeleteStrategy.FORCE.delete(tempDir);
        }
    }

    @Override
    public StorageEngine<ByteArray, byte[]> getStorageEngine() {
        return store;
    }

    public void testPersistence() throws Exception {
        this.store.put(new ByteArray("abc".getBytes()), new Versioned<byte[]>("cdef".getBytes()));
        this.store.close();
        this.store = new Db4oByteArrayStorageEngine(this.tempDir + "/" + "test", dbConfig);
        List<Versioned<byte[]>> vals = store.get(new ByteArray("abc".getBytes()));
        assertEquals(1, vals.size());
        TestUtils.bytesEqual("cdef".getBytes(), vals.get(0).getValue());
    }

    public void testEquals() {
        String name = "someName";
        assertEquals(new Db4oByteArrayStorageEngine(this.tempDir + "/" + name, dbConfig),
                     new Db4oByteArrayStorageEngine(this.tempDir + "/" + name, dbConfig));
    }

    public void testNullConstructorParameters() {
        try {
            new Db4oByteArrayStorageEngine(null, dbConfig);
        } catch(IllegalArgumentException e) {
            return;
        }
        fail("No exception thrown for null path name.");
        try {
            new Db4oByteArrayStorageEngine("name", null);
        } catch(IllegalArgumentException e) {
            return;
        }
        fail("No exception thrown for null configuration.");
    }

    public void testSimultaneousIterationAndModification() throws Exception {
        // start a thread to do modifications
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final Random rand = new Random();
        final AtomicInteger count = new AtomicInteger(0);
        executor.execute(new Runnable() {

            public void run() {
                while(!Thread.interrupted()) {
                    byte[] bytes = Integer.toString(count.getAndIncrement()).getBytes();
                    store.put(new ByteArray(bytes), Versioned.value(bytes));
                    count.incrementAndGet();
                }
            }
        });
        executor.execute(new Runnable() {

            public void run() {
                while(!Thread.interrupted()) {
                    byte[] bytes = Integer.toString(rand.nextInt(count.get())).getBytes();
                    store.delete(new ByteArray(bytes), new VectorClock());
                    count.incrementAndGet();
                }
            }
        });

        // wait a bit
        while(count.get() < 300)
            continue;

        // now simultaneously do iteration
        ClosableIterator<Pair<ByteArray, Versioned<byte[]>>> iter = this.store.entries();
        while(iter.hasNext())
            iter.next();
        iter.close();
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
    }
}
