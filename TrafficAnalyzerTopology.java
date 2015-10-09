/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package storm.starter;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.testing.TestWordSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;
import java.io.*;

import java.util.Map;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Scanner;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

class StdinSpout extends BaseRichSpout {
    public static Logger LOG = LoggerFactory.getLogger(TestWordSpout.class);
    boolean _isDistributed;
    SpoutOutputCollector _collector;
    //private Scanner in;


    public StdinSpout() {
        this(true);
    }

    public StdinSpout(boolean isDistributed) {
        this._isDistributed = isDistributed;
        //in = new Scanner(System.in);

    }

    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        this._collector = collector;
    }

    public void close() {
    }

    public void nextTuple() {
        try
        {
            BufferedReader reader = new BufferedReader(new FileReader("/tmp/mypipe"));
            try
            {
                String line;
                while(true)
                {
                    try
                    {
                        while((line = reader.readLine()) != null)
                        {
                            if(!line.trim().equals(""))
                            {
                                System.out.println(line);
                                this._collector.emit(new Values(new Object[]{line}));
                            }

                        }
                        System.out.println("line = null");
                        reader.close();
                        reader = new BufferedReader(new FileReader("/tmp/mypipe"));
                    } catch(Exception IOException)
                    {
                        System.out.println("IOException");
                    }
                }
            } finally
            {
                reader.close();
            }
        } catch(Exception ex)
        {
            System.out.println("Exception");
            ex.printStackTrace();
        }

        /*
        try {
            // Connect to the named pipe
            RandomAccessFile pipe = new RandomAccessFile("/tmp/mypipe", "r"); // "r" or "w" or for bidirectional (Windows only) "rw"

            try {
                while (true) {
                    try {
                        String line = pipe.readLine();

                        if(line == null)
                        {
                            pipe.close();
                            pipe = new RandomAccessFile("/tmp/mypipe", "r"); // "r" or "w" or for bidirectional (Windows only) "rw"
                        } else
                        {
                            if(!line.trim().equals(""))
                            {
                                this._collector.emit(new Values(new Object[]{line}));
                            }
                        }
                    } catch (Exception ex)
                    {
                        pipe.close();
                        pipe = new RandomAccessFile("/tmp/mypipe", "r"); // "r" or "w" or for bidirectional (Windows only) "rw"
                        ex.printStackTrace();
                    }
                }
            } finally
            {
                pipe.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
        /*
        Scanner in = new Scanner(System.in);

        while(in.hasNextLine())
        {
            String line = in.nextLine();
            this._collector.emit(new Values(new Object[]{line}));
        }

        in.close();
        */
    }

    public void ack(Object msgId) {
    }

    public void fail(Object msgId) {
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(new String[]{"word"}));
    }

    public Map<String, Object> getComponentConfiguration() {
        if(!this._isDistributed) {
            HashMap ret = new HashMap();
            ret.put("topology.max.task.parallelism", Integer.valueOf(1));
            return ret;
        } else {
            return null;
        }
    }
}



/**
 * This is a basic example of a Storm topology.
 */
public class TrafficAnalyzerTopology {

    static class TcpdumpFilterBolt extends BaseRichBolt {
        OutputCollector _collector;

        final static int SYN_THRESHOLD  = 10;
        final static int ICMP_THRESHOLD = 10;

        static String reportUrl="http://10.10.40.30:5984/trafficanalyzer-dev/";
        static String myIP="10.10.20.20";

        static String second;
        static String currentSecond = null;

        static Map<String, Integer[]> stats = new HashMap<String, Integer[]>(); // Per-source-IP SYN & ICMP requests / second.

        static int synTotal = 0; // Total SYN requests / second.
        static int icmpTotal = 0; // Total SYN requests / second.

        static CloseableHttpClient httpClient;

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {

            _collector = collector;
            httpClient = HttpClients.createDefault();
        }

