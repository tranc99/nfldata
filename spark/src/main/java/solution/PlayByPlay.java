package solution;

import model.Play;
import model.PlayData;
import model.PlayTypes;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.hadoop.util.hash.Hash;
import org.apache.hadoop.yarn.webapp.hamlet.HamletSpec;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayByPlay {

    private static Logger logger = Logger.getLogger(PlayByPlay.class);

    public static void main(String[] args) {
        SparkConf conf = new SparkConf();
        conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");

        JavaSparkContext sc = new JavaSparkContext("local", "JavaAPISuite", conf);

        final Broadcast<HashMap<String, ArrayList<String>>> teamSeasonToPlayersArrested = sc.broadcast(loadArrests("../arrests.csv", sc));

        JavaPairRDD<String, String> lines = sc.wholeTextFiles("../input/2013_nfl_pbp_data_through_wk_4.csv");
        JavaRDD<PlayData> plays =
                lines.flatMap(
                        (Tuple2<String, String> pair) -> {
                            PlayByPlayParser playByPlayParser = new PlayByPlayParser();
                            return playByPlayParser.parsePlayFile(pair._2(), pair._1());
                        }
                ).map((Play p) -> ArrestParser.parseArrest(p, teamSeasonToPlayersArrested.getValue()));

        plays.collect().forEach(
                t -> System.out.println("Away:" + t.getPlay().getPlayDesc())
                //t -> System.out.println("Away:" + t.getAwayTeam() + " Home:" + t.getHomeTeam())
        );
    }

    private static HashMap<String, ArrayList<String>> loadArrests(String arrestsFile, JavaSparkContext sc) {
        JavaRDD<String> input = sc.textFile(arrestsFile, 1);

        HashMap<String, ArrayList<String>> teamSeasonToPlayersArrested = new HashMap<String, ArrayList<String>>();

        for (String line : input.collect()) {
            String[] pieces = line.split(",");

            String key = ArrestParser.getKey(pieces[0], pieces[1]);

            ArrayList<String> arrestsPerSeasonAndTeam = teamSeasonToPlayersArrested.get(key);

            if (arrestsPerSeasonAndTeam == null) {
                arrestsPerSeasonAndTeam = new ArrayList<String>();
                teamSeasonToPlayersArrested.put(key, arrestsPerSeasonAndTeam);
            }

            arrestsPerSeasonAndTeam.add(pieces[2]);
        }

        return teamSeasonToPlayersArrested;
    }
}