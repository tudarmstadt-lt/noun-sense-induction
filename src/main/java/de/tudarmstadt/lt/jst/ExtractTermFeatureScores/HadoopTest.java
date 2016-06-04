package de.tudarmstadt.lt.jst.ExtractTermFeatureScores;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ToolRunner;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import de.tudarmstadt.lt.jst.Utils.Resources;

import static org.junit.Assert.*;


public class HadoopTest {
    public void runDependencyHoling(boolean selfFeatures, boolean mwe, int expectedLengthWF,
        HashMap<String, List<String>> expectedWFPairs, HashMap<String, List<String>> unexpectedWFPairs) throws Exception
    {
        TestPaths paths = new TestPaths().invoke();
        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", false);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "dependency");
        conf.setBoolean("holing.dependencies.semantify", true);
        conf.setBoolean("holing.nouns_only", false);
        conf.setBoolean("holing.dependencies.noun_noun_dependencies_only", false);
        String mwePath = mwe ? Resources.getJarResourcePath("data/voc-sample.csv") : "";
        conf.setStrings("holing.mwe.vocabulary", mwePath);
        conf.setBoolean("holing.mwe.self_features", selfFeatures);

        ToolRunner.run(conf, new HadoopMain(), new String[]{paths.getInputPath(), paths.getOutputDir()});

        String WFPath = (new File(paths.getOutputDir(), "WF-r-00000")).getAbsolutePath();
        List<String> lines = Files.readAllLines(Paths.get(WFPath), Charset.forName("UTF-8"));
        assertTrue("Number of lines in WF file is wrong.", lines.size() == expectedLengthWF);

        for(String line : lines) {
            String[] fields = line.split("\t");
            String word = fields.length == 3 ? fields[0] : "";
            String feature = fields.length == 3 ? fields[1] : "";
            if (expectedWFPairs.containsKey(word) && expectedWFPairs.get(word).contains(feature)) {
                expectedWFPairs.get(word).remove(feature);
                if (expectedWFPairs.get(word).size() == 0) expectedWFPairs.remove(word);
            }
            if (unexpectedWFPairs.containsKey(word) && unexpectedWFPairs.get(word).contains(feature)) {
                fail("Unexpected feature in the WF file: " + word + "#" + feature);
            }
        }
        assertEquals("Some expected features are missing in the WF file.", 0, expectedWFPairs.size());

    }

    @Test
    public void testDependencyHolingMweSelfFeatures() throws Exception {
        HashMap<String, List<String>> expectedWF = new HashMap<>();
        expectedWF.put("Green pears", new LinkedList<>(Arrays.asList("nn(@,pear)","nn(Green,@)","subj(@,grow)")));
        expectedWF.put("very", new LinkedList<>(Arrays.asList("advmod(@,rigid)")));
        expectedWF.put("Knoll Road", new LinkedList<>(Arrays.asList("nn(@,Road)","nn(@,park)","nn(Knoll,@)","prep_along(proceed,@)","prep_on(continue,@)")));
        expectedWF.put("rarely", new LinkedList<>(Arrays.asList("advmod(@,fit)")));

        HashMap<String, List<String>> unexpectedWF = new HashMap<>();
        unexpectedWF.put("rarely", new LinkedList<>(Arrays.asList("nn(@,the)")));
        unexpectedWF.put("very", new LinkedList<>(Arrays.asList("nn(@,the)")));

        runDependencyHoling(true, true, 752, expectedWF, unexpectedWF);
    }

    @Test
    public void testDependencyHolingMweNoSelfFeatures() throws Exception {
        HashMap<String, List<String>> expectedWF = new HashMap<>();
        expectedWF.put("rarely", new LinkedList<>(Arrays.asList("advmod(@,fit)")));
        expectedWF.put("very", new LinkedList<>(Arrays.asList("advmod(@,rigid)")));
        expectedWF.put("Knoll Road", new LinkedList<>(Arrays.asList("nn(@,park)","prep_along(proceed,@)","prep_on(continue,@)")));
        expectedWF.put("Green pears", new LinkedList<>(Arrays.asList("subj(@,grow)")));

        HashMap<String, List<String>> unexpectedWF = new HashMap<>();
        unexpectedWF.put("rarely", new LinkedList<>(Arrays.asList("nn(@,the)")));
        unexpectedWF.put("very", new LinkedList<>(Arrays.asList("nn(@,the)")));
        unexpectedWF.put("Knoll Road", new LinkedList<>(Arrays.asList("nn(@,Road)","nn(Knoll,@)")));
        unexpectedWF.put("Green pears", new LinkedList<>(Arrays.asList("nn(@,pear)","nn(Green,@)")));

        runDependencyHoling(false, true, 741, expectedWF, unexpectedWF);
    }

    @Test
    public void testDependencyHoling() throws Exception {
        HashMap<String, List<String>> expectedWF = new HashMap<>();
        expectedWF.put("very", new LinkedList<>(Arrays.asList("advmod(@,rigid)")));
        expectedWF.put("rarely", new LinkedList<>(Arrays.asList("advmod(@,fit)")));

        HashMap<String, List<String>> unexpectedWF = new HashMap<>();
        unexpectedWF.put("rarely", new LinkedList<>(Arrays.asList("nn(@,the)")));
        unexpectedWF.put("very", new LinkedList<String>(Arrays.asList("nn(@,the)")));
        unexpectedWF.put("Green pears", new LinkedList<>(Arrays.asList("nn(@,pear)","nn(Green,@)","subj(@,grow)")));
        unexpectedWF.put("Knoll Road", new LinkedList<>(Arrays.asList("nn(@,Road)","nn(@,park)","nn(Knoll,@)","prep_along(proceed,@)","prep_on(continue,@)")));

        runDependencyHoling(false, false, 726, expectedWF, unexpectedWF);
    }