        public TcpdumpFilterBolt(String myIP, String reportUrl)
        {
            super();
            // this.myIP = myIP;
            // this.reportUrl = reportUrl;

        }
        @Override
        public void execute(Tuple tuple) {
            // _collector.emit(tuple, new Values(tuple.getString(0)));
            // _collector.ack(tuple);

            String tcpdump = tuple.getString(0);

            try{
                // Very annoying problem getting program arguments to static inner class.
                System.out.println("Server IP: " + myIP + ". Report URL: " + reportUrl);

                // String tcpdump = "21:48:15.984275 IP (tos 0x10, ttl  64, id 2548, offset 0, flags [DF], proto 6, length: 60) 10.111.58.30.38207 > 10.111.58.18.22: S [tcp sum ok] 3526920906:3526920906(0) win 5840 <mss 1460,sackOK,timestamp 2324857662 0,nop,wscale 2>";


                System.out.println(tcpdump);

                String[] field = tcpdump.split(" ");

                // need to set currentSecond for first run only.
                if(currentSecond == null)
                {
                    currentSecond = field[0].split("\\.")[0];
                }

                second = field[0].split("\\.")[0];

                if(!currentSecond.equals(second))
                {
                    // sendUpdate(currentSecond, synTotal, synStats, icmpTotal, icmpStats);
                    if(synTotal!=0 || icmpTotal!=0) {
                        sendUpdate(currentSecond, synTotal, icmpTotal, stats);
                    }

                    currentSecond = second;

                    // resetStats(synStats, icmpStats); // reset per-source-IP SYN requests / second.
                    resetStats(stats); // reset per-source-IP reqeusts / second stats.
                    synTotal = 0; // reset Total SYN requests / second.
                    icmpTotal = 0;
                }

                String protocol = field[1];

                // Determine if this is an IP packet.
                if(protocol.equals("IP"))
                {
                    // Determine if this packet is a SYN packet
                    if(field[6].contains("S"))
                    {
                        String srcIP = field[2].substring(0, field[2].lastIndexOf('.'));
                        String srcPort = field[2].substring(field[2].lastIndexOf('.')+1);

                        String dstIP = field[4].substring(0, field[4].lastIndexOf('.'));
                        String dstPort = field[4].substring(field[4].lastIndexOf('.')+1, field[4].lastIndexOf(':'));

                        // Determine if this packet is from a remote host.
                        if((!srcIP.equals(myIP)) && (!srcIP.equals("127.0.0.1")))
                        {

                            System.out.println(second + " " + protocol + " " + srcIP + " " + srcPort + " " + dstIP + " " + dstPort);

                            synTotal++;

                            // Update per-source-IP stats
                            if(stats.containsKey(srcIP))
                            {
                                stats.put(srcIP, new Integer[] {stats.get(srcIP)[0]+1, stats.get(srcIP)[1]});
                            }
                            else
                            {
                                stats.put(srcIP, new Integer[] {1, 0});
                            }

                            System.out.println(srcIP + ": " + stats.get(srcIP)[0] + " SYNs. " + stats.get(srcIP)[1] + " ICMPs.");
                        }
                    }

                    // Determine if this packet is an ICMP
                    if(field[5].equals("ICMP"))
                    {
                        String srcIP = field[2];
                        String dstIP = field[4];

                        // Determine if this packet is from a remote host.
                        if((!srcIP.equals(myIP)) && (!srcIP.equals("127.0.0.1")))
                        {

                            System.out.println(second + " " + protocol + " " + srcIP + " " + dstIP);

                            icmpTotal++;

                            // Update per-source-IP stats
                            if(stats.containsKey(srcIP))
                            {
                                stats.put(srcIP, new Integer[] {stats.get(srcIP)[0], stats.get(srcIP)[1]+1});
                            }
                            else
                            {
                                stats.put(srcIP, new Integer[] {0, 1});
                            }

                            System.out.println(srcIP + ": " + stats.get(srcIP)[0] + " SYNs. " + stats.get(srcIP)[1] + " ICMPs.");
                        }

                    }

                }
            }catch (ArrayIndexOutOfBoundsException ex)
            {
                ex.printStackTrace();
                //System.out.println("")
            }
        }

