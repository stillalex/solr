/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.apache.solr.bench.BaseBenchState;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.IndexSchemaFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(Threads.MAX) // number of cores
@Warmup(time = 5, iterations = 1)
@Measurement(time = 5, iterations = 3)
@Fork(value = 1)
@Timeout(time = 60)
public class SolrCoresBenchTest {

  @State(Scope.Benchmark)
  public static class BenchState {

    private int cores = 10;
    private SolrCores solrCores;
    private CoreContainer container;
    private SplittableRandom random;

    public BenchState() {
    }

    @Setup(Level.Trial)
    public void doSetup() throws IOException {
      System.setProperty("pkiHandlerPrivateKeyPath", "");
      System.setProperty("pkiHandlerPublicKeyPath", "");

      random = new SplittableRandom(BaseBenchState.getRandomSeed());

      String workDir = System.getProperty("workBaseDir", "build/work");
      Path baseDir = Paths.get(workDir, "cores-bench-" + System.currentTimeMillis());
      Files.createDirectories(baseDir);
      String solrXml = MiniSolrCloudCluster.DEFAULT_CLOUD_SOLR_XML;
      NodeConfig nodeConfig = SolrXmlConfig.fromString(baseDir, solrXml);
      container = new CoreContainer(nodeConfig);
      container.load();

      SolrResourceLoader loader = new SolrResourceLoader(baseDir);

      solrCores = new SolrCores(container);
      solrCores.load(loader);

      for (int i = 0; i < cores; i++) {
        String coreName = "core" + i;
        CoreDescriptor cd = new CoreDescriptor(
            coreName,
            container.getCoreRootDirectory().resolve(coreName),
            container);

        SolrConfig solrConfig = new SolrConfig(Paths.get("src/resources/configs/cloud-minimal"), "conf/solrconfig.xml");
        IndexSchema schema = IndexSchemaFactory.buildIndexSchema("configs/cloud-minimal/conf/schema.xml", solrConfig);

        ConfigSet configSet = new ConfigSet("fakeConfigset", solrConfig, forceFetch -> schema, null, true);
        SolrCore core = new SolrCore(container, cd, configSet);
        solrCores.putCore(cd, core);
      }
    }

    @TearDown(Level.Trial)
    public void shutdown()
        throws Exception {
      for (SolrCore core : solrCores.getCores()) {
        while (core.getOpenCount() > 0) {
          core.close();
        }
      }
      solrCores.close();
      container.shutdown();
    }
  }

  @Benchmark
  @Timeout(time = 300)
  public Object getCoreFromAnyList(
      BenchState state) throws Exception {
    int id = state.random.nextInt(state.cores);
    String name = "core" + id;
    return state.solrCores.getCoreFromAnyList(name, true, null);
  }
}