    @Test
    public void testTrigramHolingPRJ() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("data/python-ruby-jaguar.txt").getFile());
        String inputPath = file.getAbsolutePath();
        String outputDir = inputPath + "-out";
        FileUtils.deleteDirectory(new File(outputDir));
        System.out.println("Input text: " + inputPath);
        System.out.println("Output directory: "+  outputDir);

        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", false);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "trigram");
        conf.setBoolean("holing.nouns_only", false);
        conf.setInt("holing.processeach", 1);
        ToolRunner.run(conf, new HadoopMain(), new String[]{inputPath, outputDir});
    }

    @Test
    public void testTrigramWithCoocsBig() throws Exception {
        TestPaths paths = new TestPaths().invoke();
        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", true);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "trigram");
        conf.setBoolean("holing.nouns_only", false);
        conf.setInt("holing.processeach", 1);

        ToolRunner.run(conf, new HadoopMain(), new String[]{paths.getInputPath(), paths.getOutputDir()});
    }

    @Test
    public void testTrigramWithCoocsBigEachTenth() throws Exception {
        String inputPath = getTestCorpusPath();
        String outputDir = inputPath + "-out";
        FileUtils.deleteDirectory(new File(outputDir));
        System.out.println("Input text: " + inputPath);
        System.out.println("Output directory: "+  outputDir);

        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", true);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "trigram");
        conf.setBoolean("holing.nouns_only", false);
        conf.setInt("holing.processeach", 10);

        ToolRunner.run(conf, new HadoopMain(), new String[]{inputPath, outputDir});
    }

    private String getTestCorpusPath() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("data/ukwac-sample-10.txt").getFile());
        return file.getAbsolutePath();
    }

    @Test
    public void testTrigramWithCoocs() throws Exception {
        TestPaths paths = new TestPaths().invoke();
        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", true);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "trigram");
        conf.setBoolean("holing.nouns_only", false);

        ToolRunner.run(conf, new HadoopMain(), new String[]{paths.getInputPath(), paths.getOutputDir()});
    }

    @Test
    public void testTrigram() throws Exception {
        TestPaths paths = new TestPaths().invoke();
        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", false);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "trigram");
        conf.setBoolean("holing.dependencies.semantify", true);
        conf.setBoolean("holing.nouns_only", false);
        conf.setBoolean("holing.dependencies.noun_noun_dependencies_only", false);

        ToolRunner.run(conf, new HadoopMain(), new String[]{paths.getInputPath(), paths.getOutputDir()});

        String WFPath = (new File(paths.getOutputDir(), "WF-r-00000")).getAbsolutePath();
        List<String> lines = Files.readAllLines(Paths.get(WFPath), Charset.forName("UTF-8"));
        assertTrue("Number of lines is wrong.", lines.size() == 412);

        Set<String> expectedFeatures = new HashSet<>(Arrays.asList("place_@_#","#_@_yet", "sum_@_a", "give_@_of"));
        for(String line : lines) {
            String[] fields = line.split("\t");
            String feature = fields.length == 3 ? fields[1] : "";
            if (expectedFeatures.contains(feature)) expectedFeatures.remove(feature);
        }
        assertTrue("Some features are missing in the file.", expectedFeatures.size() == 0); // all expected features are found
    }

    @Test
    public void testTrigramNoLemmatization() throws Exception {
        TestPaths paths = new TestPaths().invoke();
        Configuration conf = new Configuration();
        conf.setBoolean("holing.coocs", false);
        conf.setInt("holing.sentences.maxlength", 100);
        conf.setStrings("holing.type", "trigram");
        conf.setBoolean("holing.dependencies.semantify", true);
        conf.setBoolean("holing.nouns_only", false);
        conf.setBoolean("holing.dependencies.noun_noun_dependencies_only", false);
        conf.setBoolean("holing.lemmatize", false);

        ToolRunner.run(conf, new HadoopMain(), new String[]{paths.getInputPath(), paths.getOutputDir()});

        String WFPath = (new File(paths.getOutputDir(), "WF-r-00000")).getAbsolutePath();
        List<String> lines = Files.readAllLines(Paths.get(WFPath), Charset.forName("UTF-8"));
        assertTrue("Number of lines is wrong.", lines.size() == 412);

        Set<String> expectedFeatures = new HashSet<>(Arrays.asList("was_@_very","#_@_yet", "sum_@_a", "gave_@_of", "other_@_products"));
        for(String line : lines) {
            String[] fields = line.split("\t");
            String feature = fields.length == 3 ? fields[1] : "";
            if (expectedFeatures.contains(feature)) expectedFeatures.remove(feature);
        }
        assertTrue("Some features are missing in the file.", expectedFeatures.size() == 0); // all expected features are found
    }

    private class TestPaths {
        private String inputPath;
        private String outputDir;

        public String getInputPath() {
            return inputPath;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public TestPaths invoke() throws IOException {
            inputPath = getTestCorpusPath();
            outputDir = inputPath + "-out";
            FileUtils.deleteDirectory(new File(outputDir));
            System.out.println("Input text: " + inputPath);
            System.out.println("Output directory: "+  outputDir);
            return this;
        }
    }
}