        public static void sendUpdate(String second, int synTotal, int icmpTotal, Map<String, Integer[]> stats)
        {
            System.out.println("Sending update for: " + second + ". Total SYNs: " + synTotal + ". Total ICMPs: " + icmpTotal);

            for(String key : stats.keySet())
            {
                boolean synThresholdExceeded = stats.get(key)[0] > SYN_THRESHOLD;
                boolean icmpThresholdExceeded = stats.get(key)[1] > ICMP_THRESHOLD;
                System.out.println(key + ": " + stats.get(key)[0] + " SYNs. Threshold exceeded? " + synThresholdExceeded + ".  " + stats.get(key)[1] + " ICMPs. Threshold exceeded? " + icmpThresholdExceeded);
            }


            try {

                // "http://posttestserver.com/post.php?dir=storm"

                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                Date date = new Date();

                String fullUrl = reportUrl + dateFormat.format(date) + second.replace(":", "");

                HttpPut request = new HttpPut(fullUrl);

                JSONObject root = new JSONObject();
                JSONObject elements = new JSONObject();

                JSONArray trafficArray = new JSONArray();

                for(String key : stats.keySet())
                {
                    boolean synThresholdExceeded = stats.get(key)[0] > SYN_THRESHOLD;
                    boolean icmpThresholdExceeded = stats.get(key)[1] > ICMP_THRESHOLD;

                    trafficArray.put(new JSONObject().put("attempts", stats.get(key)[0]).put("protocol", "SYN").put("sourceIP", key).put("thresholdExceeded", synThresholdExceeded));
                    trafficArray.put(new JSONObject().put("attempts", stats.get(key)[1]).put("protocol", "ICMP").put("sourceIP", key).put("thresholdExceeded", icmpThresholdExceeded));
                }

                elements.put("element", trafficArray);

                JSONArray totalsArray = new JSONArray();

                totalsArray.put(new JSONObject().put("totalConnections", synTotal).put("protocol", "SYN"));
                totalsArray.put(new JSONObject().put("totalConnections", icmpTotal).put("protocol", "ICMP"));

                JSONObject totalData = new JSONObject().put("totals", totalsArray);



                root.put("timestamp", dateFormat.format(date) + "T" + second + ".000Z");
                root.put("totalData", totalData);
                root.put("elements", elements);


                System.out.println("Sending JSON to " + fullUrl + ": " + root.toString());
                StringEntity params = new StringEntity(root.toString());
                request.addHeader("content-type", "application/x-www-form-urlencoded");
                request.setEntity(params);
                CloseableHttpResponse response = httpClient.execute(request);
                try {

                    System.out.println(response.getStatusLine());
                    HttpEntity entity2 = response.getEntity();
                    // do something useful with the response body
                    // and ensure it is fully consumed
                    EntityUtils.consume(entity2);

                    // handle response here...
                } finally {
                    response.close();
                }
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
            }
        }

        public static void resetStats(Map<String, Integer[]> stats)
        {
            for(String key : stats.keySet())
            {
                stats.put(key, new Integer[] {0,0});
            }
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("word"));
        }


    }

    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("tcpdumpinput", new StdinSpout(), 1);
        builder.setBolt("process", new TcpdumpFilterBolt(args[0], args[1]), 1).shuffleGrouping("tcpdumpinput");

        Config conf = new Config();
        conf.setDebug(true);

    if (args != null && args.length == 3) {
      conf.setNumWorkers(1);

      StormSubmitter.submitTopologyWithProgressBar(args[2], conf, builder.createTopology());
    }
    else {

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("test", conf, builder.createTopology());
        // Utils.sleep(10000);
        // cluster.killTopology("test");
        // cluster.shutdown();
        }
    }
}
